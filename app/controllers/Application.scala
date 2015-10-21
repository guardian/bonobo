package controllers

import models._
import com.gu.googleauth.{ UserIdentity, GoogleAuthConfig }
import org.joda.time.DateTime
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
        val keys: List[BonoboInfo] = dynamo.search(searchFormData.query)
        val searchResultsMessage = s"Search results for query: ${searchFormData.query}"
        Ok(views.html.showKeys(keys, searchResultsMessage, lastDirection = "", hasNext = false, keys.length, request.user.firstName))
      }
    )
  }

  def createNewUserForm = maybeAuth { implicit request =>
    Ok(views.html.createUser(message = "", createUserForm, request.user.firstName))
  }

  def createUser = maybeAuth.async { implicit request =>

    def saveUserOnDB(consumer: UserCreationResult, formData: CreateUserFormData, rateLimits: RateLimits): Result = {

      val newBonoboUser = BonoboUser(consumer.id, formData)
      dynamo.saveBonoboUser(newBonoboUser)

      val newKongKey = KongKey(consumer, formData.tier, rateLimits)
      dynamo.saveKongKey(newKongKey)

      Ok(views.html.createUser(message = "A new user has been successfully added", createUserForm, request.user.firstName))
    }

    def displayError(message: String): Result = {
      Ok(views.html.createUser(message, createUserForm, request.user.firstName))
    }

    def handleInvalidForm(form: Form[CreateUserFormData]): Future[Result] = {
      Future.successful(Ok(views.html.createUser(message = "Please, correct the highlighted fields.", form, request.user.firstName)))
    }

    def handleValidForm(createUserFormData: CreateUserFormData): Future[Result] = {
      val rateLimits: RateLimits = createUserFormData.tier match {
        case "Developer" => new RateLimits(720, 5000)
        case "Rights managed" => new RateLimits(720, 10000)
        case "Internal" => new RateLimits(720, 10000)
      }
      kong.registerUser(createUserFormData.email, rateLimits, createUserFormData.key) map {
        consumer => saveUserOnDB(consumer, createUserFormData, rateLimits)
      } recover {
        case ConflictFailure(message) => displayError("Conflict failure: " + message)
        case GenericFailure(message) => displayError(message)
      }
    }
    createUserForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def showKeys(direction: String, range: String) = maybeAuth { implicit request =>
    val (keys, hasNext): (List[BonoboInfo], Boolean) = dynamo.getKeys(direction, range)
    val totalKeys = dynamo.getNumberOfKeys
    range match {
      case "" => Ok(views.html.showKeys(keys, pageTitle = "All keys", "", hasNext, totalKeys, request.user.firstName))
      case _ => Ok(views.html.showKeys(keys, pageTitle = "All keys", direction, hasNext, totalKeys, request.user.firstName))
    }
  }

  def editUser(id: String) = maybeAuth { implicit request =>
    val result = dynamo.retrieveUser(id)
    val filledForm = editUserForm.fill(EditUserFormData(result.email, result.name, result.company, result.url))
    Ok(views.html.editUser(message = "", id, filledForm, request.user.firstName))
  }

  def updateUser(id: String) = maybeAuth.async { implicit request =>

    def handleInvalidForm(form: Form[EditUserFormData]): Future[Result] = {
      Future.successful(Ok(views.html.editUser(message = "Plase correct the highlighted fields", id, form, request.user.firstName)))
    }

    def handleValidForm(form: EditUserFormData): Future[Result] = {

      val updatedUser = BonoboUser(id, form)
      dynamo.updateBonoboUser(updatedUser)

      Future.successful(Ok(views.html.editUser(message = "The user has been successfully updated", id, editUserForm.fill(form), request.user.firstName)))
    }

    editUserForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def editKey(id: String) = maybeAuth { implicit request =>

    val result = dynamo.retrieveKey(id)
    val filledForm = editKeyForm.fill(EditKeyFormData(result.key, result.requestsPerDay,
      result.requestsPerMinute, result.tier, Some(false), result.status))
    Ok(views.html.editKey(message = "", id, filledForm, request.user.firstName))
  }

  def updateKey(consumerId: String) = maybeAuth.async { implicit request =>

    val oldKey = dynamo.retrieveKey(consumerId)

    def handleInvalidForm(form: Form[EditKeyFormData]): Future[Result] = {
      Future.successful(Ok(views.html.editKey(message = "Please, correct the highlighted fields.", consumerId, form, request.user.firstName)))
    }

    def updateKongKey(newFormData: EditKeyFormData): Result = {

      val updatedKey = KongKey(consumerId, newFormData, oldKey.createdAt)
      dynamo.updateKongKey(updatedKey)

      Ok(views.html.editKey(message = "The user has been successfully updated", consumerId, editKeyForm.fill(newFormData), request.user.firstName))
    }

    def handleValidForm(newFormData: EditKeyFormData): Future[Result] = {

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

      def activateKeyIfNecessary(): Future[String] = {
        if (oldKey.status == "Inactive" && newFormData.status == "Active") {
          kong.createKey(consumerId, Some(oldKey.key))
        } else {
          Future.successful(oldKey.key)
        }
      }

      for {
        _ <- updateRateLimitsIfNecessary()
        _ <- deactivateKeyIfNecessary()
        _ <- activateKeyIfNecessary()
      } yield {
        updateKongKey(newFormData)
        Ok(views.html.editKey(message = "The user has been successfully updated", consumerId, editKeyForm.fill(newFormData), request.user.firstName))
      }
    }

    editKeyForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def healthcheck = Action { Ok("OK") }
}

object Application {
  case class CreateUserFormData(email: String, name: String, company: String, url: String, tier: String, key: Option[String] = None)

  val createUserForm: Form[CreateUserFormData] = Form(
    mapping(
      "email" -> nonEmptyText,
      "name" -> nonEmptyText,
      "company" -> nonEmptyText,
      "url" -> nonEmptyText,
      "tier" -> nonEmptyText,
      "key" -> optional(text)
    )(CreateUserFormData.apply)(CreateUserFormData.unapply)
  )

  case class EditKeyFormData(key: String, requestsPerDay: Int, requestsPerMinute: Int, tier: String, defaultRequests: Option[Boolean], status: String)

  val editKeyForm: Form[EditKeyFormData] = Form(
    mapping(
      "key" -> nonEmptyText,
      "requestsPerDay" -> number,
      "requestsPerMinute" -> number,
      "tier" -> nonEmptyText,
      "defaultRequests" -> optional(boolean),
      "status" -> nonEmptyText
    )(EditKeyFormData.apply)(EditKeyFormData.unapply)
  )

  case class EditUserFormData(email: String, name: String, company: String, url: String)

  val editUserForm: Form[EditUserFormData] = Form(
    mapping(
      "email" -> nonEmptyText,
      "name" -> nonEmptyText,
      "company" -> nonEmptyText,
      "url" -> nonEmptyText
    )(EditUserFormData.apply)(EditUserFormData.unapply)
  )

  case class SearchFormData(query: String)

  val searchForm = Form(
    mapping(
      "query" -> nonEmptyText(minLength = 2, maxLength = 45)
    )(SearchFormData.apply)(SearchFormData.unapply)
  )
}
