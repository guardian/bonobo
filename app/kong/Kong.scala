package kong

import models._

import play.api.libs.json._
import play.api.libs.ws._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Kong {

  case object KeyCreationFailed extends RuntimeException("KeyCreationFailed", null, true, false)

  case object Happy

  case class ConflictFailure(message: String) extends Exception(message)

  case class GenericFailure(message: String) extends Exception(message)

}

trait Kong {
  import Kong._

  def registerUser(username: String, rateLimit: RateLimits): Future[KongCreateConsumerResponse]

  def updateUser(id: String, newRateLimit: RateLimits): Future[Happy.type]

  def createKey(consumerId: String): Future[Happy.type]

  def deleteKey(userId: String): Future[Happy.type]
}

class KongClient(ws: WSClient, serverUrl: String, apiName: String) extends Kong {

  import Kong._

  def registerUser(username: String, rateLimit: RateLimits): Future[KongCreateConsumerResponse] = {

    for {
      consumer <- createConsumer(username)
      _ <- setRateLimit(consumer.id, rateLimit)
      _ <- createKey(consumer.id) // TODO: this will not do if I want to set a custom key
    } yield consumer
  }

  private def createConsumer(username: String): Future[KongCreateConsumerResponse] = {
    ws.url(s"$serverUrl/consumers").post(Map("username" -> Seq(username))).flatMap {
      response =>
        response.status match {
          case 201 => response.json.validate[KongCreateConsumerResponse] match {
            case JsSuccess(json, _) => Future.successful(json)
            case JsError(consumerError) => Future.failed(GenericFailure(consumerError.toString()))
          }
          case 409 => Future.failed(ConflictFailure(response.toString))
          case other => Future.failed(GenericFailure(s"Kong responded with status $other when trying to add a new consumer"))
        }
    }
  }

  private def setRateLimit(consumerId: String, rateLimit: RateLimits): Future[Unit] = {
    ws.url(s"$serverUrl/apis/$apiName/plugins").post(Map(
      "consumer_id" -> Seq(consumerId),
      "name" -> Seq("ratelimiting"),
      "value.minute" -> Seq(rateLimit.requestsPerMinute.toString),
      "value.day" -> Seq(rateLimit.requestsPerDay.toString))).flatMap {
      response =>
        response.status match {
          case 201 => Future.successful()
          case 409 => Future.failed(ConflictFailure(response.body))
          case other => Future.failed(GenericFailure(s"Kong responded with status $other when trying to set the rate limit" +
            s" for user $consumerId"))
        }
    }
  }

  def createKey(consumerId: String): Future[Happy.type] = {
    // TODO: we're using the consumerId as a key here. Might want to change this down the line?
    ws.url(s"$serverUrl/consumers/$consumerId/keyauth").post(Map(
      "key" -> Seq(consumerId))).flatMap {
      response =>
        response.status match {
          case 201 => Future.successful(Happy)
          case _ => Future.failed(KeyCreationFailed)
        }
    }
  }

  private def getPluginId(consumerId: String): Future[String] = {
    Logger.info("calling get plugin id")
    ws.url(s"$serverUrl/apis/$apiName/plugins")
      .withQueryString("consumer_id" -> s"$consumerId")
      .withQueryString("name" -> "ratelimiting").get().flatMap {
        response =>
          (response.json \\ "id").headOption match {
            case Some(JsString(id)) => Future.successful(id)
            case _ => Future.failed(GenericFailure("failed to parse json")) // TODO return useful stuff
          }
      }
  }

  def updateUser(id: String, newRateLimit: RateLimits): Future[Happy.type] = {
    getPluginId(id) flatMap {
      pluginId =>
        Logger.info(s"UPDATE USER request is $serverUrl/apis/$apiName/plugins/$pluginId")
        ws.url(s"$serverUrl/apis/$apiName/plugins/$pluginId").patch(Map(
          "consumer_id" -> Seq(id),
          "name" -> Seq("ratelimiting"),
          "value.minute" -> Seq(newRateLimit.requestsPerMinute.toString),
          "value.day" -> Seq(newRateLimit.requestsPerDay.toString))).flatMap {
          response =>
            Logger.info(s"${response.body}")
            response.status match {
              case 200 => Future.successful(Happy)
              case 409 => Future.failed(ConflictFailure(response.body))
              case other =>
                Future.failed(GenericFailure(s"Kong responded with status $other when trying to set the rate limit" +
                  s" for user $id; \n the sever said ${response.body} \n" +
                  s"the request was $serverUrl/apis/$apiName/plugins/$pluginId"))
            }
        }
    }
  }

  def getKeyIdForGivenUser(consumerId: String): Future[String] = {
    ws.url(s"$serverUrl/consumers/$consumerId/keyauth").get().flatMap {
      response =>
        response.json.validate[KongListConsumerKeysResponse] match {
          case JsSuccess(KongListConsumerKeysResponse(head :: tail), _) => Future.successful(head.id)
          case JsSuccess(KongListConsumerKeysResponse(Nil), _) => Future.failed(GenericFailure("No keys found"))
          case JsError(consumerError) => Future.failed(GenericFailure(consumerError.toString()))
        }
    }
  }

  def deleteKey(userId: String): Future[Happy.type] = {
    getKeyIdForGivenUser(userId) flatMap {
      keyId =>
        ws.url(s"$serverUrl/consumers/$userId/keyauth/$keyId").delete().flatMap {
          response =>
            response.status match {
              case 204 => Future.successful(Happy)
              case other => Future.failed(GenericFailure(s"Kong responded with status $other when trying to delete " +
                s"the key $keyId for user $userId; the server said ${
                  response.body
                }"))
            }
        }
    }
  }
}