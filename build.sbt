name := "gazette"

scalaVersion := "2.10.5"

licenses += ("BSD 2-Clause", url("http://opensource.org/licenses/BSD-2-Clause"))

resolvers ++= List(
  "bmjames"   at "https://dl.bintray.com/bmjames/maven",
  "oncue"     at "https://dl.bintray.com/oncue/releases",
  "scalaz"    at "https://dl.bintray.com/scalaz/releases",
  "tpolecat"  at "https://dl.bintray.com/tpolecat/maven"
)

val doobieVersion = "0.2.2"

def http4sProject(project: String) =
  "org.http4s" %% s"http4s-${project}" % "0.7.0" exclude("com.chuusai", "shapeless_2.10.4")

val monocleVersion = "1.1.1"

val scalazVersion = "7.1.2"

libraryDependencies ++= List(
  compilerPlugin("org.scalamacros"  % ("paradise_" ++ scalaVersion.value) % "2.0.1"),

  "io.argonaut"                 %% "argonaut"                   % "6.1",
  "org.tpolecat"                %% "atto-core"                  % "0.4.1",
  "org.tpolecat"                %% "doobie-core"                % "0.2.2",
  "org.tpolecat"                %% "doobie-contrib-h2"          % "0.2.2",
  "com.h2database"              %  "h2"                         % "1.3.170",
  http4sProject("argonaut"),
  http4sProject("blazeserver"),
  http4sProject("blazeclient"),
  http4sProject("dsl"),
  http4sProject("server"),
  "com.github.julien-truffaut"  %% "monocle-core"               % monocleVersion,
  "com.github.julien-truffaut"  %% "monocle-macro"              % monocleVersion,
  "net.bmjames"                 %% "scala-optparse-applicative" % "0.2.1",
  "oncue.knobs"                 %% "core"                       % "3.1.4",
  "oncue.journal"               %% "core"                       % "2.1.1",
  "org.scalaz"                  %% "scalaz-core"                % scalazVersion,
  "org.scalaz"                  %% "scalaz-concurrent"          % scalazVersion,
  "org.scalaz.stream"           %% "scalaz-stream"              % "0.7.1a",
  "org.scodec"                  %% "scodec-core"                % "1.7.0"         exclude("com.chuusai", "shapeless_2.10.4"),
  "org.scodec"                  %% "scodec-stream"              % "0.7.0"
)

scalacOptions ++= List(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)

initialCommands :=
  """
  import gazette._
  import gazette.Client._

  import java.sql.Date

  import org.http4s.Uri.IPv4

  val uri = uriFromInfo(ServerInfo(IPv4("127.0.0.1"), 8080))
  val todo1 = Todo("drive", "work", None, List("important"))
  val todo2 = Todo("leave", "work", None, List("important"))
  val todo3 = Todo("lunch", "personal", Some(Date.valueOf("2015-06-13")), List("food"))
  val todo4 = Todo("movies", "social", Some(Date.valueOf("2015-06-11")), List("film"))

  val allTodos = List(todo1, todo2, todo3, todo4)
  """
