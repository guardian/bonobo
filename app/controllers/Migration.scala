package controllers

import kong.Kong
import models._
import play.api.Logger
import play.api.libs.json.{ Json, JsError, JsSuccess }
import play.api.mvc._
import store.DB
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class Migration(dynamo: DB, kong: Kong) extends Controller {
  def migrate = Action.async(parse.json) { implicit request =>
    request.body.validate[List[MasheryUser]] match {
      case JsSuccess(masheryUsers, _) => {
        Future.traverse(masheryUsers)(masheryUser => handleMasheryUser(masheryUser)) map { userResults =>
          val count = handleUserResult(userResults)
          Logger.info(s"Number of successful users: $count")
          Ok(Json.toJson(count))
        }
      }
      case JsError(errorMessage) => {
        Logger.warn(s"Migration: Error when parsing json: $errorMessage")
        Future.successful(BadRequest(s"Invalid Json: $errorMessage"))
      }
    }
  }

  import Migration._

  private def handleMasheryUser(user: MasheryUser): Future[MigrateUserResult] = {
    val bonoboId = java.util.UUID.randomUUID().toString
    val additionalInfo = AdditionalUserInfo(user.createdAt, MasheryRegistration)
    val bonoboUser = BonoboUser(bonoboId, user.email, user.name, unspecifiedIfEmpty(user.companyName), unspecifiedIfEmpty(user.companyUrl), additionalInfo)
    handleUserAndKeys(bonoboUser, user.keys)
  }

  private def handleUserAndKeys(bonoboUser: BonoboUser, masheryKeys: List[MasheryKey]): Future[MigrateUserResult] = {
    dynamo.getUserWithEmail(bonoboUser.email) match {
      case Some(user) => Future.successful(EmailConflict(user.email))
      case None => {
        dynamo.saveUser(bonoboUser)
        Future.traverse(masheryKeys)(key => createKeyForUser(bonoboUser, key)).map { keyResult => MigratedUser(keyResult) }
      }
    }
  }

  private def createKeyForUser(bonoboUser: BonoboUser, masheryKey: MasheryKey): Future[MigrateKeyResult] = {
    if (dynamo.getKeyWithValue(masheryKey.key).isDefined) {
      Logger.info(s"Key ${masheryKey.key} already taken")
      Future.successful(KeyConflict(masheryKey.key))
    } else {
      val rateLimits: RateLimits = RateLimits(masheryKey.requestsPerMinute, masheryKey.requestsPerDay)
      kong.createConsumerAndKey(masheryKey.tier, rateLimits, Option(masheryKey.key)) map {
        consumer =>
          {
            val kongKey = KongKey(bonoboUser.bonoboId, consumer, RateLimits(masheryKey.requestsPerMinute, masheryKey.requestsPerDay), masheryKey.tier, masheryKey.productName, masheryKey.productUrl, masheryKey.status, masheryKey.createdAt)
            dynamo.saveKey(kongKey)
            if (masheryKey.status == KongKey.Inactive) kong.deleteKey(consumer.id)
            MigratedKey
          }
      }
    }
  }

  private def handleUserResult(userResults: List[MigrateUserResult]): MigrationResult = {
    userResults.foldLeft(MigrationResult(0, 0, List.empty, List.empty)) { (counter, result) =>
      result match {
        case MigratedUser(keys) => {
          keys.foldLeft(counter.copy(successfullyManagedUsers = counter.successfullyManagedUsers + 1)) { (keysCounter, keysResult) =>
            keysResult match {
              case MigratedKey => keysCounter.copy(successfullyManagedKeys = keysCounter.successfullyManagedKeys + 1)
              case KeyConflict(key) => keysCounter.copy(keyConflicts = keysCounter.keyConflicts :+ KeyConflict(key))
            }
          }
        }
        case EmailConflict(email) => counter.copy(userConflicts = counter.userConflicts :+ EmailConflict(email))
      }
    }
  }
}

object Migration {
  def unspecifiedIfEmpty(s: String) = if (s.isEmpty) "Unspecified" else s
}

