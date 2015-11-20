package controllers

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
  def migrate = Action.async(parse.json) { implicit request =>
    request.body.validate[List[MasheryUser]] match {
      case JsSuccess(masheryUsers, _) => {
        Future.traverse(masheryUsers)(masheryUser => handleMasheryUser(masheryUser)) map (_ => Ok("OK"))
      }
      case JsError(errorMessage) => {
        Logger.warn(s"Migration: Error when parsing json: $errorMessage")
        Future.successful(BadRequest(s"Invalid Json: $errorMessage"))
      }
    }
  }

  import Migration._

  private def handleMasheryUser(user: MasheryUser): Future[List[Unit]] = {
    val bonoboId = java.util.UUID.randomUUID().toString
    val additionalInfo = AdditionalUserInfo(user.createdAt, MasheryRegistration)
    val bonoboUser = BonoboUser(bonoboId, user.email, user.name, unspecifiedIfEmpty(user.companyName), unspecifiedIfEmpty(user.companyUrl), additionalInfo)
    handleUserAndKeys(bonoboUser, user.keys)
  }

  private def handleUserAndKeys(bonoboUser: BonoboUser, masheryKeys: List[MasheryKey]): Future[List[Unit]] = {
    dynamo.getUserWithEmail(bonoboUser.email) match {
      case Some(user) => {
        Logger.warn(s"Migration: Email already taken when creating user with name ${bonoboUser.name} and keys with value ${masheryKeys.head.key}")
        Future.failed(ConflictFailure("Email already taken. You cannot have two users with the same email."))
      }
      case None => {
        dynamo.saveUser(bonoboUser)
        Future.traverse(masheryKeys)(key => createKey(bonoboUser, key))
      }
    }
  }

  private def createKey(bonoboUser: BonoboUser, masheryKey: MasheryKey): Future[Unit] = {
    if (dynamo.getKeyWithValue(masheryKey.key).isDefined) {
      Logger.warn(s"Migration: Key $masheryKey already taken")
      Future.failed(ConflictFailure("Key already taken."))
    } else {
      val rateLimits: RateLimits = RateLimits(masheryKey.requestsPerMinute, masheryKey.requestsPerDay)
      kong.createConsumerAndKey(masheryKey.tier, rateLimits, Option(masheryKey.key)) flatMap {
        consumer =>
          {
            val kongKey = KongKey(bonoboUser.bonoboId, consumer, RateLimits(masheryKey.requestsPerMinute, masheryKey.requestsPerDay), masheryKey.tier, masheryKey.productName, masheryKey.productUrl, masheryKey.status, masheryKey.createdAt)
            dynamo.saveKey(kongKey)
            if (masheryKey.status == KongKey.Inactive) kong.deleteKey(consumer.id).map(_ => ())
            else Future.successful(())
          }
      }
    }
  }
}

object Migration {
  def unspecifiedIfEmpty(s: String) = if (s.isEmpty) "Unspecified" else s
}
