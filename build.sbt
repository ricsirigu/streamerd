val ScalaVer = "2.12.3"

val ScalaJSDom = "0.9.3"
val ScalaTags  = "0.6.5"

val ApacheIO    = "2.5"
val ApacheCodec = "1.10"

val Cats          = "0.9.0"
val KindProjector = "0.9.4"
val Circe         = "0.8.0"

val jsPath = file("assets") / "js"

scalaVersion in ThisBuild := ScalaVer

lazy val commonSettings = Seq(
  name    := "streamerd",
  version := "0.8.0",
  scalaVersion := ScalaVer,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % ScalaTags,
    "io.circe"    %%% "circe-core"    % Circe,
    "io.circe"    %%% "circe-generic" % Circe,
    "io.circe"    %%% "circe-parser"  % Circe,
    "org.typelevel" %%% "cats-core" % Cats,
    "com.lihaoyi" %%% "scalatags" % ScalaTags
  ),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % KindProjector),
  scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:experimental.macros",
      "-unchecked",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Xfuture",
      "-Ypartial-unification",
      "-Ywarn-unused-import")
)

lazy val root = project.in(file(".")).
  aggregate(js, jvm)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val streamerd = crossProject.in(file("."))
  .settings(commonSettings)

lazy val jvm = streamerd.jvm
  .settings(
    libraryDependencies ++= Seq(
      "commons-io"    % "commons-io"    % ApacheIO,
      "commons-codec" % "commons-codec" % ApacheCodec
    ),
    baseDirectory in reStart := new File("."),
    reStart := reStart.dependsOn(fastOptJS in (js, Compile)).evaluated,
    addCommandAlias("run", "streamerdJVM/reStart"),
    addCommandAlias("stop", "streamerdJVM/reStop")
  )

lazy val js  = streamerd.js
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "org.scala-js"  %%% "scalajs-dom" % ScalaJSDom
    ),
    artifactPath in (Compile, fastOptJS) := jsPath / "application.js"
  )
