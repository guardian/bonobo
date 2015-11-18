package controllers

import com.amazonaws.services.simpleemail.model.SendEmailResult
import email.MailClient
import kong.Kong
import logic.CommercialFormLogic
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import store.DB

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CommercialForm(dynamo: DB, kong: Kong, awsEmail: MailClient, val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import CommercialForm._
  import Forms.CommercialRequestKeyFormData

  private val logic = new CommercialFormLogic(dynamo, kong)

  def requestKeyPage = Action { implicit request =>
    Ok(views.html.commercialRequestKey(requestKeyForm))
  }

  def requestKey = Action.async { implicit request =>
    def handleInvalidForm(form: Form[CommercialRequestKeyFormData]): Future[Result] = {
      Future.successful(BadRequest(views.html.commercialRequestKey(form, error = Some("Please correct the highlighted fields."))))
    }

    def handleValidForm(formData: CommercialRequestKeyFormData): Future[Result] = {
      logic.sendRequest(formData) match {
        case Left(error) => Future.successful(BadRequest(views.html.commercialRequestKey(requestKeyForm.fill(formData), error = Some(error))))
        case Right(user) => {
          awsEmail.sendEmailCommercialRequest(user) map {
            case result: SendEmailResult => Redirect(routes.CommercialForm.requestMessage())
            case _ => Redirect(routes.CommercialForm.requestMessage(Some("We were unable to send the email. Please contact [email] for further instructions")))
          }
        }
      }
    }
    requestKeyForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def requestMessage(error: Option[String]) = Action {
    Ok(views.html.commercialRequestMessage(error = error))
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
