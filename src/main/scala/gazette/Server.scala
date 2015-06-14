package gazette

import argonaut.Argonaut.ToJsonIdentity

import doobie.contrib.h2.h2transactor.H2Transactor
import doobie.contrib.h2.h2types.unliftedStringArrayType
import doobie.imports.{ConnectionIO, Transactor, toMoreConnectionIOOps, toSqlInterpolator}

import java.sql.Date

import journal.Logger

import knobs.{ClassPathResource, Config, loadImmutable}

import org.http4s.{Response, UrlForm}
import org.http4s.dsl.{->, /, BadRequest, BadRequestSyntax, GET, Ok, OkSyntax, POST, Root}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.HttpService

import scalaz._
import scalaz.Scalaz._
import scalaz.concurrent.Task

object Server extends TaskApp {
  val log = Logger[Server.type]

  val transactor: Task[Transactor[Task]] = H2Transactor[Task]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")

  def transact[A](action: ConnectionIO[A]): Task[A] =
    transactor.flatMap(xa => action.transact(xa))

  def createTable: ConnectionIO[Unit] =
    sql"""
    CREATE TABLE todo (
      event     VARCHAR NOT NULL,
      category  VARCHAR NOT NULL,
      due       DATE,
      tags      ARRAY
    )
    """.update.run.void

  def logBracket[M[_] : Applicative, A](before: String, after: String)(action: M[A]): M[A] =
    Applicative[M].point(log.info(before)) *> action <* Applicative[M].point(log.info(after))

  def insertTodo(todo: Todo): ConnectionIO[Unit] =
    logBracket(s"Inserting ${todo}..", s"${todo} inserted.") {
      sql"""
      INSERT INTO todo (event, category, due, tags) VALUES (${todo.event}, ${todo.category}, ${todo.due}, ${todo.tags})
      """.update.run.void
    }

  def selectAll: ConnectionIO[List[Todo]] =
    logBracket("Fetching all table rows..", "All rows fetched.") {
      sql"SELECT event, category, due, tags FROM todo".query[Todo].list
    }

  def inCategory(cat: String): ConnectionIO[List[Todo]] =
    logBracket(s"Fetching to-dos for category ${cat}..", s"To-dos in category ${cat} fetched.") {
      sql"SELECT event, category, due, tags FROM todo WHERE category = ${cat}".query[Todo].list
    }

  def due(date: Date): ConnectionIO[List[Todo]] =
    logBracket(s"Fetching to-dos due on ${date}..", s"To-dos due on ${date} fetched.") {
      sql"SELECT event, category, due, tags FROM todo WHERE due = ${date}".query[Todo].list
    }

  def inTag(tag: String): ConnectionIO[List[Todo]] =
    logBracket(s"Fetching to-dos with tag ${tag}..", s"To-dos with tag ${tag} fetched.") {
      sql"SELECT event, category, due, tags FROM todo WHERE ARRAY_CONTAINS(tags, ${tag})".query[Todo].list
    }

  def parseForm(map: Map[String, Seq[String]]): ValidationNel[String, Todo] = {
    val event = map.get("event").flatMap(_.headOption).toSuccess(NonEmptyList("event"))
    val category = map.get("category").flatMap(_.headOption).toSuccess(NonEmptyList("category"))
    val due = map.get("due").fold(Option.empty[Date])(_.headOption.flatMap(Util.parseDate))
    val tags = map.get("tags").fold(List.empty[String])(_.toList)
    (event |@| category) { case (e, c) => Todo(e, c, due, tags) }
  }

  def category(params: Map[String, String]): Option[Task[List[Todo]]] =
    params.get("category").map(cat => transact(inCategory(cat)))

  def today: Task[List[Todo]] =
    for {
      date <- Util.currentDate
      rs   <- transact(due(date))
    } yield rs

  def dueOn(params: Map[String, String]): Option[Task[List[Todo]]] =
    for {
      stringDate <- params.get("due")
      date       <- Util.parseDate(stringDate)
    } yield transact(due(date))

  def tag(params: Map[String, String]): Option[Task[List[Todo]]] =
    params.get("tag").map(t => transact(inTag(t)))

  val service = HttpService {
    case req @ POST -> Root / "todo" =>
      req.decode[UrlForm] { data =>
        parseForm(data.values) match {
          case Success(td) => Ok(transact(insertTodo(td)) *> Task.now(td.asJson.nospaces))
          case Failure(er) => BadRequest(s"Missing required fields: ${er.shows}")
        }
      }

    case GET -> Root / "todo" / "today" => Ok(today.map(_.asJson.nospaces))

    case req @ GET -> Root / "todo" =>
      if (req.params.isEmpty) Ok(transact(selectAll).map(_.asJson.nospaces))
      else {
        val p = req.params
        category(p).orElse(dueOn(p)).orElse(tag(p)) match {
          case Some(r) => Ok(r.map(_.asJson.nospaces))
          case None    => BadRequest("Query parameters must be empty or have one of {category, due, tag}.")
        }
      }

  }

  def parseConfig(config: Config): ValidationNel[String, (String, Int)] =
    Apply[({type l[a] = ValidationNel[String, a]})#l].tuple2(config.lookup[String]("host").toSuccess(NonEmptyList("host")),
                                                             config.lookup[Int]("port").toSuccess(NonEmptyList("port")))

  override def runc: Task[Unit] = {
    val configPath = "server.cfg"

    for {
      _  <- Task.delay(log.info(s"Loading config from ${configPath}.."))
      cf <- loadImmutable(List(ClassPathResource(configPath).required))
      _  <- Task.delay(log.info(s"Config loaded, parsing config.."))
      t  <- parseConfig(cf).fold(es => Task.fail(MissingConfig(es)), t => Task.now(t))
      _  <- Task.delay(log.info(s"Config parsed."))
      xa <- transactor
      _  <- Task.delay(log.info("Creating table.."))
      _  <- createTable.transact(xa)
      _  <- Task.delay(log.info("Table created."))
      _  <- Task.delay(BlazeBuilder.bindHttp(host = t._1, port = t._2).mountService(service, "/gazette").run.awaitShutdown())
    } yield ()
  }
}

final case class MissingConfig(missing: NonEmptyList[String]) extends Exception
