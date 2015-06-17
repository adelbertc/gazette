package gazette

import argonaut.{CodecJson, DecodeJson, EncodeJson, Json}
import argonaut.Argonaut._

import gazette.Util.stringCodec

import java.sql.Date

import org.http4s.EntityDecoder
import org.http4s.argonaut.jsonOf

import scodec.{Attempt, Codec, Err}
import scodec.codecs.{uint16, utf8, variableSizeBits}

import scalaz.Disjunction

final case class Todo(event: String, category: String, due: Option[Date], tags: List[String])

object Todo {
  private implicit val dateTimeEncodeJson: EncodeJson[Date] =
    EncodeJson.of[String].contramap(_.toString)

  private implicit val dateTimeDecodeJson: DecodeJson[Date] =
    DecodeJson.optionDecoder(_.string.flatMap(Util.parseDate), "java.sql.Date")

  implicit val todoCodecJson: CodecJson[Todo] = CodecJson.derive[Todo]

  implicit val todoEntityDecoder: EntityDecoder[Todo] = jsonOf[Todo]

  private def todoFromTuple(tuple: (String, String, String, String)): Attempt[Todo] = {
    val (event, category, rawDate, rawTags) = tuple
    val tags = Util.parseCsv(rawTags)
    val todo = if (rawDate.isEmpty) Some(Todo(event, category, None, tags))
               else Util.parseDate(rawDate).map(date => Todo(event, category, Some(date), tags))
    Attempt.fromOption(todo, Err(s"Could not separate ${rawDate} into yyyy-MM-dd."))
  }

  private def todoToTuple(todo: Todo): Attempt[(String, String, String, String)] =
    Attempt.fromOption(Some((todo.event, todo.category, todo.due.fold("")(_.toString), todo.tags.mkString(","))),
                       Err(s"Strange error, encoding a Todo should always work."))


  implicit val todoCodec: Codec[Todo] =
    (stringCodec ~~ stringCodec ~~ stringCodec ~~ stringCodec).exmap(todoFromTuple, todoToTuple)
}
