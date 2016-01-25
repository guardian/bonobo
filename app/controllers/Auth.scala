package controllers

import com.gu.googleauth.{ UserIdentity, GoogleAuth, GoogleAuthConfig }
import play.api.mvc._
import play.api.libs.ws.WSAPI
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Auth(val authConfig: GoogleAuthConfig, ws: WSAPI) extends Controller with AuthActions {
  import Auth._

  def login = Action.async { implicit request =>
    val antiForgeryToken = GoogleAuth.generateAntiForgeryToken()
    GoogleAuth.redirectToGoogle(authConfig, antiForgeryToken, ws).map {
      _.withSession { request.session + (antiForgeryKey -> antiForgeryToken) }
    }
  }

  /*
  User comes back from Google.
  We must ensure we have the anti forgery token from the loginAction call and pass this into a verification call which
  will return a Future[UserIdentity] if the authentication is successful. If unsuccessful then the Future will fail.
  */
  def oauth2Callback = Action.async { implicit request =>
    val session = request.session
    session.get(antiForgeryKey) match {
      case None =>
        Future.successful(Forbidden("Anti forgery token missing in session"))
      case Some(token) =>
        GoogleAuth.validatedUserIdentity(authConfig, token, ws).map { identity =>
          // We store the URL a user was trying to get to in the LOGIN_ORIGIN_KEY in AuthAction
          // Redirect a user back there now if it exists
          val redirect = session.get(LOGIN_ORIGIN_KEY) match {
            case Some(url) => Redirect(url)
            case None => Redirect(routes.Application.showKeys(labels = List.empty, range = None))
          }
          // Store the JSON representation of the identity in the session - this is checked by AuthAction later
          redirect.withSession {
            session + (UserIdentity.KEY -> Json.toJson(identity).toString) - antiForgeryKey - LOGIN_ORIGIN_KEY
          }
        } recover {
          case t =>
            // you might want to record login failures here - we just redirect to the login page
            Forbidden("Login failure")
              .withSession(session - antiForgeryKey)
        }
    }
  }

}

object Auth {
  val antiForgeryKey = "antiForgeryKey"
}
