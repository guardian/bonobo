package controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._

class OpenForm(val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import OpenForm._

  def showCreateKeyOpenForm = Action { implicit request =>
    Ok(views.html.openCreateKey(createkeyForm))
  }

  def createKeyOpenForm = Action { implicit request =>
    def handleInvalidForm(form: Form[OpenCreateKeyFormData]): Result = {
      Ok(views.html.openCreateKey(form)).flashing(Flash(Map("error" -> "Please correct the highlighted fields.")))
    }

    def handleValidForm(formData: OpenCreateKeyFormData): Result = {
      formData.acceptTerms match {
        case false => Ok(views.html.openCreateKey(createkeyForm.fill(formData))).flashing(Flash(Map("error" -> "You have to accept the Guardian Open Platform terms and conditions.")))
        case true => Ok(views.html.openShowKey("key"))
      }
    }
    createkeyForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}

object OpenForm {
  case class OpenCreateKeyFormData(name: String, email: String, productName: String, productUrl: String, companyName: String, CompanyUrl: String, acceptTerms: Boolean)

  val createkeyForm: Form[OpenCreateKeyFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "productName" -> nonEmptyText,
      "productUrl" -> nonEmptyText,
      "companyName" -> nonEmptyText,
      "companyUrl" -> text,
      "acceptTerms" -> boolean
    )(OpenCreateKeyFormData.apply)(OpenCreateKeyFormData.unapply)
  )
}
