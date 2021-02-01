package logic

import controllers.Forms.{ CreateKeyFormData, CreateUserFormData, EditKeyFormData, EditUserFormData }
import kong.Kong
import kong.Kong.{ ConflictFailure, Happy }
import models._
import store.DB
import play.api.Logger

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
  def createUser(form: CreateUserFormData): Future[ConsumerCreationResult] = {
    Logger.info(s"ApplicationLogic: Creating user with name ${form.name}")
    def saveUserAndKeyOnDB(consumer: ConsumerCreationResult, formData: CreateUserFormData, rateLimits: RateLimits): Unit = {
      val labelIds = formData.labelIds.split(",").toList.filter(_.nonEmpty)
      Logger.info(s"Labels to be assigned with the ${form.name}: $labelIds")
      val newBonoboUser = BonoboUser(consumer.kongConsumerId, formData, labelIds)
      dynamo.saveUser(newBonoboUser)

      // when a new user is created, bonoboId and kongConsumerId (taken from the consumer object) will be the same
      saveKeyOnDB(userId = consumer.kongConsumerId, consumer, rateLimits, formData.tier, formData.productName, formData.productUrl, newBonoboUser.labelIds)
    }

    def createConsumerAndKey: Future[ConsumerCreationResult] = {
      val rateLimits: RateLimits = form.tier.rateLimit
      kong.createConsumerAndKey(form.tier, rateLimits, form.key) map {
        consumer =>
          saveUserAndKeyOnDB(consumer, form, rateLimits)
          consumer
      }
    }

    val emailAlreadyTaken = dynamo.isEmailInUse(form.email)
    Logger.info(s"ApplicationLogic: Check if user with email ${form.email} already exists: $emailAlreadyTaken")
    if (emailAlreadyTaken)
      Future.failed(ConflictFailure("Email already taken. You cannot have two users with the same email."))
    else
      checkingIfKeyAlreadyTaken(form.key)(createConsumerAndKey)
  }

  def updateUser(userId: String, form: EditUserFormData): Either[String, Unit] = {
    Logger.info(s"ApplicationLogic: Updating user with id $userId")
    def updateUserOnDB(oldUser: BonoboUser) = {
      val labelIds = form.labelIds.split(",").toList.filter(_.nonEmpty)
      Logger.info(s"Labels to be assigned with the ${form.name}: $labelIds")
      val updatedUser = BonoboUser(userId, form, oldUser.additionalInfo.createdAt, oldUser.additionalInfo.registrationType, labelIds)
      Right(dynamo.updateUser(updatedUser))
    }
    dynamo.getUserWithId(userId) match {
      case Some(oldUser) => {
        if (oldUser.email != form.email && dynamo.isEmailInUse(form.email))
          Left(s"A user with the email ${form.email} already exists.")
        else updateUserOnDB(oldUser)
      }
      case None => Left(s"Something bad happened when trying to get the user from the database.")
    }
  }

  /**
   * Creates a new key for the given user.
   * The key will be randomly generated if no custom key is specified.
   */
  def createKey(userId: String, form: CreateKeyFormData): Future[String] = {
    Logger.info(s"ApplicationLogic: Creating key for user with id $userId")
    def createConsumerAndKey: Future[String] = {
      val rateLimits: RateLimits = form.tier.rateLimit
      kong.createConsumerAndKey(form.tier, rateLimits, form.key) flatMap {
        consumer =>
          {
            saveKeyOnDB(userId, consumer, rateLimits, form.tier, form.productName, form.productUrl, dynamo.getLabelsFor(userId))
            Future.successful(consumer.key)
          }
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
  def updateKey(oldKey: KongKey, form: EditKeyFormData): Future[Unit] = {
    Logger.info(s"ApplicationLogic: Updating key with id ${oldKey.kongConsumerId}")
    val bonoboId = oldKey.bonoboId
    val kongConsumerId = oldKey.kongConsumerId

    def updateKongKeyOnDB(form: EditKeyFormData): Unit = {
      val updatedKey = {
        if (form.defaultRequests) {
          val defaultRateLimits = form.tier.rateLimit
          KongKey(bonoboId, kongConsumerId, form, oldKey.createdAt, defaultRateLimits, oldKey.rangeKey)
        } else KongKey(bonoboId, kongConsumerId, form, oldKey.createdAt, RateLimits(form.requestsPerMinute, form.requestsPerDay), oldKey.rangeKey)
      }
      dynamo.updateKey(updatedKey)
    }

    def updateUsernameIfNecessary(): Future[Happy.type] = {
      if (oldKey.tier != form.tier) {
        kong.updateConsumerUsername(kongConsumerId, form.tier)
      } else {
        Future.successful(Happy)
      }
    }

    def updateRateLimitsIfNecessary(): Future[Happy.type] = {
      val oldRateLimits = RateLimits(oldKey.requestsPerMinute, oldKey.requestsPerDay)
      val newRateLimits = {
        if (form.defaultRequests) form.tier.rateLimit
        else RateLimits(form.requestsPerMinute, form.requestsPerDay)
      }

      if (oldRateLimits != newRateLimits) {
        kong.updateConsumerRateLimit(kongConsumerId, newRateLimits)
      } else {
        Future.successful(Happy)
      }
    }

    def deactivateKeyIfNecessary(): Future[Happy.type] = {
      if (oldKey.status == KongKey.Active && form.status == KongKey.Inactive) {
        kong.deleteKey(kongConsumerId)
      } else {
        Future.successful(Happy)
      }
    }

    def activateKeyIfNecessary(): Future[String] = {
      if (oldKey.status == KongKey.Inactive && form.status == KongKey.Active) {
        kong.createKey(kongConsumerId, Some(oldKey.key))
      } else {
        Future.successful(oldKey.key)
      }
    }

    for {
      _ <- updateRateLimitsIfNecessary()
      _ <- updateUsernameIfNecessary()
      _ <- deactivateKeyIfNecessary()
      _ <- activateKeyIfNecessary()
    } yield {
      updateKongKeyOnDB(form)
    }
  }

  def deleteKey(key: KongKey): Future[Happy.type] = for {
    _ <- kong.deleteKey(key.kongConsumerId)
    _ <- Future.successful(dynamo.deleteKey(key))
  } yield Happy

  private def checkingIfKeyAlreadyTaken[A](key: Option[String])(f: => Future[A]): Future[A] = key match {
    case Some(value) =>
      if (dynamo.isKeyPresent(value))
        Future.failed(ConflictFailure("Key already taken."))
      else f
    case None => f
  }

  private def saveKeyOnDB(userId: String, consumer: ConsumerCreationResult, rateLimits: RateLimits, tier: Tier, productName: String, productUrl: Option[String], labelIds: List[String]): Unit = {
    val newKongKey = KongKey(userId, consumer, rateLimits, tier, productName, productUrl)
    dynamo.saveKey(newKongKey, labelIds)
  }
}
