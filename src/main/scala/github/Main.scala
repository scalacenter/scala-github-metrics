package github

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.jfree.data.time.{Day, TimeTableXYDataset}

import scala.collection.mutable.HashMap
import scala.concurrent.duration._
import scala.concurrent.Await
import scalax.chart.api._

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val github = new Github(args.headOption)
    val commitHistory = Await.result(github.fetchUserRepos("scala-native", "scala-native"), 10.seconds)
    val commitFrequency = new HashMap[Day, Double]
    commitHistory.foreach(commitContainer => {
      val commitDate = commitContainer.commit.author.date
      val commitDateNoTime = new Day(commitDate.getDayOfMonth, commitDate.getMonthOfYear, commitDate.getYear)

      val count = commitFrequency.get(commitDateNoTime)
      count match {
        case Some(previousCount) => commitFrequency.put(commitDateNoTime, previousCount + 1.0)
        case None => commitFrequency.put(commitDateNoTime, 1.0)
      }
    })
    val data = new TimeTableXYDataset

    for ((date, count) <- commitFrequency) data.add(date, count, "commits")


    val chart = XYLineChart(data)
    chart.saveAsSVG("mychart.svg")
  }
}
