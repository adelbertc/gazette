package gazette

import argonaut.Argonaut._

import doobie.contrib.h2.h2transactor.H2Transactor
import doobie.contrib.h2.h2types._
import doobie.imports._
import doobie.util.composite._

import java.sql.Date

import org.http4s.UrlForm
import org.http4s.dsl.{->, /, BadRequest, BadRequestSyntax, GET, Ok, OkSyntax, POST, Root}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.HttpService

import scalaz.{Failure, NonEmptyList, Success, ValidationNel}
import scalaz.concurrent.Task
import scalaz.std.map._
import scalaz.std.option._
import scalaz.std.string._
import scalaz.syntax.apply.{ToFunctorOps => _, _}
import scalaz.syntax.show._
import scalaz.syntax.std.option._
import scalaz.syntax.traverse._

object GazetteServer extends TaskApp {
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

  def insertTodo(todo: Todo): ConnectionIO[Unit] =
    sql"""
    INSERT INTO todo (event, category, due, tags) VALUES (${todo.event}, ${todo.category}, ${todo.due}, ${todo.tags})
    """.update.run.void

  def selectAll: ConnectionIO[List[Todo]] =
    sql"SELECT event, category, due, tags FROM todo".query[Todo].list

  def parseForm(map: Map[String, Seq[String]]): ValidationNel[String, Todo] = {
    val event = map.get("event").flatMap(_.headOption).toSuccess(NonEmptyList("event"))
    val category = map.get("category").flatMap(_.headOption).toSuccess(NonEmptyList("category"))
    val due = map.get("due").fold(Option.empty[Date])(_.headOption.flatMap(Util.parseDate))
    val tags = map.get("tags").fold(List.empty[String])(_.toList)
    (event |@| category) { case (e, c) => Todo(e, c, due, tags) }
  }

  val service = HttpService {
    case req @ POST -> Root / "todo" =>
      req.decode[UrlForm] { data =>
        parseForm(data.values) match {
          case Success(td) => Ok(transact(insertTodo(td)) *> Task.now(td.asJson.nospaces))
          case Failure(er) => BadRequest(s"Missing required fields: ${er.shows}")
        }
      }

    case GET -> Root / "todo" =>
      Ok(transact(selectAll).map(_.asJson.nospaces))
  }

  override def runc: Task[Unit] =
    for {
      xa <- transactor
      _  <- createTable.transact(xa)
      _  <- Task.delay(BlazeBuilder.bindHttp(8080).mountService(service, "/gazette").run.awaitShutdown())
    } yield ()
}
