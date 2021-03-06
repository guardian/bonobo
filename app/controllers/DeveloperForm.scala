package controllers

import email.MailClient
import org.joda.time.DateTime
import kong.Kong
import kong.Kong.{ ConflictFailure, GenericFailure }
import logic.DeveloperFormLogic
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import store.DB

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeveloperForm(override val controllerComponents: ControllerComponents, dynamo: DB, kong: Kong, awsEmail: MailClient, assetsFinder: AssetsFinder, hashFn: (String, Long) => String)
  extends BaseController with I18nSupport {
  import DeveloperForm._
  import Forms.DeveloperCreateKeyFormData

  private val logic = new DeveloperFormLogic(dynamo, kong)

  def createKeyPage = Action { implicit request =>
    Ok(views.html.developerCreateKey(assetsFinder, createKeyForm))
  }

  def createKey = Action.async { implicit request =>
    def handleInvalidForm(form: Form[DeveloperCreateKeyFormData]): Future[Result] = {
      Future.successful(BadRequest(views.html.developerCreateKey(assetsFinder, form, error = Some("Please correct the highlighted fields."))))
    }

    def handleValidForm(formData: DeveloperCreateKeyFormData): Future[Result] = {
      logic.createUser(formData) flatMap { consumerKey =>
        awsEmail.sendEmailNewKey(formData.email, consumerKey) map {
          result => Redirect(routes.DeveloperForm.complete)
        } recover {
          case _ => Redirect(routes.DeveloperForm.complete).flashing("error" -> s"We were unable to send the email with the new key to ${formData.email}.  Please contact content.delivery@theguardian.com for further instructions.")
        }
      } recover {
        case ConflictFailure(errorMessage) => Conflict(views.html.developerCreateKey(assetsFinder, createKeyForm.fill(formData), error = Some(errorMessage)))
        case GenericFailure(errorMessage) => InternalServerError(views.html.developerCreateKey(assetsFinder, createKeyForm.fill(formData), error = Some(errorMessage)))
      }
    }
    createKeyForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  /**
   * Deletes user's account and keys. This action is subsequent of
   * the user receiving an email asking whether they are still using their api keys.
   * It is a strong constraint, so we make sure the user indeed received the email
   * by checking the `user.additionalInfo.remindedAt` field.
   */
  def deleteUser(userId: String, hash: String) = Action.async { implicit request =>
    Future { dynamo.getUserWithId(userId) } flatMap { user =>
      if (user.exists(_.additionalInfo.remindedAt.exists(validate(userId, hash))))
        logic.deleteUser(user.get).recover {
          case err =>
            awsEmail.sendEmailDeletionFailed(userId, err)
            ()
        }.map {
          _ => Ok(views.html.developerDeleteComplete(assetsFinder))
        }
      else
        Future.successful(Forbidden)
    }
  }

  /**
   * Extends the lifetime of a user's account and keys. This action is subsequent of
   * the user receiving an email asking whether they are still using their api keys.
   * It is a strong constraint, so we make sure the user indeed received the email
   * by checking the `user.additionalInfo.remindedAt` field.
   */
  def extendUser(userId: String, hash: String) = Action.async { implicit request =>
    Future { dynamo.getUserWithId(userId) } flatMap { user =>
      if (user.exists(_.additionalInfo.remindedAt.exists(validate(userId, hash))))
        logic.extendUser(user.get).recover {
          case err =>
            awsEmail.sendEmailExtensionFailed(userId, err)
            ()
        }.map {
          _ => Ok(views.html.developerExtendComplete(assetsFinder))
        }
      else
        Future.successful(Forbidden)
    }
  }

  def validate(userId: String, hash: String)(time: Long): Boolean =
    hashFn(userId, time) == hash

  def complete = Action {
    Ok(views.html.developerRegisterComplete(assetsFinder))
  }
}

object DeveloperForm {
  import Forms.DeveloperCreateKeyFormData

  /** Time (in weeks) a deletion/extension request remains valid */
  val gracePeriod = 2

  val createKeyForm: Form[DeveloperCreateKeyFormData] = Form(
    mapping(
      "name" -> nonEmptyText(maxLength = 200),
      "email" -> email.verifying("Maximum length is 200", _.length <= 200),
      "productName" -> nonEmptyText(maxLength = 200),
      "productUrl" -> optional(text.verifying("Maximum length is 200", _.length <= 200)),
      "companyName" -> optional(text.verifying("Maximum length is 200", _.length <= 200)),
      "companyUrl" -> optional(text.verifying("Maximum length is 200", _.length <= 200)),
      "acceptTerms" -> boolean.verifying("You have to accept the Guardian Open Platform terms and conditions.", terms => terms))(DeveloperCreateKeyFormData.apply)(DeveloperCreateKeyFormData.unapply))
}
