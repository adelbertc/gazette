package gazette

import java.sql.Date

import scodec.{Attempt, Codec, Err}
import scodec.codecs.{uint16, utf8, variableSizeBits}

import scalaz.Disjunction

object Util {
  def parseDate(s: String): Option[Date] = Disjunction.fromTryCatchNonFatal(Date.valueOf(s)).toOption

  def parseCsv(s: String): List[String] =
    s.split(",").toList.map(_.trim).filter(_.nonEmpty)

  implicit val stringCodec = variableSizeBits(uint16, utf8)

  private def stringToDate(s: String): Attempt[Date] =
    Attempt.fromOption(Util.parseDate(s), Err(s"Could not parse yyyy-MM-dd from ${s}."))

  private def dateToString(d: Date): Attempt[String] =
    Attempt.fromOption(Some(d.toString), Err("Strange error, encoding a Date should always work."))

  implicit val dateCodec: Codec[Date] = variableSizeBits(uint16, utf8).exmap(stringToDate, dateToString)
}
