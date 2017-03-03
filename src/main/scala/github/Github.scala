package github

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import org.json4s._
import org.joda.time.DateTime

import scala.concurrent.Future

object Github {
  case class CommitHistory(commit: Commit)
  case class Commit(author: CommitAuthor)
  case class CommitAuthor(name: String, email: String, date: DateTime)
}

class Github(token: Option[String])(implicit system: ActorSystem, materializer: ActorMaterializer) extends Json4sSupport {
  import system.dispatcher

  implicit val formats = DefaultFormats ++ Seq(DateTimeSerializer)
  implicit val serialization = native.Serialization

  private val poolClientFlow = Http().cachedHostConnectionPoolHttps[HttpRequest]("api.github.com")
  
  def fetchUserRepos(owner: String, repo: String): Future[List[Github.CommitHistory]] = {
    paginated[Github.CommitHistory](Path.Empty / "repos" / owner / repo / "commits")
  }

  def paginated[T](path: Path)(implicit ev: Unmarshaller[HttpResponse, List[T]]): Future[List[T]] = {

    def fetchGithub(path: Path, query: Query = Query.Empty) = {
      HttpRequest(
        uri = Uri("https://api.github.com").withPath(path).withQuery(query),
        headers = token.map(t => List(Authorization(GenericHttpCredentials("token", t)))).getOrElse(Nil)
      )
    }

    def request(page: Option[Int] = None) = {
      val query = page.map(p => Query("page" -> p.toString())).getOrElse(Query())
      fetchGithub(path, query)
    }

    def extractLastPage(links: String): Int = {
      val pattern = """page=([0-9]+)>; rel=["]?([a-z]+)["]?""".r
      val pages = pattern.findAllIn(links).matchData.map(x => x.group(2) -> x.group(1).toInt).toMap
      pages.getOrElse("last", 1)
    }

    Http()
      .singleRequest(request(None))
      .flatMap { r1 =>
        val lastPage =
          r1.headers.find(_.name == "Link").map(h => extractLastPage(h.value)).getOrElse(1)
        val maxPages = 5
        val clampedLastPage = if (lastPage > maxPages) maxPages else lastPage
        Unmarshal(r1).to[List[T]].map(vs => (vs, clampedLastPage))
      }
      .flatMap {
        case (vs, lastPage) =>
          val nextPagesRequests =
            if (lastPage > 1) {
              Source((2 to lastPage).map(page => request(Some(page))))
                .map(r => (r, r))
                .via(poolClientFlow)
                .runWith(Sink.seq)
                .map(_.toList)
                .map(_.collect { case (scala.util.Success(v), _) => v })
                .flatMap(s => Future.sequence(s.map(r2 => Unmarshal(r2).to[List[T]])))
                .map(_.flatten)
            } else Future.successful(Nil)

          nextPagesRequests.map(vs2 => vs ++ vs2)
      }
  }
}
