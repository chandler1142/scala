package monocles

import org.scalatest._
import monocle._, Monocle._, monocle.macros._

/* ref - http://www.slideshare.net/JulienTruffaut/beyond-scala-lens */
class HttpRequestSpec extends WordSpec with Matchers {
  import HttpRequestSpec._

  val r1 = HttpRequest(
    GET,
    URI("localhost", 8080, "/ping", Map("hop" -> "5")),
    Map("socket_timeout" -> "20", "connection_timeout" -> "10"),
    "")

  val r2 = HttpRequest(
    POST,
    URI("gooogle.com", 443, "/search", Map("keyword" -> "monocle")),
    Map.empty,
    "")

  val method = GenLens[HttpRequest](_.method)
  val uri = GenLens[HttpRequest](_.uri)
  val headers = GenLens[HttpRequest](_.headers)
  val body = GenLens[HttpRequest](_.body)

  val host = GenLens[URI](_.host)
  val query = GenLens[URI](_.query)

  val get: Prism[HttpMethod, Unit] = GenPrism[HttpMethod, GET.type] composeIso GenIso.unit[GET.type]
  val post = GenPrism[HttpMethod, POST.type] composeIso GenIso.unit[POST.type]

  "get and post" in {
    (method composePrism get).isMatching(r1) shouldBe true
    (method composePrism post).isMatching(r1) shouldBe false
    (method composePrism post).getOption(r2) shouldBe Some(())
  }

  "host" in {
    (uri composeLens host).set("google.com")(r2) shouldBe
      r2.copy(uri = r2.uri.copy(host = "google.com"))
  }

  "query using index" in {
    val r = (uri
      composeLens query
      composeOptional index("hop")
      composePrism stringToInt).modify(_ + 10)(r1)

    r.uri.query.get("hop") shouldBe Some("15")
  }

  "query using at" in {

    /**
     *  `at` returns Lens[S, Option[A]] while `index` returns Optional[S, A]
     *  So that we need the `some: Prism[Option[A], A]` for further investigation
     */
    val r = (uri
      composeLens query
      composeLens at("hop")
      composePrism some
      composePrism stringToInt).modify(_ + 10)(r1)

    r.uri.query.get("hop") shouldBe Some("15")
  }

  "headers" in {
    val r = (headers composeLens at("Content-Type")).set(Some("text/plain; utf-8"))(r2)
    r.headers.get("Content-Type") shouldBe Some("text/plain; utf-8")
  }

  "headers with filterIndex" in {
    val r = (headers
      composeTraversal filterIndex { h: String => h.contains("timeout") }
      composePrism stringToInt).modify(_ * 2)(r1)

    println(r)
    r.headers.get("socket_timeout") shouldBe Some("40")
    r.headers.get("connection_timeout") shouldBe Some("20")
  }
}

object HttpRequestSpec {
  sealed trait HttpMethod
  case object GET   extends HttpMethod
  case object POST  extends HttpMethod

  case class URI(host: String, port: Int, path: String, query: Map[String, String])
  case class HttpRequest(method: HttpMethod, uri: URI, headers: Map[String, String], body: String)
}
