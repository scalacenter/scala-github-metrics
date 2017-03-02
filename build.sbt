scalaVersion := "2.12.1"

name := "scala-metrics"

organization := "ch.epfl.scala"

version := "1.0"

//libraryDependencies += "org.eclipse.mylyn.github" %   "org.eclipse.egit.github.core" % "2.1.5"
//libraryDependencies += "co.theasi" %% "plotly" % "0.2.0"
libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.14.0"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.4.2"
lazy val akkaHttpVersion = "10.0.3"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % akkaHttpVersion