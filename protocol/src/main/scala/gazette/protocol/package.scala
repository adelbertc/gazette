package gazette

import gazette.Util.dateCodec

import java.sql.Date

import scodec.{Attempt, Codec, Err}
import scodec.codecs.{uint16, utf8, variableSizeBits}

import scalaz.Disjunction

package object protocol {
  import remotely.Protocol
  import remotely.codecs.{list, utf8}

  val server =
    Protocol.empty.
    codec[Date].
    codec[String].
    codec[Todo].
    codec[Unit].
    codec[List[Todo]].
    specify0[Unit]("createTable").
    specify1[Todo, Unit]("insertTodo").
    specify0[List[Todo]]("selectAll").
    specify1[String, List[Todo]]("inCategory").
    specify1[Date, List[Todo]]("due").
    specify1[String, List[Todo]]("inTag").
    specify1[Todo, Unit]("finish").
    specify0[Stats]("statistics")
}

