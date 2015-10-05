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

  def createKey = Action.async { implicit request =>
    def saveUserOnDB(consumer: KongCreateConsumerResponse, formData: CreateFormData, rateLimits: RateLimits): Result = {
      val newEntry = BonoboKey.apply(formData, rateLimits, consumer.id, consumer.created_at.toString)
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
        consumer => saveUserOnDB(consumer, createFormData, rateLimits)
      } recover {
        case ConflictFailure(message) => displayError("Conflict failure: " + message)
        case GenericFailure(message) => displayError(message)
      }
    }
    createForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def showKeys(direction: String, range: String) = Action {
    val (keys, hasNext) = dynamo.getKeys(direction, range)
    range match {
      case "" => Ok(views.html.showKeys(keys, pageTitle = "All keys", "", hasNext))
      case _ => Ok(views.html.showKeys(keys, pageTitle = "All keys", direction, hasNext))
    }
  }

  def editKey(id: String) = Action { implicit request =>
    val result = dynamo.retrieveKey(id)
    val filledForm = editForm.fill(EditFormData(result.key, result.email, result.name, result.company, result.url, result.requestsPerDay,
      result.requestsPerMinute, result.tier, result.status))
    Ok(views.html.editKey(message = "", id, filledForm))
  }

  def updateKey(id: String) = Action.async { implicit request =>

    val oldKey = dynamo.retrieveKey(id)

    def handleInvalidForm(form: Form[EditFormData]): Future[Result] = {
      Future.successful(Ok(views.html.editKey(message = "Please, correct the highlighted fields.", id, form)))
    }

    def updateUserOnDB(newFormData: EditFormData): Result = {
      val updatedUser = new BonoboKey(id, newFormData.key, newFormData.email, newFormData.name, newFormData.company,
        newFormData.url, newFormData.requestsPerDay, newFormData.requestsPerMinute, newFormData.tier, newFormData.status, oldKey.createdAt)
      dynamo.updateUser(updatedUser)

      Ok(views.html.editKey(message = "The user has been successfully updated", id, editForm.fill(newFormData)))
    }

    def handleValidForm(newFormData: EditFormData): Future[Result] = {
      if (oldKey.requestsPerDay != newFormData.requestsPerDay || oldKey.requestsPerMinute != newFormData.requestsPerMinute) {
        kong.updateUser(id, new RateLimits(newFormData.requestsPerMinute, newFormData.requestsPerDay)) map {
          _ =>
            {
              println("yo yo saving in the db")
              updateUserOnDB(newFormData)
            }
        }
      } else {
        updateUserOnDB(newFormData)
        Future.successful(Ok(views.html.editKey(message = "YEE! A user should have been updated!", id, editForm.fill(newFormData))))
      }
    }
    editForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
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

  case class EditFormData(key: String, email: String, name: String, company: String, url: String, requestsPerDay: Int,
                          requestsPerMinute: Int, tier: String, status: String)

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
