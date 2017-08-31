package controllers

import email.MailClient
import logic.CommercialFormLogic
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import store.DB

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CommercialForm(override val controllerComponents: ControllerComponents, dynamo: DB, awsEmail: MailClient, assetsFinder: AssetsFinder)
    extends BaseController with I18nSupport {
  import CommercialForm._
  import Forms.CommercialRequestKeyFormData

  private val logic = new CommercialFormLogic(dynamo)

  def requestKeyPage = Action { implicit request =>
    Ok(views.html.commercialRequestKey(assetsFinder, requestKeyForm))
  }

  def requestKey = Action.async { implicit request =>
    def handleInvalidForm(form: Form[CommercialRequestKeyFormData]): Future[Result] = {
      Future.successful(BadRequest(views.html.commercialRequestKey(assetsFinder, form, error = Some("Please correct the highlighted fields."))))
    }

    def handleValidForm(formData: CommercialRequestKeyFormData): Future[Result] = {
      logic.sendRequest(formData) match {
        case Left(error) => Future.successful(BadRequest(views.html.commercialRequestKey(assetsFinder, requestKeyForm.fill(formData), error = Some(error))))
        case Right(user) => {
          awsEmail.sendEmailCommercialRequestToModerators(user, formData.productName, formData.productUrl) flatMap {
            resultEmailModerators =>
              {
                awsEmail.sendEmailCommercialRequestToUser(formData.email) map {
                  resultEmailUser =>
                    Redirect(routes.CommercialForm.requestMessage())
                } recover {
                  case _ => Redirect(routes.CommercialForm.requestMessage()).flashing("error" -> "We were unable to send you a confirmation email.")
                }
              }
          } recover {
            case _ => Redirect(routes.CommercialForm.requestMessage()).flashing("error" -> "We were unable to send your registration request to our Content Delivery team. Please contact content.delivery@theguardian.com for further instructions.")
          }
        }
      }
    }
    requestKeyForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def requestMessage = Action {
    Ok(views.html.commercialRequestMessage(assetsFinder))
  }
}

object CommercialForm {
  import Forms.CommercialRequestKeyFormData

  val requestKeyForm: Form[CommercialRequestKeyFormData] = Form(
    mapping(
      "name" -> nonEmptyText(maxLength = 200),
      "email" -> email.verifying("Maximum length is 200", _.length <= 200),
      "productName" -> nonEmptyText(maxLength = 200),
      "productUrl" -> nonEmptyText(maxLength = 200),
      "companyName" -> nonEmptyText(maxLength = 200),
      "companyUrl" -> nonEmptyText(maxLength = 200),
      "businessArea" -> nonEmptyText(maxLength = 200),
      "monthlyUsers" -> number,
      "commercialModel" -> nonEmptyText(maxLength = 200),
      "content" -> nonEmptyText(maxLength = 200),
      "articlesPerDay" -> number,
      "contentFormat" -> nonEmptyText(maxLength = 200),
      "acceptTerms" -> boolean.verifying("You have to accept the Guardian Open Platform terms and conditions.", terms => terms)
    )(CommercialRequestKeyFormData.apply)(CommercialRequestKeyFormData.unapply)
  )
}
