package auth

import com.gu.googleauth.{ GoogleServiceAccount, GoogleGroupChecker }
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }

trait Authorisation {

  /**
   * Decide whether the user with the given email address is authorised to access the Bonobo admin pages.
   */
  def isAuthorised(email: String)(implicit ec: ExecutionContext): Future[Boolean]

}

/**
 * Authorisation based on the user being a member of certain Google groups
 *
 * @param serviceAccount The service account used to make requests to the Google API
 */
class GoogleGroupsAuthorisation(serviceAccount: GoogleServiceAccount) extends Authorisation {
  import GoogleGroupsAuthorisation._

  private val checker = new GoogleGroupChecker(serviceAccount)

  def isAuthorised(email: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    getGroupsForUser(email).map { groups =>
      val hasTwoFactorAuth = hasTwoFactorAuthEnabled(groups)
      if (!hasTwoFactorAuth)
        Logger.info(s"Rejecting user [$email] because they do not have 2FA enabled")
      hasTwoFactorAuth
    }
  }

  private def getGroupsForUser(email: String)(implicit ec: ExecutionContext): Future[Set[String]] = {
    checker.retrieveGroupsFor(email).map { groups =>
      Logger.info(s"User $email is in the following groups: $groups")
      groups
    }
  }

  private def hasTwoFactorAuthEnabled(groups: Set[String]): Boolean = groups.contains(TwoFactorAuthGroup)

}

object GoogleGroupsAuthorisation {
  val TwoFactorAuthGroup = "2fa_enforce@guardian.co.uk"
}

/**
 * Dummy authorisation for use in DEV environment and automated tests.
 * Does no authorisation, i.e. accepts everyone.
 */
object DummyAuthorisation extends Authorisation {

  def isAuthorised(email: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    Logger.info(s"Skipping authorisation for user $email because I am a dummy!")
    Future.successful(true)
  }

}
