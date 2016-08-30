package kong

import kong.Kong.Happy
import models.{ ConsumerCreationResult, RateLimits, Tier }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class KongKeyWrapper(key: String, migrationKey: Option[String])
case class ConsumerCreationResultWrapper(consumerCR: ConsumerCreationResult, migrationConsumerCR: ConsumerCreationResult)

case class KongWrapper(existingKong: Kong, newKong: Kong) {

  def createConsumerAndKey(tier: Tier, rateLimit: RateLimits, key: Option[String]): Future[ConsumerCreationResultWrapper] = {
    val apiKey = key.getOrElse(java.util.UUID.randomUUID.toString)
    for {
      cr1 <- existingKong.createConsumerAndKey(tier, rateLimit, apiKey)
      cr2 <- newKong.createConsumerAndKey(tier, rateLimit, apiKey)
    } yield {
      ConsumerCreationResultWrapper(cr1, cr2)
    }
  }

  def createKey(consumerId: String, maybeMigrationKongId: Option[String], customKey: Option[String] = None): Future[KongKeyWrapper] = {
    val apiKey = customKey.getOrElse(java.util.UUID.randomUUID.toString)
    maybeMigrationKongId match {
      case Some(migrationKongId) =>
        for {
          key <- existingKong.createKey(consumerId, apiKey)
          migrationKey <- newKong.createKey(migrationKongId, apiKey)
        } yield KongKeyWrapper(key, Some(migrationKey))

      case None => existingKong.createKey(consumerId, apiKey).map(KongKeyWrapper(_, None))
    }
  }

  def updateConsumerUsername(consumerId: String, maybeMigrationKongId: Option[String], tier: Tier): Future[Happy.type] = {
    maybeMigrationKongId match {
      case Some(migrationKongId) =>
        for {
          _ <- existingKong.updateConsumerUsername(consumerId, tier)
          _ <- newKong.updateConsumerUsername(migrationKongId, tier)
        } yield Happy

      case None => existingKong.updateConsumerUsername(consumerId, tier)
    }
  }

  def updateConsumer(consumerId: String, maybeMigrationKongId: Option[String], newRateLimit: RateLimits): Future[Happy.type] = {
    maybeMigrationKongId match {
      case Some(migrationKongId) =>
        for {
          _ <- existingKong.updateConsumer(consumerId, newRateLimit)
          _ <- newKong.updateConsumer(migrationKongId, newRateLimit)
        } yield Happy

      case None => existingKong.updateConsumer(consumerId, newRateLimit)
    }
  }

  def deleteKey(consumerId: String, maybeMigrationKongId: Option[String]): Future[Happy.type] = {
    maybeMigrationKongId match {
      case Some(migrationKongId) =>
        for {
          _ <- existingKong.deleteKey(consumerId)
          _ <- newKong.deleteKey(migrationKongId)
        } yield Happy

      case None => existingKong.deleteKey(consumerId)
    }
  }
}
