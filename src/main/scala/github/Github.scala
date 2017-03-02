package github
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import unmarshalling.{Unmarshal, Unmarshaller}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.Future

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
import org.joda.time.format.ISODateTimeFormat
  import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

object Main {
    def main(args: Array[String]): Unit = {
      implicit val system = ActorSystem()
      import system.dispatcher
      implicit val materializer = ActorMaterializer()

      val github = new Github
      println(Await.result(github.fetchUserRepos("scala-native", "scala-native"), 10.seconds))
    }
}

object Github {
  case class Foo(v: String)
  case class CommitHistory(commit: Commit)
  case class Commit(author: CommitAuthor)
  case class CommitAuthor(name: String, email: String, date: DateTime)
}

class Github(implicit system: ActorSystem, materializer: ActorMaterializer) extends Json4sSupport {


  import system.dispatcher
  private val poolClientFlow = Http().cachedHostConnectionPoolHttps[HttpRequest]("api.github.com")
  private val token = "0aa1824e08caa35ec976c60e40e50c5cf1764552"

  def fetchGithub(path: Path, query: Query = Query.Empty) = {
    HttpRequest(
      uri = Uri(s"https://api.github.com").withPath(path).withQuery(query),
      headers = List(Authorization(GenericHttpCredentials("token", token)))
    )
  }

  def fetchUserRepos(owner: String, repo: String): Future[List[Github.Foo]] = {
    paginated[Github.Foo](Path.Empty / "repos" / owner / repo / "commits")
  }

  def paginated[T](path: Path)(
    implicit ev: Unmarshaller[HttpResponse, List[T]]): Future[List[T]] = {

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


/**
  * Scope serializer, since Scope is not a case class json4s can't handle this by default
  *
  */


object DateTimeSerializer
  extends CustomSerializer[DateTime](
    format =>
      (
        {
          case JString(dateTime) => {
            val parser = ISODateTimeFormat.dateTimeParser
            parser.parseDateTime(dateTime)
          }
        }, {
        case dateTime: DateTime => {
          val formatter = ISODateTimeFormat.dateTime
          JString(formatter.print(dateTime))
        }
      }
      ))


/*
 * Copyright 2015 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.reflect.InvocationTargetException

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.json4s._

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope *Json4s* protocol.
  */
trait Json4sSupport {
  implicit val formats = DefaultFormats ++ Seq(DateTimeSerializer)
  implicit val serialization = native.Serialization

  /**
    * HTTP entity => `A`
    *
    * @tparam A type to decode
    * @return unmarshaller for `A`
    */
  implicit def json4sUnmarshaller[A: Manifest](implicit serialization: Serialization,
                                               formats: Formats): FromEntityUnmarshaller[A] =
    Unmarshaller.byteStringUnmarshaller.forContentTypes(`application/json`).mapWithCharset {
      (data, charset) =>
        try serialization.read(data.decodeString(charset.nioCharset.name))
        catch {
          case MappingException("unknown error", ite: InvocationTargetException) =>
            throw ite.getCause
        }
    }

  /**
    * `A` => HTTP entity
    *
    * @tparam A type to encode, must be upper bounded by `AnyRef`
    * @return marshaller for any `A` value
    */
  implicit def json4sMarshaller[A <: AnyRef](implicit serialization: Serialization,
                                             formats: Formats): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(`application/json`)(serialization.writePretty[A])
}
