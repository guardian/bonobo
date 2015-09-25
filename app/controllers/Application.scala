package controllers

import models.{BonoboKeys, ConsumerInput}
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._
import store.Dynamo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application(dynamo: Dynamo, ws: WSClient, val messagesApi: MessagesApi) extends Controller with I18nSupport {

  import Application._

  def index = Action {
    Ok(views.html.index("Yo yo yo, your new application is ready."))
  }

  val search_form = Form(
    "query" -> nonEmptyText(minLength = 2, maxLength = 45)
  )

  def search = Action { implicit request =>
    val query = search_form.bindFromRequest.get
    val keys: List[BonoboKeys] = dynamo.search(query)
    Ok(views.html.showKeys(keys, s"Search results for query: $query"))
  }


  def createKeyForm = Action { implicit request =>
    Ok(views.html.createKey("Enter your details", form))
  }

  /* creates a new user and stores information on dynamoDB */
  def createKey = Action.async { implicit request =>
    def createNewUser(consumer: ConsumerInput, formData: FormData): Result = {
      val newEntry = new BonoboKeys(consumer.id, formData.key, formData.email, formData.name, formData.surname,
        formData.company, formData.url, formData.requestsPerDay, formData.requestsPerMinute, formData.tier, formData.status, consumer.created_at)
      dynamo.save(newEntry)
      Ok(views.html.createKey("A new object has been saved to DynamoDB", form))
    }

    def displayConflictError(): Result = {
      Ok(views.html.createKey("Email already taken", form))
    }

    def displayGenericError(): Result = {
      Ok(views.html.createKey("Something horrible happened", form))
    }

    def handleErrors(f: Form[FormData]): Future[Result] = {
      Future.successful(Ok(views.html.createKey("Please, correct the highlighted fields.", f)))
    }

    def handleSuccess(formData: FormData): Future[Result] = {
      ws.url("http://52.18.126.249:8001/consumers").post(Map("username" -> Seq(formData.email))).map {
        response =>
          response.status match {
            case 201 => response.json.validate[ConsumerInput] match {
              case JsSuccess(consumerInput, _) => createNewUser(consumerInput, formData)
              case JsError(consumerError) => displayGenericError()
            }
            case 409 => displayConflictError()
            case _ => displayGenericError()
          }
      }
    }

    form.bindFromRequest.fold[Future[Result]](handleErrors, handleSuccess)
  }

  def showKeys = Action {
    val keys: List[BonoboKeys] = dynamo.getAllKeys()
    Ok(views.html.showKeys(keys, "All keys"))
  }
}

object Application {
  case class FormData(key: String, email: String, name: String, surname: String, company: String, url: String, requestsPerDay: Int, requestsPerMinute: Int, tier: String, status: String)

  val form: Form[FormData] = Form(
    mapping(
      "key" -> nonEmptyText,
      "email" -> nonEmptyText,
      "name" -> nonEmptyText,
      "surname" -> nonEmptyText,
      "company" -> nonEmptyText,
      "url" -> nonEmptyText,
      "requestsPerDay" -> number,
      "requestsPerMinute" -> number,
      "tier" -> nonEmptyText,
      "status" -> nonEmptyText
    )(FormData.apply)(FormData.unapply)
  )
}
