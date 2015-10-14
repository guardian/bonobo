package controllers

import models.{ RateLimits, BonoboKey, KongCreateConsumerResponse }
import com.gu.googleauth.{ UserIdentity, GoogleAuthConfig }
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.Logger
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._
import store._
import kong._
import kong.Kong._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application(dynamo: DB, kong: Kong, val messagesApi: MessagesApi, val authConfig: GoogleAuthConfig, val enableAuth: Boolean) extends Controller with AuthActions with I18nSupport {

  import Application._

  object FakeAuthAction extends AuthenticatedBuilder[UserIdentity](userinfo = _ => Some(UserIdentity("", "", "First", "Last", Long.MaxValue, None)))

  private def maybeAuth: AuthenticatedBuilder[UserIdentity] = if (enableAuth) AuthAction else FakeAuthAction

  def search = maybeAuth { implicit request =>
    searchForm.bindFromRequest.fold(
      formWithErrors => {
        Ok(views.html.index("Invalid search"))
      },
      searchFormData => {
        val keys: List[BonoboKey] = dynamo.search(searchFormData.query)
        val searchResultsMessage = s"Search results for query: ${searchFormData.query}"
        Ok(views.html.showKeys(keys, searchResultsMessage, lastDirection = "", hasNext = false, request.user.firstName))
      }
    )
  }

  def createKeyForm = maybeAuth { implicit request =>
    Ok(views.html.createKey(message = "", createForm, request.user.firstName))
  }

  def createKey = maybeAuth.async { implicit request =>

    def saveUserOnDB(consumer: KongCreateConsumerResponse, formData: CreateFormData, rateLimits: RateLimits): Result = {

      val newEntry = BonoboKey.apply(formData, rateLimits, consumer.id, consumer.created_at.toString)
      dynamo.save(newEntry)

      Ok(views.html.createKey(message = "A new user has been successfully added", createForm, request.user.firstName))
    }

    def displayError(message: String): Result = {
      Ok(views.html.createKey(message, createForm, request.user.firstName))
    }

    def handleInvalidForm(form: Form[CreateFormData]): Future[Result] = {
      Future.successful(Ok(views.html.createKey(message = "Please, correct the highlighted fields.", form, request.user.firstName)))
    }

    def handleValidForm(createFormData: CreateFormData): Future[Result] = {
      val rateLimits: RateLimits = createFormData.tier match {
        case "Developer" => new RateLimits(720, 5000)
        case "Rights managed" => new RateLimits(720, 10000)
        case "Internal" => new RateLimits(720, 10000)
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

  def showKeys(direction: String, range: String) = maybeAuth { implicit request =>
    val (keys, hasNext) = dynamo.getKeys(direction, range)
    range match {
      case "" => Ok(views.html.showKeys(keys, pageTitle = "All keys", "", hasNext, request.user.firstName))
      case _ => Ok(views.html.showKeys(keys, pageTitle = "All keys", direction, hasNext, request.user.firstName))
    }
  }

  def editKey(id: String) = maybeAuth { implicit request =>
    val result = dynamo.retrieveKey(id)
    val filledForm = editForm.fill(EditFormData(result.key, result.email, result.name, result.company, result.url, result.requestsPerDay,
      result.requestsPerMinute, result.tier, result.status))
    Ok(views.html.editKey(message = "", id, filledForm, request.user.firstName))
  }

  def updateKey(consumerId: String) = maybeAuth.async { implicit request =>

    val oldKey = dynamo.retrieveKey(consumerId)

    def handleInvalidForm(form: Form[EditFormData]): Future[Result] = {
      Future.successful(Ok(views.html.editKey(message = "Please, correct the highlighted fields.", consumerId, form, request.user.firstName)))
    }

    def updateUserOnDB(newFormData: EditFormData): Result = {
      val updatedUser = new BonoboKey(consumerId, newFormData.key, newFormData.email, newFormData.name, newFormData.company,
        newFormData.url, newFormData.requestsPerDay, newFormData.requestsPerMinute, newFormData.tier, newFormData.status, oldKey.createdAt)
      dynamo.updateUser(updatedUser)

      Ok(views.html.editKey(message = "The user has been successfully updated", consumerId, editForm.fill(newFormData), request.user.firstName))
    }

    def handleValidForm(newFormData: EditFormData): Future[Result] = {

      def updateRateLimitsIfNecessary(): Future[Happy.type] = {
        if (oldKey.requestsPerDay != newFormData.requestsPerDay || oldKey.requestsPerMinute != newFormData.requestsPerMinute) {
          kong.updateUser(consumerId, new RateLimits(newFormData.requestsPerMinute, newFormData.requestsPerDay))
        } else {
          Future.successful(Happy)
        }
      }

      def deactivateKeyIfNecessary(): Future[Happy.type] = {
        if (oldKey.status == "Active" && newFormData.status == "Inactive") {
          kong.deleteKey(consumerId)
        } else {
          Future.successful(Happy)
        }
      }

      def activateKeyIfNecessary(): Future[Happy.type] = {
        if (oldKey.status == "Inactive" && newFormData.status == "Active") {
          kong.createKey(consumerId)
        } else {
          Future.successful(Happy)
        }
      }

      for {
        _ <- updateRateLimitsIfNecessary()
        _ <- deactivateKeyIfNecessary()
        _ <- activateKeyIfNecessary()
      } yield {
        updateUserOnDB(newFormData)
        Ok(views.html.editKey(message = "The user has been successfully updated", consumerId, editForm.fill(newFormData), request.user.firstName))
      }
    }

    editForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def healthcheck = Action { Ok("OK") }
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
