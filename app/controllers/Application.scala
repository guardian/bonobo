package controllers

import models._
import com.gu.googleauth.{ UserIdentity, GoogleAuthConfig }
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraint
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

      Redirect("/user/" + consumer.id + "/edit")
    }

    def displayError(message: String): Result = {
      Ok(views.html.createUser(message, createUserForm, request.user.firstName))
    }

    def handleInvalidForm(form: Form[CreateUserFormData]): Future[Result] = {
      Future.successful(Ok(views.html.createUser(message = "Please, correct the highlighted fields.", form, request.user.firstName)))
    }

    def handleValidForm(createUserFormData: CreateUserFormData): Future[Result] = {
      val rateLimits: RateLimits = RateLimits.matchTierWithRateLimits(createUserFormData.tier)
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
    val consumer = dynamo.getUserWithId(id)
    val userKeys = dynamo.getAllKeysWithId(id)
    val filledForm = editUserForm.fill(EditUserFormData(consumer.email, consumer.name, consumer.company, consumer.url))

    Ok(views.html.editUser(message = "", id, filledForm, request.user.firstName, userKeys))
  }

  def updateUser(id: String) = maybeAuth.async { implicit request =>

    val userKeys = dynamo.getAllKeysWithId(id)

    def handleInvalidForm(form: Form[EditUserFormData]): Future[Result] = {

      Future.successful(Ok(views.html.editUser(message = "Please correct the highlighted fields", id, form, request.user.firstName, userKeys)))
    }

    def handleValidForm(form: EditUserFormData): Future[Result] = {

      val updatedUser = BonoboUser(id, form)
      dynamo.updateBonoboUser(updatedUser)

      Future.successful(Ok(views.html.editUser(message = "The user has been successfully updated", id,
        editUserForm.fill(form), request.user.firstName, userKeys)))
    }

    editUserForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def createNewKeyForm(userId: String) = maybeAuth { implicit request =>
    Ok(views.html.createKey(message = "", userId, createKeyForm, request.user.firstName))
  }

  def createKey(userId: String) = maybeAuth.async { implicit request =>

    def saveNewKeyOnDB(consumer: UserCreationResult, form: CreateKeyFormData, rateLimits: RateLimits): Result = {

      val newKongKey = new KongKey(userId, consumer.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, form.tier, "Active", consumer.createdAt)
      dynamo.saveKongKey(newKongKey)

      Redirect("/user/" + userId + "/edit")
    }

    def handleInvalidForm(brokenKeyForm: Form[CreateKeyFormData]): Future[Result] = {

      Future.successful(Ok(views.html.createKey(message = "Plase correct the highlighted fields", userId, brokenKeyForm, request.user.firstName)))
    }

    def handleValidForm(form: CreateKeyFormData): Future[Result] = {

      val rateLimits: RateLimits = RateLimits.matchTierWithRateLimits(form.tier)

      kong.registerUser(java.util.UUID.randomUUID.toString, rateLimits, form.key) map {
        consumer => saveNewKeyOnDB(consumer, form, rateLimits)
      } recover {
        case ConflictFailure(message) => Conflict(views.html.createKey("Conflict failure: " + message, userId, createKeyForm, request.user.firstName))
        case GenericFailure(message) => InternalServerError(views.html.createKey("Generic failure: " + message, userId, createKeyForm, request.user.firstName))
      }
    }

    createKeyForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def editKey(keyValue: String) = maybeAuth { implicit request =>

    val key = dynamo.retrieveKey(keyValue)
    val filledForm = editKeyForm.fill(EditKeyFormData(key.key, key.requestsPerDay,
      key.requestsPerMinute, key.tier, defaultRequests = false, key.status))

    Ok(views.html.editKey(message = "", keyValue, filledForm, request.user.firstName))
  }

  def updateKey(keyValue: String) = maybeAuth.async { implicit request =>

    val oldKey = dynamo.retrieveKey(keyValue)
    val consumerId = oldKey.bonoboId

    def handleInvalidForm(form: Form[EditKeyFormData]): Future[Result] = {
      Future.successful(Ok(views.html.editKey(message = "Please correct the highlighted fields.", keyValue, form, request.user.firstName)))
    }

    def updateKongKey(newFormData: EditKeyFormData): Unit = {
      val updatedKey = {
        if (newFormData.defaultRequests) {
          val defaultRateLimits = RateLimits.matchTierWithRateLimits(newFormData.tier)
          KongKey(consumerId, newFormData, oldKey.createdAt, defaultRateLimits)
        } else KongKey(consumerId, newFormData, oldKey.createdAt, RateLimits(newFormData.requestsPerMinute, newFormData.requestsPerDay))
      }
      dynamo.updateKongKey(updatedKey)
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
        Redirect("/user/" + consumerId + "/edit")
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
      "email" -> email,
      "name" -> nonEmptyText,
      "company" -> nonEmptyText,
      "url" -> nonEmptyText,
      "tier" -> nonEmptyText,
      "key" -> optional(text.verifying("Invalid key - do not use spaces", key => !key.contains(' ')))
    )(CreateUserFormData.apply)(CreateUserFormData.unapply)
  )

  case class EditUserFormData(email: String, name: String, company: String, url: String)

  val editUserForm: Form[EditUserFormData] = Form(
    mapping(
      "email" -> email,
      "name" -> nonEmptyText,
      "company" -> nonEmptyText,
      "url" -> nonEmptyText
    )(EditUserFormData.apply)(EditUserFormData.unapply)
  )

  case class CreateKeyFormData(key: Option[String], tier: String)

  val createKeyForm: Form[CreateKeyFormData] = Form(
    mapping(
      "key" -> optional(text.verifying("Invalid key - do not use spaces", key => !key.contains(' '))),
      "tier" -> nonEmptyText
    )(CreateKeyFormData.apply)(CreateKeyFormData.unapply)
  )

  case class EditKeyFormData(key: String, requestsPerDay: Int, requestsPerMinute: Int, tier: String, defaultRequests: Boolean, status: String)

  val editKeyForm: Form[EditKeyFormData] = Form(
    mapping(
      "key" -> nonEmptyText,
      "requestsPerDay" -> number,
      "requestsPerMinute" -> number,
      "tier" -> nonEmptyText,
      "defaultRequests" -> boolean,
      "status" -> nonEmptyText
    )(EditKeyFormData.apply)(EditKeyFormData.unapply)
  )

  case class SearchFormData(query: String)

  val searchForm = Form(
    mapping(
      "query" -> nonEmptyText(minLength = 2, maxLength = 45)
    )(SearchFormData.apply)(SearchFormData.unapply)
  )
}
