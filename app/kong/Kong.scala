package kong

import java.util.UUID

import models._
import org.joda.time.DateTime

import play.api.libs.json._
import play.api.libs.ws._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Kong {

  case object Happy
  case class ConflictFailure(message: String) extends RuntimeException(message, null, true, false)
  case class GenericFailure(message: String) extends RuntimeException(message, null, true, false)

  /* model used to parse json after create consumer */
  case class KongCreateConsumerResponse(id: String, created_at: Long)

  object KongCreateConsumerResponse {
    implicit val consumerRead = Json.reads[KongCreateConsumerResponse]
  }

  /* These are used to extract the key.id from the json response of kong.getKeyIdForGivenConsumer(),
     which looks like this: { "data" : [ { "id": "<value>", ... }, ... ] }
   */

  case class KongListConsumerKeysResponse(data: List[KongKeyResponse])

  case class KongKeyResponse(id: String)

  object KongKeyResponse {
    implicit val keyRead = Json.reads[KongKeyResponse]
  }

  object KongListConsumerKeysResponse {
    implicit val keyRead = Json.reads[KongListConsumerKeysResponse]
  }

  case class KongPluginConfig(id: String)

  object KongPluginConfig {
    implicit val pluginsRead = Json.reads[KongPluginConfig]
  }
}

trait Kong {
  import Kong._

  def createConsumerAndKey(tier: Tier, rateLimit: RateLimits, key: Option[String]): Future[ConsumerCreationResult]

  def updateConsumer(id: String, newRateLimit: RateLimits): Future[Happy.type]

  def updateConsumerUsername(id: String, tier: Tier): Future[Happy.type]

  def createKey(consumerId: String, customKey: Option[String] = None): Future[String]

  def deleteKey(consumerId: String): Future[Happy.type]

  def deleteKeyById(consumerId: String, keyId: String): Future[Happy.type]
}

class KongClient(ws: WSClient, serverUrl: String, apiName: String) extends Kong {

  import Kong._

  val RateLimitingPluginName = "rate-limiting"
  val KeyAuthPluginName = "key-auth"

  def createConsumerAndKey(tier: Tier, rateLimit: RateLimits, key: Option[String]): Future[ConsumerCreationResult] = {
    for {
      consumer <- createConsumer(tier)
      _ <- setRateLimit(consumer.id, rateLimit)
      key <- createKey(consumer.id, key)
    } yield ConsumerCreationResult(consumer.id, new DateTime(consumer.created_at), key)
  }

  private def createConsumer(tier: Tier): Future[KongCreateConsumerResponse] = {
    val username = s"${UUID.randomUUID}:${tier.conciergeName}"
    ws.url(s"$serverUrl/consumers").post(Map("username" -> Seq(username))).flatMap {
      response =>
        response.status match {
          case 201 => response.json.validate[KongCreateConsumerResponse] match {
            case JsSuccess(json, _) => success(s"Kong: The consumer has been successfully created with the id ${json.id}", json)
            case JsError(consumerError) => genericFail(consumerError.toString())
          }
          case 409 => conflictFail(s"Kong: Consumer with username $username already exists")
          case other =>
            genericFail(s"Kong responded with status $other - ${response.body} when attempting to create a new consumer")
        }
    }
  }

  private def setRateLimit(consumerId: String, rateLimit: RateLimits): Future[Unit] = {
    ws.url(s"$serverUrl/apis/$apiName/plugins").post(Map(
      "consumer_id" -> Seq(consumerId),
      "name" -> Seq(RateLimitingPluginName),
      "config.minute" -> Seq(rateLimit.requestsPerMinute.toString),
      "config.day" -> Seq(rateLimit.requestsPerDay.toString))).flatMap {
      response =>
        response.status match {
          case 201 => success(s"Kong: The rate limits for consumer with id $consumerId have been set successfully", ())
          case 409 => conflictFail(response.body)
          case other =>
            genericFail(s"Kong responded with status $other - ${response.body} when trying to set the rate limit for consumer $consumerId")
        }
    }
  }

  def createKey(consumerId: String, customKey: Option[String] = None): Future[String] = {
    val key: String = customKey getOrElse java.util.UUID.randomUUID.toString
    ws.url(s"$serverUrl/consumers/$consumerId/$KeyAuthPluginName").post(Map(
      "key" -> Seq(key))).flatMap {
      response =>
        response.status match {
          case 201 => success(s"Kong: Success when creating the new key $key", key)
          case 409 => conflictFail(s"Kong: Key $key already taken - try using a different value")
          case other =>
            genericFail(s"Kong responded with status $other - ${response.body} when trying to create a key for consumer $consumerId")
        }
    }
  }

