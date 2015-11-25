package controllers

import kong.Kong
import models._
import play.api.Logger
import play.api.libs.json.{ Json, JsError, JsSuccess }
import play.api.mvc._
import store.DB
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

class Migration(dynamo: DB, kong: Kong) extends Controller {
  def migrate = Action(parse.json) { implicit request =>
    Logger.info(s"Handling migration request ${request.id}")
    request.body.validate[List[MasheryUser]] match {
      case JsSuccess(masheryUsers, _) => {
        val userResults = masheryUsers.map(handleMasheryUser)
        val result = handleUserResult(userResults)
        Logger.info(s"Returning result for migration request ${request.id}. Result: $result")
        Ok(Json.toJson(result))
      }
      case JsError(errorMessage) => {
        Logger.warn(s"Migration: Error when parsing json: $errorMessage")
        BadRequest(s"Invalid Json: $errorMessage")
      }
    }
  }

  import Migration._

  private def handleMasheryUser(user: MasheryUser): MigrateUserResult = {
    val bonoboId = java.util.UUID.randomUUID().toString
    val additionalInfo = AdditionalUserInfo(user.createdAt, MasheryRegistration)
    val bonoboUser = BonoboUser(bonoboId, user.email, unspecifiedIfEmpty(user.name), unspecifiedIfEmpty(user.companyName), unspecifiedIfEmpty(user.companyUrl), additionalInfo)
    handleUserAndKeys(bonoboUser, user.keys)
  }

  private def handleUserAndKeys(bonoboUser: BonoboUser, masheryKeys: List[MasheryKey]): MigrateUserResult = {
    if (dynamo.isEmailInUse(bonoboUser.email)) {
      EmailConflict(bonoboUser.email)
    } else {
      dynamo.saveUser(bonoboUser)
      val keyResults = masheryKeys.map(key => createKeyForUser(bonoboUser, key))
      MigratedUser(keyResults)
    }
  }

  private def createKeyForUser(bonoboUser: BonoboUser, masheryKey: MasheryKey): MigrateKeyResult = {
    try {
      if (dynamo.isKeyPresent(masheryKey.key)) {
        Logger.info(s"Key ${masheryKey.key} already taken")
        KeyConflict(masheryKey.key)
      } else {
        val rateLimits: RateLimits = RateLimits(masheryKey.requestsPerMinute, masheryKey.requestsPerDay)
        val future = kong.createConsumerAndKey(masheryKey.tier, rateLimits, Option(masheryKey.key)) map {
          consumer =>
            {
              val kongKey = KongKey(bonoboUser.bonoboId, consumer, RateLimits(masheryKey.requestsPerMinute, masheryKey.requestsPerDay), masheryKey.tier, unspecifiedIfEmpty(masheryKey.productName), unspecifiedIfEmpty(masheryKey.productUrl), masheryKey.status, masheryKey.createdAt)
              dynamo.saveKey(kongKey)
              if (masheryKey.status == KongKey.Inactive) kong.deleteKey(consumer.id)
              MigratedKey
            }
        }
        Await.result(future, atMost = 3.seconds)
      }
    } catch {
      case NonFatal(e) =>
        Logger.warn(s"Skipping key [${masheryKey.key}] because we got an exception", e)
        ThrewException(masheryKey.key)
    }
  }

  private def handleUserResult(userResults: List[MigrateUserResult]): MigrationResult = {
    userResults.foldLeft(MigrationResult(0, 0, List.empty, List.empty)) { (counter, result) =>
      result match {
        case MigratedUser(keys) => {
          keys.foldLeft(counter.copy(successfullyManagedUsers = counter.successfullyManagedUsers + 1)) { (keysCounter, keysResult) =>
            keysResult match {
              case MigratedKey => keysCounter.copy(successfullyManagedKeys = keysCounter.successfullyManagedKeys + 1)
              case KeyConflict(key) => keysCounter.copy(failedKeys = keysCounter.failedKeys :+ key)
              case ThrewException(key) => keysCounter.copy(failedKeys = keysCounter.failedKeys :+ key)
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

