package controllers

import models.{ RateLimits, BonoboKey, KongCreateConsumerResponse }
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import store._
import kong._
import kong.Kong._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application(dynamo: DB, kong: Kong, val messagesApi: MessagesApi) extends Controller with I18nSupport {

  import Application._

  def index = Action {
    Ok(views.html.index("Yo yo yo, your new application is ready."))
  }

  def search = Action { implicit request =>
    searchForm.bindFromRequest.fold(
      formWithErrors => {
        Ok(views.html.index("Invalid search"))
      },
      searchFormData => {
        val keys: List[BonoboKey] = dynamo.search(searchFormData.query)
        val searchResultsMessage = s"Search results for query: ${searchFormData.query}"
        Ok(views.html.showKeys(keys, searchResultsMessage, lastDirection = "", hasNext = false))
      }
    )
  }

  def createKeyForm = Action { implicit request =>
    Ok(views.html.createKey(message = "", createForm))
  }

  def editKey(id: String) = Action { implicit request =>
    val result = dynamo.retrieveKey(id)
    val filledForm = editForm.fill(EditFormData(result.key, result.email, result.name, result.company, result.url, result.requestsPerDay,
      result.requestsPerMinute, result.tier, result.status))
    Ok(views.html.editKey(message = "", id, filledForm))
  }

  def createKey = Action.async { implicit request =>
    def saveUser(consumer: KongCreateConsumerResponse, formData: CreateFormData, rateLimits: RateLimits): Result = {
      val key: String = java.util.UUID.randomUUID.toString
      val newEntry = new BonoboKey(consumer.id, key, formData.email, formData.name, formData.company,
        formData.url, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, formData.tier, formData.status, consumer.created_at.toString)
      dynamo.save(newEntry)

      Ok(views.html.createKey(message = "A new user has been successfully added", createForm))
    }

    def displayError(message: String): Result = {
      Ok(views.html.createKey(message, createForm))
    }

    def handleInvalidForm(form: Form[CreateFormData]): Future[Result] = {
      Future.successful(Ok(views.html.createKey(message = "Please, correct the highlighted fields.", form)))
    }

    def handleValidForm(createFormData: CreateFormData): Future[Result] = {
      val rateLimits: RateLimits = createFormData.tier match {
        case "1" => new RateLimits(720, 5000)
        case "2" => new RateLimits(720, 10000)
        case "3" => new RateLimits(720, 10000)
      }
      kong.registerUser(createFormData.email, rateLimits) map {
        consumer => saveUser(consumer, createFormData, rateLimits)
      } recover {
        case ConflictFailure => displayError("Username already taken")
        case GenericFailure(message) => displayError(message)
      }
    }
    createForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def showFirstKeys = Action {
    val (keys, hasNext) = dynamo.getKeys("next", "")
    Ok(views.html.showKeys(keys, pageTitle = "All keys", lastDirection = "", hasNext))
  }

  def showKeys(direction: String, range: String) = Action { implicit request =>
    val (keys, hasNext) = dynamo.getKeys(direction, range)
    Ok(views.html.showKeys(keys, pageTitle = "All keys", direction, hasNext))
  }
}

object Application {
  case class CreateFormData(email: String, name: String, company: String, url: String, tier: String, status: String)

  val createForm: Form[CreateFormData] = Form(
    mapping(
      "email" -> nonEmptyText,
      "name" -> nonEmptyText,
      "company" -> nonEmptyText,
      "url" -> nonEmptyText,
      "tier" -> nonEmptyText,
      "status" -> nonEmptyText
    )(CreateFormData.apply)(CreateFormData.unapply)
  )

  case class EditFormData(key: String, email: String, name: String, company: String, url: String, reqestsPerDay: Int, requestsPerMinute: Int, tier: String, status: String)

  val editForm: Form[EditFormData] = Form(
    mapping(
      "key" -> nonEmptyText,
      "email" -> nonEmptyText,
      "name" -> nonEmptyText,
      "company" -> nonEmptyText,
      "url" -> nonEmptyText,
      "requestsPerDay" -> number,
      "requestsPerMinute" -> number,
      "tier" -> nonEmptyText,
      "status" -> nonEmptyText
    )(EditFormData.apply)(EditFormData.unapply)
  )

  case class SearchFormData(query: String)

  val searchForm = Form(
    mapping(
      "query" -> nonEmptyText(minLength = 2, maxLength = 45)
    )(SearchFormData.apply)(SearchFormData.unapply)
  )
}
