name := "gazette"

scalaVersion := "2.10.5"

licenses += ("BSD 2-Clause", url("http://opensource.org/licenses/BSD-2-Clause"))

resolvers ++= List(
  "oncue"     at "http://dl.bintray.com/oncue/releases",
  "scalaz"    at "http://dl.bintray.com/scalaz/releases",
  "tpolecat"  at "http://dl.bintray.com/tpolecat/maven"
)

val doobieVersion = "0.2.2"

def http4sProject(project: String) =
  "org.http4s" %% s"http4s-${project}" % "0.7.0" exclude("com.chuusai", "shapeless_2.10.4")

val scalazVersion = "7.1.2"

libraryDependencies ++= List(
  compilerPlugin("org.scalamacros"  % ("paradise_" ++ scalaVersion.value) % "2.0.1"),

  "io.argonaut"     %% "argonaut"           % "6.1",
  "org.tpolecat"    %% "doobie-core"        % "0.2.2",
  "org.tpolecat"    %% "doobie-contrib-h2"  % "0.2.2",
  "com.h2database"  %  "h2"                 % "1.3.170",
  http4sProject("argonaut"),
  http4sProject("blazeserver"),
  http4sProject("dsl"),
  http4sProject("server"),
  "org.scalaz"      %% "scalaz-core"        % scalazVersion,
  "org.scalaz"      %% "scalaz-concurrent"  % scalazVersion
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
