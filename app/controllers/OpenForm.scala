package controllers

import kong.Kong
import kong.Kong.{ GenericFailure, ConflictFailure }
import logic.OpenFormLogic
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import store.DB
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OpenForm(dynamo: DB, kong: Kong, val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import OpenForm._
  import Forms.OpenCreateKeyFormData

  private val logic = new OpenFormLogic(dynamo, kong)

  def createKeyPage = Action { implicit request =>
    Ok(views.html.openCreateKey(createKeyForm))
  }

  def createKey = Action.async { implicit request =>
    def handleInvalidForm(form: Form[OpenCreateKeyFormData]): Future[Result] = {
      Future.successful(Ok(views.html.openCreateKey(form, error = Some("Please correct the highlighted fields."))))
    }

    def handleValidForm(formData: OpenCreateKeyFormData): Future[Result] = {
      logic.createUser(formData) map { consumerId =>
        Redirect(routes.OpenForm.showKey(consumerId))
      } recover {
        case ConflictFailure(errorMessage) => Conflict(views.html.openCreateKey(createKeyForm.fill(formData), error = Some(errorMessage)))
        case GenericFailure(errorMessage) => InternalServerError(views.html.openCreateKey(createKeyForm.fill(formData), error = Some(errorMessage)))
      }
    }
    createKeyForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def showKey(consumerId: String) = Action {
    Ok(views.html.openShowKey(dynamo.getKeyForUser(consumerId)))
  }
}

object OpenForm {
  import Forms.OpenCreateKeyFormData

  val createKeyForm: Form[OpenCreateKeyFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "productName" -> nonEmptyText,
      "productUrl" -> nonEmptyText,
      "companyName" -> nonEmptyText,
      "companyUrl" -> text,
      "acceptTerms" -> boolean.verifying("You have to accept the Guardian Open Platform terms and conditions.", terms => terms)
    )(OpenCreateKeyFormData.apply)(OpenCreateKeyFormData.unapply)
  )
}
