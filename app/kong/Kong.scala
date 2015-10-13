package kong

import models._

import play.api.libs.json._
import play.api.libs.ws._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Kong {
  case object KeyCreationFailed extends RuntimeException("KeyCreationFailed", null, true, false)
  case class ConflictFailure(message: String) extends Exception(message)
  case class GenericFailure(message: String) extends Exception(message)
}

trait Kong {
  def registerUser(username: String, rateLimit: RateLimits): Future[KongCreateConsumerResponse]
  def updateUser(id: String, newRateLimit: RateLimits): Future[Unit]
}

class KongClient(ws: WSClient, serverUrl: String, apiName: String) extends Kong {
  import Kong._

  def registerUser(username: String, rateLimit: RateLimits): Future[KongCreateConsumerResponse] = {

    for {
      consumer <- createConsumer(username)
      _ <- setRateLimit(consumer.id, rateLimit)
      _ <- createKey(consumer.id)
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
      "name" -> Seq("rate-limiting"),
      "config.minute" -> Seq(rateLimit.requestsPerMinute.toString),
      "config.day" -> Seq(rateLimit.requestsPerDay.toString))).flatMap {
      response =>
        response.status match {
          case 201 => Future.successful()
          case 409 => Future.failed(ConflictFailure(response.body))
          case other => Future.failed(GenericFailure(s"Kong responded with status $other when trying to set the rate limit" +
            s" for user $consumerId"))
        }
    }
  }

  private def createKey(consumerId: String): Future[Unit] = {
    // TODO: we're using the consumerId as a key here. Might want to change this down the line?
    ws.url(s"$serverUrl/consumers/$consumerId/keyauth").post(Map(
      "key" -> Seq(consumerId))).flatMap {
      response =>
        response.status match {
          case 201 => Future.successful()
          case _ => Future.failed(KeyCreationFailed)
        }
    }
  }

  private def getPluginId(consumerId: String): Future[String] = {
    ws.url(s"$serverUrl/apis/$apiName").get().map {
      response =>
        response.json.validate[List[KongPluginConfig]] match {
          case JsSuccess(json, _) => json.head.id
          case JsError(pluginIdError) => "something bad happened"
        }
    }
  }

  def updateUser(id: String, newRateLimit: RateLimits): Future[Unit] = {
    getPluginId(id) map {
      pluginId =>
        ws.url(s"$serverUrl/apis/$apiName/plugins/$pluginId").patch(Map(
          "consumer_id" -> Seq(id),
          "name" -> Seq("ratelimiting"),
          "value.minute" -> Seq(newRateLimit.requestsPerMinute.toString),
          "value.day" -> Seq(newRateLimit.requestsPerDay.toString))).flatMap {
          response =>
            response.status match {
              case 200 => Future.successful()
              case 409 => Future.failed(ConflictFailure(response.body))
              case other => Future.failed(GenericFailure(s"Kong responded with status $other when trying to set the rate limit" +
                s" for user $id; \n the sever said ${response.body} \n" +
                s"the request was $serverUrl/apis/$apiName/plugins/$pluginId"))
            }
        }
    }
  }
}
