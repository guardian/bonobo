package controllers

import kong.Kong
import kong.Kong.ConflictFailure
import models._
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{ Json, JsError, JsSuccess, JsValue }
import play.api.mvc._
import store.DB
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

case class MasheryUser(
  name: String,
  email: String,
  productName: String,
  productUrl: String,
  companyName: String,
  companyUrl: Option[String],
  createdAt: DateTime,
  keys: List[MasheryKey])

object MasheryKey {
  implicit val keyRead = Json.reads[MasheryKey]
}

object MasheryUser {
  implicit val userRead = Json.reads[MasheryUser]
}

case class MasheryKey(
  key: String,
  requestsPerDay: Int,
  requestsPerMinute: Int,
  tier: Tier,
  status: String,
  createdAt: DateTime)

class Migration(dynamo: DB, kong: Kong) extends Controller {
  def migrate = Action(parse.json) { implicit request =>
    request.body.validate[List[MasheryUser]] match {
      case JsSuccess(j, _) => j.map(handleValidJson)
      case JsError(errorMessage) => Logger.warn(s"Error when parsing json: $errorMessage")
    }
    Ok("OK")
  }

  private def handleValidJson(user: MasheryUser): Unit = {
    val bonoboId = java.util.UUID.randomUUID().toString
    val additionalInfo = AdditionalUserInfo(user.createdAt, MasheryRegistration)
    val bonoboUser = BonoboUser(bonoboId, user.email, user.name, user.productName, user.productUrl, user.companyName, user.companyUrl, additionalInfo)
    user.keys.map(createUserAndKey(bonoboUser, _))
  }

  private def createUserAndKey(bonoboUser: BonoboUser, masheryKey: MasheryKey): Future[String] = {
    Logger.info(s"Migration: Creating user with name ${bonoboUser.name}")

    def createConsumer: Future[String] = {
      val rateLimits: RateLimits = masheryKey.tier.rateLimit
      kong.createConsumerAndKey(masheryKey.tier, rateLimits, Some(masheryKey.key)) map {
        consumer =>
          dynamo.saveUser(bonoboUser)
          val kongKey = KongKey(bonoboUser.bonoboId, consumer.id, masheryKey.key, masheryKey.requestsPerDay, masheryKey.requestsPerMinute, masheryKey.tier, masheryKey.status, masheryKey.createdAt, masheryKey.createdAt.toString)
          dynamo.saveKey(kongKey)
          consumer.id
      }
    }

    dynamo.getUserWithEmail(bonoboUser.email) match {
      case Some(user) => Future.failed(ConflictFailure("Email already taken. You cannot have two users with the same email."))
      case None => checkingIfKeyAlreadyTaken(masheryKey.key)(createConsumer)
    }
  }

  private def saveKeyOnDB(userId: String, consumer: ConsumerCreationResult, rateLimits: RateLimits, tier: Tier): Unit = {
    val newKongKey = KongKey(userId, consumer, rateLimits, tier)
    dynamo.saveKey(newKongKey)
  }

  private def checkingIfKeyAlreadyTaken[A](key: String)(f: => Future[A]): Future[A] =
    if (dynamo.getKeyWithValue(key).isDefined)
      Future.failed(ConflictFailure("Key already taken."))
    else f
}
