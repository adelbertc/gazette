package gazette

import doobie.contrib.h2.h2transactor.H2Transactor
import doobie.contrib.h2.h2types.unliftedStringArrayType
import doobie.imports.{ConnectionIO, Transactor, toMoreConnectionIOOps, toSqlInterpolator}

import java.sql.Date

import journal.Logger

import scalaz.Applicative
import scalaz.concurrent.Task
import scalaz.syntax.apply._

object Database {
  val log = Logger[Database.type]

  def statistics: ConnectionIO[Stats] = selectAll.map(Stats.fromTodos)

  val transactor: Task[Transactor[Task]] = H2Transactor[Task]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")

  def transact[A](action: ConnectionIO[A]): Task[A] =
    transactor.flatMap(xa => action.transact(xa))

  def createTable: ConnectionIO[Unit] =
    sql"""
    CREATE TABLE todo (
      event     VARCHAR NOT NULL,
      category  VARCHAR NOT NULL,
      due       DATE,
      tags      ARRAY,
      PRIMARY KEY(event, category)
    )
    """.update.run.void

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

  def finish(todo: Todo): ConnectionIO[Unit] =
    logBracket(s"Removing to-do ${todo}..", s"To-do ${todo} removed.") {
      sql"""
      DELETE FROM todo WHERE
      event = ${todo.event} AND
      category = ${todo.category}
      """.update.run.void
    }

  def logBracket[M[_] : Applicative, A](before: String, after: String)(action: M[A]): M[A] =
    Applicative[M].point(log.info(before)) *> action <* Applicative[M].point(log.info(after))
}
