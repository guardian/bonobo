package controllers

import kong.Kong
import kong.Kong.{ GenericFailure, ConflictFailure }
import logic.{ CommercialFormLogic, DeveloperFormLogic }
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import store.DB
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CommercialForm(dynamo: DB, kong: Kong, val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import CommercialForm._
  import Forms.CommercialRequestKeyFormData

  private val logic = new CommercialFormLogic(dynamo, kong)

  def requestKeyPage = Action { implicit request =>
    Ok(views.html.commercialRequestKey(requestKeyForm))
  }

  def requestKey = Action { implicit request =>
    def handleInvalidForm(form: Form[CommercialRequestKeyFormData]): Result = {
      BadRequest(views.html.commercialRequestKey(form, error = Some("Please correct the highlighted fields.")))
    }

    def handleValidForm(formData: CommercialRequestKeyFormData): Result = {
      logic.sendRequest(formData) match {
        case Left(error) => BadRequest(views.html.commercialRequestKey(requestKeyForm.fill(formData), error = Some(error)))
        case Right(_) => Redirect(routes.CommercialForm.requestMessage())
      }
    }
    requestKeyForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def requestMessage = Action {
    Ok(views.html.commercialRequestMessage())
  }
}

object CommercialForm {
  import Forms.CommercialRequestKeyFormData

  val requestKeyForm: Form[CommercialRequestKeyFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "productName" -> nonEmptyText,
      "productUrl" -> nonEmptyText,
      "companyName" -> nonEmptyText,
      "companyUrl" -> optional(text),
      "businessArea" -> optional(text),
      "monthlyUsers" -> optional(number),
      "commercialModel" -> optional(text),
      "content" -> optional(text),
      "articlesPerDay" -> optional(number),
      "acceptTerms" -> boolean.verifying("You have to accept the Guardian Open Platform terms and conditions.", terms => terms)
    )(CommercialRequestKeyFormData.apply)(CommercialRequestKeyFormData.unapply)
  )
}
