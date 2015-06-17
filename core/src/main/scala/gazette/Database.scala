package gazette

import doobie.contrib.h2.h2transactor.H2Transactor
import doobie.contrib.h2.h2types.unliftedStringArrayType
import doobie.imports.{ConnectionIO, Transactor, toMoreConnectionIOOps, toSqlInterpolator}
import doobie.util.capture.Capture

import gazette.Util.dateCodec

import java.net.InetSocketAddress
import java.sql.Date

import journal.Logger

import knobs.{ClassPathResource, Config, loadImmutable}

import remotely.{GenClient, GenServer, Monitoring, Response, Signature}

import scalaz.{Applicative, Apply, NonEmptyList, ValidationNel}
import scalaz.concurrent.Task
import scalaz.syntax.std.option._
import scalaz.syntax.apply._

@GenServer(gazette.protocol.server)
abstract class DatabaseServer

class Database extends DatabaseServer {
  private implicit val responseCapture: Capture[Response] =
    new Capture[Response] {
      def apply[A](a: => A): Response[A] = Response.delay(a)
    }

  val log = Logger[Database]

  val transactor: Response[Transactor[Response]] = H2Transactor[Response]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")

  def transact[A](action: ConnectionIO[A]): Response[A] =
    transactor.flatMap(xa => action.transact(xa))

  val createTable: Response[Unit] =
    transact {
      sql"""
      CREATE TABLE todo (
        event     VARCHAR NOT NULL,
        category  VARCHAR NOT NULL,
        due       DATE,
        tags      ARRAY,
        PRIMARY KEY(event, category)
      )
      """.update.run.void
    }

  val insertTodo: Todo => Response[Unit] = todo =>
    transact {
      logBracket(s"Inserting ${todo}..", s"${todo} inserted.") {
        sql"""
        INSERT INTO todo (event, category, due, tags) VALUES (${todo.event}, ${todo.category}, ${todo.due}, ${todo.tags})
        """.update.run.void
      }
    }

  val selectAll: Response[List[Todo]] =
    transact {
      logBracket("Fetching all table rows..", "All rows fetched.") {
        sql"SELECT event, category, due, tags FROM todo".query[Todo].list
      }
    }

  val inCategory: String => Response[List[Todo]] = cat =>
    transact {
      logBracket(s"Fetching to-dos for category ${cat}..", s"To-dos in category ${cat} fetched.") {
        sql"SELECT event, category, due, tags FROM todo WHERE category = ${cat}".query[Todo].list
      }
    }

  val due: Date => Response[List[Todo]] = date =>
    transact {
      logBracket(s"Fetching to-dos due on ${date}..", s"To-dos due on ${date} fetched.") {
        sql"SELECT event, category, due, tags FROM todo WHERE due = ${date}".query[Todo].list
      }
    }

  val inTag: String => Response[List[Todo]] = tag =>
    transact {
      logBracket(s"Fetching to-dos with tag ${tag}..", s"To-dos with tag ${tag} fetched.") {
        sql"SELECT event, category, due, tags FROM todo WHERE ARRAY_CONTAINS(tags, ${tag})".query[Todo].list
      }
    }

  val finish: Todo => Response[Unit] = todo =>
    transact {
      logBracket(s"Removing to-do ${todo}..", s"To-do ${todo} removed.") {
        sql"""
        DELETE FROM todo WHERE
        event = ${todo.event} AND
        category = ${todo.category}
        """.update.run.void
      }
    }

  val statistics: Response[Stats] = selectAll.map(Stats.fromTodos)

  def logBracket[M[_] : Applicative, A](before: String, after: String)(action: M[A]): M[A] =
    Applicative[M].point(log.info(before)) *> action <* Applicative[M].point(log.info(after))
}

object Database extends TaskApp {
  val log = Logger[Database.type]

  def parseConfig(config: Config): ValidationNel[String, (String, Int)] =
    Apply[({type l[a] = ValidationNel[String, a]})#l].tuple2(
      config.lookup[String]("db_host").toSuccess(NonEmptyList("db_host")),
      config.lookup[Int]("db_port").toSuccess(NonEmptyList("db_port")))

  override def runc: Task[Unit] = {
    val configPath = "server.cfg"
    val server = new Database
    val monitoring = Monitoring.consoleLogger("[db server]")

    val serverTask =
      for {
        _     <- Task.delay(log.info(s"Loading config from ${configPath}.."))
        cf    <- loadImmutable(List(ClassPathResource(configPath).required))
        _     <- Task.delay(log.info(s"Config loaded, parsing config.."))
        t     <- parseConfig(cf).fold(es => Task.fail(MissingConfig(es)), t => Task.now(t))
        _     <- Task.delay(log.info(s"Config parsed."))
        s     <- server.environment.serve(new InetSocketAddress(t._1, t._2), monitoring = monitoring)
      } yield s

    // This is kind of weird
    serverTask.flatMap { stop =>
      try {
        while (true) { }
        Task.now(())
      } finally stop.run
    }
  }
}

@GenClient(gazette.protocol.server.signatures)
object DatabaseClient
