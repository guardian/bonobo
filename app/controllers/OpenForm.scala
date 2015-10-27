package controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._

class OpenForm(val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import OpenForm._

  def createKeyPage = Action { implicit request =>
    Ok(views.html.openCreateKey(createKeyForm))
  }

  def createKey = Action { implicit request =>
    def handleInvalidForm(form: Form[OpenCreateKeyFormData]): Result = {
      Ok(views.html.openCreateKey(form, error = Some("Please correct the highlighted fields.")))
    }

    def handleValidForm(formData: OpenCreateKeyFormData): Result = {
      Redirect(routes.OpenForm.showKey)
    }
    createKeyForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def showKey = Action {
    Ok(views.html.openShowKey("key"))
  }
}

object OpenForm {
  case class OpenCreateKeyFormData(name: String, email: String, productName: String, productUrl: String, companyName: String, CompanyUrl: String, acceptTerms: Boolean)

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
