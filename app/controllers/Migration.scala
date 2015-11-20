package controllers

import controllers.Forms.CreateKeyFormData
import kong.Kong
import kong.Kong.ConflictFailure
import models._
import play.api.Logger
import play.api.libs.json.{ JsError, JsSuccess }
import play.api.mvc._
import store.DB
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class Migration(dynamo: DB, kong: Kong) extends Controller {
  def migrate = Action(parse.json) { implicit request =>
    request.body.validate[List[MasheryUser]] match {
      case JsSuccess(j, _) => {
        j.foreach(handleMasheryUser)
      }
      case JsError(errorMessage) => Logger.warn(s"Migration: Error when parsing json: $errorMessage")
    }
    Ok("OK")
  }

  private def handleMasheryUser(user: MasheryUser): Future[Unit] = {
    val bonoboId = java.util.UUID.randomUUID().toString
    val additionalInfo = AdditionalUserInfo(user.createdAt, MasheryRegistration)
    val bonoboUser = BonoboUser(bonoboId, user.email, user.name, user.companyName, user.companyUrl, additionalInfo)
    //TODO: Check for empty fields

    createUserAndKeys(bonoboUser, user.keys)
  }

  private def createUserAndKeys(bonoboUser: BonoboUser, masheryKeys: List[MasheryKey]): Future[Unit] = {
    Logger.info(s"Migration: Creating user $bonoboUser with keys $masheryKeys")

    dynamo.getUserWithEmail(bonoboUser.email) match {
      case Some(user) => {
        Logger.warn(s"Migration: Email already taken when creating user with name ${bonoboUser.name} and key with value ${masheryKeys.head.key}")
        Future.failed(ConflictFailure("Email already taken. You cannot have two users with the same email."))
      }
      case None => {
        dynamo.saveUser(bonoboUser)
        masheryKeys.map(key => createKey(bonoboUser, key))
        Future.successful(())
      }
    }
  }

  private def createKey(bonoboUser: BonoboUser, key: MasheryKey): Future[Unit] = {
    Logger.info(s"Migration: Creating key for user $bonoboUser")
    def createConsumerAndKey: Future[Unit] = {
      val rateLimits: RateLimits = key.tier.rateLimit
      kong.createConsumerAndKey(key.tier, rateLimits, Some(key.key)) map {
        consumer => saveKeyOnDB(bonoboUser.bonoboId, consumer, rateLimits, key.tier, key.productName, key.productUrl)
      }
    }
    checkingIfKeyAlreadyTaken(key.key)(createConsumerAndKey)
  }

  private def saveKeyOnDB(userId: String, consumer: ConsumerCreationResult, rateLimits: RateLimits, tier: Tier, productName: String, productUrl: String): Unit = {
    val newKongKey = KongKey(userId, consumer, rateLimits, tier, productName, productUrl)
    dynamo.saveKey(newKongKey)
  }

  private def checkingIfKeyAlreadyTaken[A](key: String)(f: => Future[A]): Future[A] =
    if (dynamo.getKeyWithValue(key).isDefined) {
      Logger.warn(s"Migration: Key $key already taken")
      Future.failed(ConflictFailure("Key already taken."))
    } else f
}
