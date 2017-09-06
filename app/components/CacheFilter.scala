package components

import akka.stream.Materializer
import org.joda.time.DateTime
import play.api.mvc.{ Result, RequestHeader, Filter }
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class CacheFilter(implicit val mat: Materializer) extends Filter {
  def apply(nextFilter: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
    nextFilter(request).map { result =>
      result
        .withHeaders("Expires" -> "Tue, 03 Jul 2001 06:00:00 GMT")
        .withHeaders("Last-Modified" -> s"${DateTime.now().toString("EEE, dd MMM yyyy hh:mm:ss")} GMT")
        .withHeaders("Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0")
        .withHeaders("Pragma" -> "no-cache")
    }
  }
}
