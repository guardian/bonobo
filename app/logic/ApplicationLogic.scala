package logic

import controllers.Forms.{ EditKeyFormData, CreateKeyFormData, CreateUserFormData }
import kong.Kong
import kong.Kong.{ ConflictFailure, Happy }
import models._
import store.DB

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Orchestrates Kong and Dynamo to perform CRUD operations on users and keys.
 *
 * There are 3 concepts at play here:
 *  - User = a real human, represented as a user in Bonobo. They may have multiple keys.
 *  - Consumer = a Kong consumer. There is one per key, because Kong enforces rate limits on consumers rather than individual keys.
 *  - Key = a Kong key.
 *
 *  So, every time we create a new key for an existing user, we have to create a corresponding Consumer in Kong as well.
 *
 *  When we deactivate a key, we keep the consumer but delete the key from Kong.
 *  Thus, rate limit changes made while a key is inactive will take effect as expected.
 */
class ApplicationLogic(dynamo: DB, kong: Kong) {

  /**
   * Creates a consumer and key on Kong and a Bonobo user,
   * and saves the user and key in Dynamo.
   * The key will be randomly generated if no custom key is specified.
   *
   *
   * @return a Future of the newly created Kong consumer's ID
   */
  def createUser(form: CreateUserFormData): Future[String] = {
    def saveUserAndKeyOnDB(consumer: ConsumerCreationResult, formData: CreateUserFormData, rateLimits: RateLimits): Unit = {
      val newBonoboUser = BonoboUser(consumer.id, formData)
      dynamo.saveBonoboUser(newBonoboUser)

      // when a new user is created, bonoboId and kongId (taken from the consumer object) will be the same
      saveKeyOnDB(userId = consumer.id, consumer, rateLimits, formData.tier)
    }

    def createConsumerAndKey: Future[String] = {
      val rateLimits: RateLimits = form.tier.rateLimit
      kong.createConsumerAndKey(rateLimits, form.key) map {
        consumer =>
          saveUserAndKeyOnDB(consumer, form, rateLimits)
          consumer.id
      }
    }

    checkingIfKeyAlreadyTaken(form.key)(createConsumerAndKey)
  }

  /**
   * Creates a new key for the given user.
   * The key will be randomly generated if no custom key is specified.
   */
  def createKey(userId: String, form: CreateKeyFormData): Future[Unit] = {
    def createConsumerAndKey: Future[Unit] = {
      val rateLimits: RateLimits = form.tier.rateLimit
      kong.createConsumerAndKey(rateLimits, form.key) map {
        consumer => saveKeyOnDB(userId, consumer, rateLimits, form.tier)
      }
    }

    checkingIfKeyAlreadyTaken(form.key)(createConsumerAndKey)
  }

  /**
   * Updates an existing key. Operations performed may include one or more of:
   *  - updating the Kong consumer's rate limits
   *  - activating/deactivating the key (i.e. creating/deleting the key in Kong)
   *  - updating properties of the key (e.g. the tier, rate limits) in Dynamo.
   */
  def updateKey(oldKey: KongKey, newFormData: EditKeyFormData): Future[Unit] = {
    val bonoboId = oldKey.bonoboId
    val kongId = oldKey.kongId

    def updateKongKeyOnDB(newFormData: EditKeyFormData): Unit = {
      val updatedKey = {
        if (newFormData.defaultRequests) {
          val defaultRateLimits = newFormData.tier.rateLimit
          KongKey(bonoboId, kongId, newFormData, oldKey.createdAt, defaultRateLimits, oldKey.rangeKey)
        } else KongKey(bonoboId, kongId, newFormData, oldKey.createdAt, RateLimits(newFormData.requestsPerMinute, newFormData.requestsPerDay), oldKey.rangeKey)
      }
      dynamo.updateKongKey(updatedKey)
    }

    def updateRateLimitsIfNecessary(): Future[Happy.type] = {
      if (oldKey.requestsPerDay != newFormData.requestsPerDay || oldKey.requestsPerMinute != newFormData.requestsPerMinute) {
        kong.updateConsumer(kongId, new RateLimits(newFormData.requestsPerMinute, newFormData.requestsPerDay))
      } else {
        Future.successful(Happy)
      }
    }

    def deactivateKeyIfNecessary(): Future[Happy.type] = {
      if (oldKey.status == KongKey.Active && newFormData.status == KongKey.Inactive) {
        kong.deleteKey(kongId)
      } else {
        Future.successful(Happy)
      }
    }

    def activateKeyIfNecessary(): Future[String] = {
      if (oldKey.status == KongKey.Inactive && newFormData.status == KongKey.Active) {
        kong.createKey(kongId, Some(oldKey.key))
      } else {
        Future.successful(oldKey.key)
      }
    }

    for {
      _ <- updateRateLimitsIfNecessary()
      _ <- deactivateKeyIfNecessary()
      _ <- activateKeyIfNecessary()
    } yield {
      updateKongKeyOnDB(newFormData)
    }
  }

  private def checkingIfKeyAlreadyTaken[A](key: Option[String])(f: => Future[A]): Future[A] = key match {
    case Some(value) =>
      if (dynamo.retrieveKey(value).isDefined)
        Future.failed(ConflictFailure("Key already taken."))
      else f
    case None => f
  }

  private def saveKeyOnDB(userId: String, consumer: ConsumerCreationResult, rateLimits: RateLimits, tier: Tier): Unit = {
    val newKongKey = KongKey(userId, consumer, rateLimits, tier)
    dynamo.saveKongKey(newKongKey)
  }
}
