package gazette

import argonaut.Argonaut.ToJsonIdentity

import doobie.imports.toMoreConnectionIOOps

import gazette.Util.stringCodec
import gazette.Util.dateCodec

import java.net.InetSocketAddress
import java.sql.Date

import journal.Logger

import knobs.{ClassPathResource, Config, loadImmutable}

import org.http4s.{Response, UrlForm}
import org.http4s.dsl.{->, /, BadRequest, BadRequestSyntax, GET, Ok, OkSyntax, POST, Root}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.HttpService

import remotely.{Endpoint, Monitoring, Remote, Response => RResponse}
import remotely.Remote.implicits.localToRemote
import remotely.codecs.list
import remotely.transport.netty.NettyTransport

import scalaz._
import scalaz.Scalaz._
import scalaz.concurrent.Task

class Server(loc: Endpoint, monitoring: Monitoring) {
  val log = Logger[Server]

  def todosResponse(response: Remote[List[Todo]]): Task[List[Todo]] =
    response.runWithContext(loc, RResponse.Context.empty, monitoring)

  def unitResponse(response: Remote[Unit]): Task[Unit] =
    response.runWithContext(loc, RResponse.Context.empty, monitoring)

  def statsResponse(response: Remote[Stats]): Task[Stats] =
    response.runWithContext(loc, RResponse.Context.empty, monitoring)

  def parseForm(map: Map[String, Seq[String]]): ValidationNel[String, Todo] = {
    val event = map.get("event").flatMap(_.headOption).toSuccess(NonEmptyList("event"))
    val category = map.get("category").flatMap(_.headOption).toSuccess(NonEmptyList("category"))
    val due = map.get("due").fold(Option.empty[Date])(_.headOption.flatMap(Util.parseDate))
    val tags = map.get("tags").fold(List.empty[String])(_.toList)
    (event |@| category) { case (e, c) => Todo(e, c, due, tags) }
  }

  def category(params: Map[String, String]): Option[Task[List[Todo]]] =
    params.get("category").map(cat => todosResponse(DatabaseClient.inCategory(cat)))

  def today: Task[List[Todo]] =
    for {
      date <- IO.currentDate
      rs   <- todosResponse(DatabaseClient.due(date))
    } yield rs

  def dueOn(params: Map[String, String]): Option[Task[List[Todo]]] =
    for {
      stringDate <- params.get("due")
      date       <- Util.parseDate(stringDate)
    } yield todosResponse(DatabaseClient.due(date))

  def tag(params: Map[String, String]): Option[Task[List[Todo]]] =
    params.get("tag").map(t => todosResponse(DatabaseClient.inTag(t)))

  val service = HttpService {
    case req @ POST -> Root / "todo" =>
      req.decode[UrlForm] { data =>
        parseForm(data.values) match {
          case Success(td) => Ok(unitResponse(DatabaseClient.insertTodo(td)) *> Task.now(td.asJson.nospaces))
          case Failure(er) => BadRequest(s"Missing required fields: ${er.shows}")
        }
      }

    case GET -> Root / "todo" / "today" => Ok(today.map(_.asJson.nospaces))

    case req @ GET -> Root / "todo" =>
      if (req.params.isEmpty) Ok(todosResponse(DatabaseClient.selectAll).map(_.asJson.nospaces))
      else {
        val p = req.params
        category(p).orElse(dueOn(p)).orElse(tag(p)) match {
          case Some(r) => Ok(r.map(_.asJson.nospaces))
          case None    => BadRequest("Query parameters must be empty or have one of {category, due, tag}.")
        }
      }

    case GET -> Root / "todo" / "stats" => Ok(statsResponse(DatabaseClient.statistics).map(_.asJson.nospaces))

    case req @ POST -> Root / "todo" / "finish" =>
      req.decode[UrlForm] { data =>
        parseForm(data.values) match {
          case Success(td) => Ok(unitResponse(DatabaseClient.finish(td)) *> Task.now(td.asJson.nospaces))
          case Failure(er) => BadRequest(s"Missing required fields: ${er.shows}")
        }
      }
  }
}

object Server extends TaskApp {
  val log = Logger[Server.type]

  def parseConfig(config: Config): ValidationNel[String, (String, Int, String, Int)] =
    Apply[({type l[a] = ValidationNel[String, a]})#l].tuple4(
      config.lookup[String]("server_host").toSuccess(NonEmptyList("server_host")),
      config.lookup[Int]("server_port").toSuccess(NonEmptyList("server_port")),
      config.lookup[String]("db_host").toSuccess(NonEmptyList("db_host")),
      config.lookup[Int]("db_port").toSuccess(NonEmptyList("db_port")))

  override def runc: Task[Unit] = {
    val configPath = "server.cfg"

    for {
      _  <- Task.delay(log.info(s"Loading config from ${configPath}.."))
      cf <- loadImmutable(List(ClassPathResource(configPath).required))
      _  <- Task.delay(log.info(s"Config loaded, parsing config.."))
      t  <- parseConfig(cf).fold(es => Task.fail(MissingConfig(es)), t => Task.now(t))
      _  <- Task.delay(log.info(s"Config parsed."))
      (sHost, sPort, dHost, dPort) = t
      transport <- NettyTransport.single(new InetSocketAddress(dHost, dPort))
      loc = Endpoint.single(transport)
      monitoring = Monitoring.consoleLogger("[db client]")
      s = new Server(loc, monitoring)
      _  <- DatabaseClient.createTable.runWithContext(loc, RResponse.Context.empty, monitoring)
      _  <- Task.delay(BlazeBuilder.bindHttp(host = sHost, port = sPort).mountService(s.service, "/gazette").run.awaitShutdown())
    } yield ()
  }
}

final case class MissingConfig(missing: NonEmptyList[String]) extends Exception
