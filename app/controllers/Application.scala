package controllers

import com.amazonaws.services.simpleemail.model.SendEmailResult
import email.MailClient
import logic.ApplicationLogic
import models._
import com.gu.googleauth.{ UserIdentity, GoogleAuthConfig }
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._
import store._
import kong._
import kong.Kong._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application(dynamo: DB, kong: Kong, awsEmail: MailClient, val messagesApi: MessagesApi, val authConfig: GoogleAuthConfig, val enableAuth: Boolean) extends Controller
    with AuthActions
    with I18nSupport {

  import Application._
  import Forms._

  object FakeAuthAction extends AuthenticatedBuilder[UserIdentity](userinfo = _ => Some(UserIdentity("", "", "First", "Last", Long.MaxValue, None)))

  private def maybeAuth: AuthenticatedBuilder[UserIdentity] = if (enableAuth) AuthAction else FakeAuthAction

  private val logic = new ApplicationLogic(dynamo, kong)

  def showKeys(direction: String, range: Option[String]) = maybeAuth { implicit request =>
    val resultsPage = dynamo.getKeys(direction, range)
    val totalKeys = dynamo.getNumberOfKeys()
    val givenDirection = if (range.isDefined) direction else ""
    Ok(views.html.showKeys(resultsPage.items, givenDirection, resultsPage.hasNext, totalKeys, request.user.firstName, pageTitle = "All Keys"))
  }

  def search = maybeAuth { implicit request =>
    searchForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.showKeys(List.empty, lastDirection = "", hasNext = false, totalKeys = 0, request.user.firstName, pageTitle = "Invalid search", error = Some("Try again with a valid query.")))
      },
      searchFormData => {
        val keys: List[BonoboInfo] = dynamo.search(searchFormData.query)
        val searchResultsMessage = s"Search results for query: ${searchFormData.query}"
        Ok(views.html.showKeys(keys, lastDirection = "", hasNext = false, keys.length, request.user.firstName, pageTitle = searchResultsMessage, query = Some(searchFormData.query)))
      }
    )
  }

  def createUserPage = maybeAuth { implicit request =>
    Ok(views.html.createUser(createUserForm, request.user.firstName, createUserPageTitle))
  }

  def createUser = maybeAuth.async { implicit request =>
    def handleInvalidForm(form: Form[CreateUserFormData]): Future[Result] = {
      Future.successful(BadRequest(views.html.createUser(form, request.user.firstName, createUserPageTitle, error = Some(invalidFormMessage))))
    }

    def handleValidForm(formData: CreateUserFormData): Future[Result] = {
      logic.createUser(formData) flatMap { consumer =>
        awsEmail.sendEmailNewKey(formData.email, consumer.key) map {
          result => Redirect(routes.Application.editUserPage(consumer.id))
        } recover {
          case _ => Redirect(routes.Application.editUserPage(consumer.id)).flashing("error" -> s"We were unable to send the email with the new key. Please contact ${formData.email}.")
        }
      } recover {
        case ConflictFailure(errorMessage) => Conflict(views.html.createUser(createUserForm.fill(formData), request.user.firstName, createUserPageTitle, error = Some(errorMessage)))
        case GenericFailure(errorMessage) => InternalServerError(views.html.createUser(createUserForm.fill(formData), request.user.firstName, createUserPageTitle, error = Some(errorMessage)))
      }
    }
    createUserForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def editUserPage(id: String) = maybeAuth { implicit request =>
    val userKeys = dynamo.getKeysWithUserId(id)
    dynamo.getUserWithId(id) match {
      case Some(consumer) => {
        val filledForm = editUserForm.fill(EditUserFormData(consumer.name, consumer.email, consumer.productName, consumer.productUrl, consumer.companyName, consumer.companyUrl))
        Ok(views.html.editUser(id, filledForm, Some(consumer.additionalInfo), request.user.firstName, userKeys, editUserPageTitle))
      }
      case None => {
        NotFound(views.html.editUser(id, editUserForm, None, request.user.firstName, userKeys, editUserPageTitle, error = Some("User not found.")))
      }
    }
  }

  def editUser(id: String) = maybeAuth { implicit request =>
    val userKeys = dynamo.getKeysWithUserId(id)
    val additionalInfo = dynamo.getUserWithId(id) match {
      case Some(user) => Some(user.additionalInfo)
      case None => None
    }
    def handleInvalidForm(form: Form[EditUserFormData]): Result = {
      BadRequest(views.html.editUser(id, form, additionalInfo, request.user.firstName, userKeys, editUserPageTitle, error = Some(invalidFormMessage)))
    }

    def handleValidForm(form: EditUserFormData): Result = {
      logic.updateUser(id, form) match {
        case Left(error) => Conflict(views.html.editUser(id, editUserForm.fill(form), additionalInfo, request.user.firstName, userKeys, editUserPageTitle, error = Some(error)))
        case Right(_) => Ok(views.html.editUser(id, editUserForm.fill(form), additionalInfo, request.user.firstName, userKeys, editUserPageTitle, success = Some("The user has been successfully updated.")))
      }
    }

    editUserForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def createKeyPage(userId: String) = maybeAuth { implicit request =>
    Ok(views.html.createKey(userId, createKeyForm, request.user.firstName, createKeyPageTitle))
  }

  def createKey(userId: String) = maybeAuth.async { implicit request =>
    def handleInvalidForm(brokenKeyForm: Form[CreateKeyFormData]): Future[Result] = {
      Future.successful(BadRequest(views.html.createKey(userId, brokenKeyForm, request.user.firstName, createKeyPageTitle, error = Some(invalidFormMessage))))
    }

    def handleValidForm(form: CreateKeyFormData): Future[Result] = {
      logic.createKey(userId, form) flatMap { key =>
        val user = dynamo.getUserWithId(userId)
        user match {
          case Some(u) => {
            awsEmail.sendEmailNewKey(u.email, key) map {
              result => Redirect(routes.Application.editUserPage(userId))
            } recover {
              case _ => Redirect(routes.Application.editUserPage(userId)).flashing("error" -> s"We were unable to send the email with the new key. Please contact ${u.email}.")
            }
          }
          case None => Future.successful(NotFound(views.html.createKey(userId, createKeyForm.fill(form), request.user.firstName, createKeyPageTitle, error = Some(s"User not found"))))
        }
      } recover {
        case ConflictFailure(message) => Conflict(views.html.createKey(userId, createKeyForm.fill(form), request.user.firstName, createKeyPageTitle, error = Some(s"Conflict failure: $message")))
        case GenericFailure(message) => InternalServerError(views.html.createKey(userId, createKeyForm.fill(form), request.user.firstName, createKeyPageTitle, error = Some(s"Generic failure: $message")))
      }
    }

    createKeyForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def editKeyPage(keyValue: String) = maybeAuth { implicit request =>
    dynamo.getKeyWithValue(keyValue) match {
      case Some(value) => {
        val filledForm = editKeyForm.fill(EditKeyFormData(value.key, value.requestsPerDay,
          value.requestsPerMinute, value.tier, defaultRequests = false, value.status))
        Ok(views.html.editKey(value.bonoboId, filledForm, request.user.firstName, editKeyPageTitle))
      }
      case None => NotFound
    }
  }

  def editKey(keyValue: String) = maybeAuth.async { implicit request =>
    def retrievingKeyFromDynamo(f: KongKey => Future[Result]): Future[Result] = {
      val oldKey = dynamo.getKeyWithValue(keyValue)
      oldKey match {
        case Some(key) => f(key)
        case None => Future.successful(NotFound)
      }
    }

    def handleValidForm(newFormData: EditKeyFormData): Future[Result] = {
      retrievingKeyFromDynamo { key =>
        logic.updateKey(key, newFormData).map { _ =>
          Redirect(routes.Application.editUserPage(key.bonoboId))
        } recover {
          case ConflictFailure(message) => Conflict(views.html.editKey(key.bonoboId, editKeyForm.fill(newFormData), request.user.firstName, editKeyPageTitle, error = Some(s"Conflict failure: $message")))
          case GenericFailure(message) => InternalServerError(views.html.editKey(key.bonoboId, editKeyForm.fill(newFormData), request.user.firstName, editKeyPageTitle, error = Some(s"Generic failure: $message")))
        }
      }
    }

    def handleInvalidForm(form: Form[EditKeyFormData]): Future[Result] = {
      retrievingKeyFromDynamo { key =>
        val error = if (form.errors(0).message.contains("requests")) Some(form.errors(0).message) else Some(invalidFormMessage)
        Future.successful(BadRequest(views.html.editKey(key.bonoboId, form, request.user.firstName, editKeyPageTitle, error = error)))
      }
    }

    editKeyForm.bindFromRequest.fold[Future[Result]](handleInvalidForm, handleValidForm)
  }

  def healthcheck = Action { Ok("OK") }
}

object Application {
  import Forms._

  val keyRegexPattern = """^[-a-zA-Z0-9]*$""".r.pattern
  val invalidFormMessage = "Please correct the highlighted fields."
  val invalidKeyMessage = "Invalid key: use only a-z, A-Z, 0-9 and dashes."
  val invalidTierMessage = "Invalid tier"
  val createUserPageTitle = "Create user"
  val editUserPageTitle = "Edit user"
  val createKeyPageTitle = "Create key"
  val editKeyPageTitle = "Edit key"

  val createUserForm: Form[CreateUserFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "productName" -> nonEmptyText,
      "productUrl" -> nonEmptyText,
      "companyName" -> nonEmptyText,
      "companyUrl" -> optional(text),
      "tier" -> nonEmptyText.verifying(invalidTierMessage, tier => Tier.isValid(tier)).transform(tier => Tier.withName(tier).get, (tier: Tier) => tier.toString),
      "key" -> optional(text.verifying(invalidKeyMessage, key => keyRegexPattern.matcher(key).matches()))
    )(CreateUserFormData.apply)(CreateUserFormData.unapply)
  )

  val editUserForm: Form[EditUserFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email,
      "productName" -> nonEmptyText,
      "productUrl" -> nonEmptyText,
      "companyName" -> nonEmptyText,
      "companyUrl" -> optional(text)
    )(EditUserFormData.apply)(EditUserFormData.unapply)
  )

  val createKeyForm: Form[CreateKeyFormData] = Form(
    mapping(
      "key" -> optional(text.verifying(invalidKeyMessage, key => keyRegexPattern.matcher(key).matches())),
      "tier" -> nonEmptyText.verifying(invalidTierMessage, tier => Tier.isValid(tier)).transform(tier => Tier.withName(tier).get, (tier: Tier) => tier.toString)
    )(CreateKeyFormData.apply)(CreateKeyFormData.unapply)
  )

  val editKeyForm: Form[EditKeyFormData] = Form(
    mapping(
      "key" -> nonEmptyText,
      "requestsPerDay" -> number,
      "requestsPerMinute" -> number,
      "tier" -> nonEmptyText.verifying(invalidTierMessage, tier => Tier.isValid(tier)).transform(tier => Tier.withName(tier).get, (tier: Tier) => tier.toString),
      "defaultRequests" -> boolean,
      "status" -> nonEmptyText
    )(EditKeyFormData.apply)(EditKeyFormData.unapply) verifying ("The number of requests per day is smaller than the number of requests per minute.", data => data.validateRequests)
  )

  val searchForm = Form(
    mapping(
      "query" -> nonEmptyText(minLength = 2, maxLength = 45)
    )(SearchFormData.apply)(SearchFormData.unapply)
  )
}
