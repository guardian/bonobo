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
    Ok(views.html.createKey(message = "", form))
  }

  def editKey(id: String) = Action { implicit request =>
    val result = dynamo.retrieveKey(id)
    val filledForm = form.fill(FormData(result.key, result.email, result.name, result.company, result.url, result.requestsPerDay,
      result.requestsPerMinute, result.tier, result.status))
    Ok(views.html.editKey(message = "", id, filledForm))
  }

  def createKey = Action.async { implicit request =>
    def saveUser(consumer: KongCreateConsumerResponse, formData: FormData): Result = {
      val newEntry = new BonoboKey(consumer.id, formData.key, formData.email, formData.name, formData.company,
        formData.url, formData.requestsPerDay, formData.requestsPerMinute, formData.tier, formData.status, consumer.created_at.toString)
      dynamo.save(newEntry)

      Ok(views.html.createKey(message = "A new user has been successfully added", form))
    }

    def displayError(message: String): Result = {
      Ok(views.html.createKey(message, form))
    }

    def handleInvalidForm(f: Form[FormData]): Future[Result] = {
      Future.successful(Ok(views.html.createKey(message = "Please, correct the highlighted fields.", form = f)))
    }

    def handleValidForm(formData: FormData): Future[Result] = {
      val rateLimit = RateLimits(formData.requestsPerMinute, formData.requestsPerDay)
      kong.registerUser(formData.email, rateLimit) map {
        consumer => saveUser(consumer, formData)
      } recover {
        case ConflictFailure => displayError("Username already taken")
        case GenericFailure(message) => displayError(message)
      }
    }
    form.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
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
  case class FormData(key: String, email: String, name: String, company: String, url: String, requestsPerDay: Int,
    requestsPerMinute: Int, tier: String, status: String)

  val form: Form[FormData] = Form(
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
    )(FormData.apply)(FormData.unapply)
  )

  case class SearchFormData(query: String)

  val searchForm = Form(
    mapping(
      "query" -> nonEmptyText(minLength = 2, maxLength = 45)
    )(SearchFormData.apply)(SearchFormData.unapply)
  )
}
