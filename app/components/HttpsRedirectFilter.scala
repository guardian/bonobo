package components

import play.api.Configuration
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import scala.concurrent.Future

class HttpsRedirectFilter(enabled: Boolean = HttpsRedirectFilter.DEFAULT_ENABLED,
    sslPort: String = HttpsRedirectFilter.DEFAULT_SSL_PORT) extends Filter {

  def isHealthCheck(uri: String) = uri == "/healthcheck"

  def apply(nextFilter: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] =
    if (enabled && !request.secure && !isHealthCheck(request.uri))
      Future successful Redirect(s"https://${request.domain}:${sslPort}${request.uri}").withHeaders("Strict-Transport-Security" -> "max-age=31536000; includeSubDomains")
    else nextFilter(request)
}

object HttpsRedirectFilter {

  val DEFAULT_SSL_PORT = "443"
  val DEFAULT_ENABLED = true

  def fromConfiguration(configuration: Configuration) =
    new HttpsRedirectFilter(
      configuration.getBoolean("httpsRedirectFilter.enabled").getOrElse(DEFAULT_ENABLED),
      configuration.getString("httpsRedirectFilter.sslPort").getOrElse(DEFAULT_SSL_PORT)
    )
}