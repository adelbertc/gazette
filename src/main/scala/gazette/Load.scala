package gazette

import atto.Parser
import atto.parser.character._
import atto.parser.combinator._
import atto.parser.text._
import atto.syntax.parser._

import java.sql.Date

import scalaz.{Applicative, Kleisli}
import scalaz.concurrent.Task
import scalaz.std.list._
import scalaz.stream.io
import scalaz.syntax.apply._
import scalaz.syntax.traverse._

object Load {
  private def until1(c: Char): Parser[String] = takeWhile1(_ != c)

  // Taken from
  // https://github.com/tpolecat/atto/blob/71897dacd5c75d1b2d5acfb886d57f4c5d9d1d59/example/src/main/scala/atto/example/Example.scala#L102-108
  private def date: Parser[Option[Date]] = {
    val int1 = digit.map(_ - '0')
    val int2 = (int1 |@| int1)(_ * 10 + _)
    val int4 = (int2 |@| int2)(_ * 100 + _)

    val date = (int4 <~ char('-') |@| int2 <~ char('-') |@| int2)((y, m, d) => Some(Date.valueOf(s"${y}-${m}-${d}")))
    date | Applicative[Parser].point(None)
  }

  private def tags(tagSep: Char): Parser[List[String]] =
    sepBy(stringOf(anyChar), char(tagSep))

  private def todoLine(sep: Char, tagSep: Char): Parser[Todo] =
    ((until1(sep) <* char(sep))|@| (until1(sep) <* char(sep)) |@| (date <* char(sep)) |@| tags(tagSep))(Todo.apply)

  def separatedBy(sep: Char, tagSep: Char): Kleisli[Task, String, List[Todo]] =
    Kleisli { path =>
      val lines = io.linesR(path).runLog.map(_.toList)
      val t = lines.map(_.traverseU(l => todoLine(sep, tagSep).parseOnly(l).either))
      t.flatMap(_.fold(msg => Task.fail(TodoParseException(msg)), Task.now))
    }
}

final case class TodoParseException(error: String) extends Exception
