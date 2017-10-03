package controllers.csrf

import play.api.mvc.RequestHeader
import play.filters.csrf.CSRF
import play.filters.csrf.CSRFConfig
import play.twirl.api.Html

object CSRFSupport {
  val config = CSRFConfig()

  def getToken(request: RequestHeader): Option[String] = {
    CSRF.getToken(request).map(_.value)
      // Check cookie if cookie name is defined
      .orElse(config.cookieName.flatMap(n => request.cookies.get(n).map(_.value)))
      // Check session
      .orElse(request.session.get(config.tokenName))
  }

  def formField(implicit request: RequestHeader): Html = {
    // probably not possible for an attacker to XSS with a CSRF token, but just to be on the safe side...
    val token = getToken(request).getOrElse("")
    Html(s"""<input type="hidden" name="${config.tokenName}" value="$token"/>""")
  }
}