  private def getPluginId(consumerId: String): Future[String] = {
    ws.url(s"$serverUrl/apis/$apiName/plugins")
      .withQueryStringParameters(("consumer_id" -> s"$consumerId"), ("name" -> RateLimitingPluginName))
      .get().flatMap {
        response =>
          (response.json \\ "id").headOption match {
            case Some(JsString(id)) => success(s"Kong: the id of the $RateLimitingPluginName plugin has been found successfully: $id", id)
            case _ => genericFail(s"Kong: Failed to parse json when getting the $RateLimitingPluginName plugin. Response: ${response.json}")
          }
      }
  }

  def updateConsumer(consumerId: String, newRateLimit: RateLimits): Future[Happy.type] = {
    getPluginId(consumerId) flatMap {
      pluginId =>
        ws.url(s"$serverUrl/apis/$apiName/plugins/$pluginId").patch(Map(
          "consumer_id" -> Seq(consumerId),
          "name" -> Seq(RateLimitingPluginName),
          "config.minute" -> Seq(newRateLimit.requestsPerMinute.toString),
          "config.day" -> Seq(newRateLimit.requestsPerDay.toString))).flatMap {
          response =>
            response.status match {
              case 200 => success(s"Kong: The rate limits for the consumer with id $consumerId have been updated successfully", Happy)
              case 409 => conflictFail(s"Kong: Conflict failure when trying to set rate limits for user with id $consumerId. Response: ${response.json}")
              case other =>
                genericFail(s"Kong responded with status $other - ${response.body} when trying to update the rate limit for consumer $consumerId")
            }
        }
    }
  }

  def updateConsumerUsername(consumerId: String, tier: Tier): Future[Happy.type] = {
    val username = s"${UUID.randomUUID}:${tier.conciergeName}"
    ws.url(s"$serverUrl/consumers/$consumerId").patch(Map(
      "username" -> Seq(username))).flatMap {
      response =>
        response.status match {
          case 200 => success(s"Kong: The username for the consumer with id $consumerId has been updated successfully", Happy)
          case 409 => conflictFail(s"Kong: Conflict failure when trying to update the username for user with id $consumerId. Response: ${response.json}")
          case other =>
            genericFail(s"Kong responded with status $other - ${response.body} when trying to update the username for consumer $consumerId")
        }
    }
  }

  def getKeyIdForGivenConsumer(consumerId: String): Future[String] = {
    ws.url(s"$serverUrl/consumers/$consumerId/$KeyAuthPluginName").get().flatMap {
      response =>
        response.json.validate[KongListConsumerKeysResponse] match {
          case JsSuccess(KongListConsumerKeysResponse(head :: tail), _) => success(s"Kong: The key id for consumer with id $consumerId has been found successfully", head.id)
          case JsSuccess(KongListConsumerKeysResponse(Nil), _) => genericFail(s"Kong: No key was found for consumer with id $consumerId")
          case JsError(consumerError) => genericFail(s"Kong: Failed to parse json when getting the key for consumer with id $consumerId. Response: ${consumerError.toString()}")
        }
    }
  }

  def deleteKey(consumerId: String): Future[Happy.type] = {
    getKeyIdForGivenConsumer(consumerId) flatMap {
      keyId =>
        ws.url(s"$serverUrl/consumers/$consumerId/$KeyAuthPluginName/$keyId").delete().flatMap {
          response =>
            response.status match {
              case 204 => success(s"Kong: The key with id $keyId has been deleted successfully", Happy)
              case other =>
                genericFail(s"Kong responded with status $other - ${response.body} when trying to delete the key $keyId for consumer $consumerId")
            }
        }
    }
  }

  private def genericFail[A](errorMsg: String): Future[A] = {
    Logger.warn(errorMsg)
    Future.failed[A](GenericFailure(errorMsg))
  }

  private def conflictFail[A](errorMsg: String): Future[A] = {
    Logger.warn(errorMsg)
    Future.failed[A](ConflictFailure(errorMsg))
  }

  private def success[A](successMsg: String, returnValue: A): Future[A] = {
    Logger.info(successMsg)
    Future.successful(returnValue)
  }
}