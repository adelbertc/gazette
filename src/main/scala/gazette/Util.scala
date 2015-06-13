package gazette

import java.sql.Date

import scalaz.Disjunction

object Util {
  def parseDate(s: String): Option[Date] = Disjunction.fromTryCatchNonFatal(Date.valueOf(s)).toOption
}
