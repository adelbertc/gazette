package gazette

import org.http4s.Uri

import monocle.macros.GenLens

object Lenses {
  val uriQuery = GenLens[Uri](_.query)

  val uriPath = GenLens[Uri](_.path)
}
