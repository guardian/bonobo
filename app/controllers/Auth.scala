package controllers

import com.gu.googleauth.{ GoogleAuthConfig, GoogleGroupChecker, GoogleServiceAccount, LoginSupport }
import play.api.mvc._
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext

class Auth(
  override val controllerComponents: ControllerComponents,
  val authConfig: GoogleAuthConfig,
  override val wsClient: WSClient)(
  implicit
  val executionContext: ExecutionContext)
  extends BaseController with LoginSupport {

  /**
   * Shows UI for login button and logout error feedback
   */
  def login = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.login(error))
  }

  /*
   * Redirect to Google with anti forgery token (that we keep in session storage - note that flashing is NOT secure).
   */
  def loginAction = Action.async { implicit request =>
    startGoogleLogin()
  }

  /*
  User comes back from Google.
  We must ensure we have the anti forgery token from the loginAction call and pass this into a verification call which
  will return a Future[UserIdentity] if the authentication is successful. If unsuccessful then the Future will fail.
  */
  def oauth2Callback = Action.async { implicit request =>
    processOauth2Callback()
  }

  override val failureRedirectTarget: Call = routes.Auth.login()
  override val defaultRedirectTarget: Call = routes.Application.showKeys(Nil, "next", None)
}
