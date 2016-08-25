package kong

import kong.Kong.Happy
import models.{ ConsumerCreationResult, RateLimits, Tier }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class KongKeyWrapper(key: String, migrationKey: String)
case class ConsumerCreationResultWrapper(consumerCR: ConsumerCreationResult, migrationConsumerCR: ConsumerCreationResult)

case class KongWrapper(existingKong: Kong, newKong: Kong) {

  def createConsumerAndKey(tier: Tier, rateLimit: RateLimits, key: Option[String]): Future[ConsumerCreationResultWrapper] = {
    for {
      cr1 <- existingKong.createConsumerAndKey(tier, rateLimit, key)
      cr2 <- newKong.createConsumerAndKey(tier, rateLimit, key)
    } yield {
      ConsumerCreationResultWrapper(cr1, cr2)
    }
  }

  def createKey(consumerId: String, customKey: Option[String] = None): Future[KongKeyWrapper] = {
    for {
      key <- existingKong.createKey(consumerId, customKey)
      migrationKey <- newKong.createKey(consumerId, customKey)
    } yield {
      KongKeyWrapper(key, migrationKey)
    }
  }

  def updateConsumerUsername(consumerId: String, tier: Tier): Future[Happy.type] = {
    for {
      _ <- existingKong.updateConsumerUsername(consumerId, tier)
      _ <- newKong.updateConsumerUsername(consumerId, tier)
    } yield Happy
  }

  def updateConsumer(consumerId: String, newRateLimit: RateLimits): Future[Happy.type] = {
    for {
      _ <- existingKong.updateConsumer(consumerId, newRateLimit)
      _ <- newKong.updateConsumer(consumerId, newRateLimit)
    } yield Happy
  }

  def deleteKey(consumerId: String): Future[Happy.type] = {
    for {
      _ <- existingKong.deleteKey(consumerId)
      _ <- newKong.deleteKey(consumerId)
    } yield Happy
  }
}
