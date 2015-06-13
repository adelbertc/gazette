package gazette

import argonaut.{CodecJson, DecodeJson, EncodeJson, Json}
import argonaut.Argonaut._

import java.sql.Date

final case class Todo(event: String, category: String, due: Option[Date], tags: List[String])

object Todo {
  private implicit val dateTimeEncodeJson: EncodeJson[Date] =
    EncodeJson.of[String].contramap(_.toString)

  private implicit val dateTimeDecodeJson: DecodeJson[Date] =
    DecodeJson.optionDecoder(_.string.flatMap(Util.parseDate), "java.sql.Date")

  implicit val todoCodecJson: CodecJson[Todo] = CodecJson.derive[Todo]
}
