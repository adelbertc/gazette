package gazette

import argonaut.{CodecJson, DecodeJson, EncodeJson}

import gazette.Util.stringCodec

import org.http4s.EntityDecoder
import org.http4s.argonaut.jsonOf

import scala.collection.immutable.SortedMap

import scodec.Codec
import scodec.codecs._

import spire.algebra.Monoid
import spire.std.int._
import spire.std.map._

final case class Stats(categories: SortedMap[String, Int], tags: SortedMap[String, Int])

object Stats {
  private val M: Monoid[SortedMap[String, Int]] = {
    val M = Monoid.additive[Map[String, Int]]
    new Monoid[SortedMap[String, Int]] {
      def id: SortedMap[String, Int] = SortedMap.empty[String, Int]
      def op(x: SortedMap[String, Int], y: SortedMap[String, Int]): SortedMap[String, Int] =
        SortedMap(M.op(x.toMap, y.toMap).toList: _*) // Delegate hard work to Spire
    }
  }

  implicit val metricMonoid: Monoid[Stats] =
    new Monoid[Stats] {
      def id: Stats = Stats(M.id, M.id)

      def op(x: Stats, y: Stats): Stats = Stats(M.op(x.categories, y.categories), M.op(x.tags, y.tags))
    }

  def fromTodo(todo: Todo): Stats =
    Stats(SortedMap((todo.category, 1)), SortedMap(todo.tags.map(tag => (tag, 1)): _*))

  def fromTodos(todos: List[Todo]): Stats =
    todos.map(fromTodo).foldLeft(Monoid[Stats].id)(Monoid[Stats].op)

  implicit val statsCodecJson = CodecJson.derive[Stats]

  implicit val statsEntityDecoder: EntityDecoder[Stats] = jsonOf[Stats]

  private val tuple2Codec: Codec[(String, Int)] = new TupleCodec(Codec[String], int32)

  private def listToMap(list: List[(String, Int)]): SortedMap[String, Int] = SortedMap(list: _*)

  private def mapToList(map: SortedMap[String, Int]): List[(String, Int)] = map.toList

  private val sortedMapCodec: Codec[SortedMap[String, Int]] = list(tuple2Codec).xmap(listToMap, mapToList)

  implicit val statsCodec: Codec[Stats] = (sortedMapCodec :: sortedMapCodec).as[Stats]
}
