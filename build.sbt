scalaVersion := "2.12.1"

name := "scala-metrics"

organization := "ch.epfl.scala"

version := "0.1.0"

lazy val akkaHttpVersion = "10.0.3"

libraryDependencies ++= Seq(
  "com.github.nscala-time" %% "nscala-time" % "2.14.0",
  "org.json4s" %% "json4s-native" % "3.4.2",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.github.wookietreiber" %% "scala-chart" % "latest.integration",
  "org.jfree" % "jfreesvg" % "3.0"
)

scalacOptions := Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
  // "-Ywarn-unused-import"
)
