package gazette

import argonaut.{CodecJson, DecodeJson, EncodeJson}

import org.http4s.EntityDecoder
import org.http4s.argonaut.jsonOf

import spire.algebra.Monoid
import spire.std.int._
import spire.std.map._

final case class Stats(categories: Map[String, Int], tags: Map[String, Int])

object Stats {
  private val M = Monoid.additive[Map[String, Int]]

  implicit val metricMonoid: Monoid[Stats] =
    new Monoid[Stats] {
      def id: Stats = Stats(M.id, M.id)

      def op(x: Stats, y: Stats): Stats = Stats(M.op(x.categories, y.categories), M.op(x.tags, y.tags))
    }

  def fromTodo(todo: Todo): Stats =
    Stats(Map((todo.category, 1)), todo.tags.map(tag => (tag, 1)).toMap)

  def fromTodos(todos: List[Todo]): Stats =
    todos.map(fromTodo).foldLeft(Monoid[Stats].id)(Monoid[Stats].op)

  implicit val statsCodecJson = CodecJson.derive[Stats]

  implicit val statsEntityDecoder: EntityDecoder[Stats] = jsonOf[Stats]
}
