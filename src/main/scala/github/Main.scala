package github

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github(args.headOption)
    println(Await.result(github.fetchUserRepos("scala-native", "scala-native"), 10.seconds))
  }
}
