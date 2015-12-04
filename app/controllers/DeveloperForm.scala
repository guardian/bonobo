package controllers

import email.MailClient
import kong.Kong
import kong.Kong.{ GenericFailure, ConflictFailure }
import logic.DeveloperFormLogic
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import store.DB
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeveloperForm(dynamo: DB, kong: Kong, awsEmail: MailClient, enableEmail: Boolean, val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import DeveloperForm._
  import Forms.DeveloperCreateKeyFormData

  private val logic = new DeveloperFormLogic(dynamo, kong)

  def createKeyPage = Action { implicit request =>
    Ok(views.html.developerCreateKey(createKeyForm))
  }

  def createKey = Action.async { implicit request =>
    def handleInvalidForm(form: Form[DeveloperCreateKeyFormData]): Future[Result] = {
      Future.successful(BadRequest(views.html.developerCreateKey(form, error = Some("Please correct the highlighted fields."))))
    }

    def handleValidForm(formData: DeveloperCreateKeyFormData): Future[Result] = {
      logic.createUser(formData) flatMap { consumerKey =>
        if (enableEmail) {
          awsEmail.sendEmailNewKey(formData.email, consumerKey) map {
            result => Redirect(routes.DeveloperForm.showKey(consumerKey))
          } recover {
            case _ => Redirect(routes.DeveloperForm.showKey(consumerKey)).flashing("error" -> s"We were unable to send the email with the new key. Please contact ${formData.email}.")
          }
        } else Future.successful(Redirect(routes.DeveloperForm.showKey(consumerKey)))
      } recover {
        case ConflictFailure(errorMessage) => Conflict(views.html.developerCreateKey(createKeyForm.fill(formData), error = Some(errorMessage)))
        case GenericFailure(errorMessage) => InternalServerError(views.html.developerCreateKey(createKeyForm.fill(formData), error = Some(errorMessage)))
      }
    }
    createKeyForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def showKey(consumerKey: String) = Action {
    Ok(views.html.developerShowKey(consumerKey))
  }
}

object DeveloperForm {
  import Forms.DeveloperCreateKeyFormData

  val createKeyForm: Form[DeveloperCreateKeyFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "productName" -> nonEmptyText,
      "productUrl" -> nonEmptyText,
      "companyName" -> nonEmptyText,
      "companyUrl" -> nonEmptyText,
      "acceptTerms" -> boolean.verifying("You have to accept the Guardian Open Platform terms and conditions.", terms => terms)
    )(DeveloperCreateKeyFormData.apply)(DeveloperCreateKeyFormData.unapply)
  )
}
