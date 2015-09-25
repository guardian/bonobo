package kong

import models._

import play.api.libs.json._
import play.api.libs.ws._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Kong {

  sealed trait CreateKeyResult
  case class Succeeded(consumerInput: ConsumerInput) extends CreateKeyResult
  case object UsernameAlreadyTaken extends CreateKeyResult
  case object GenericError extends CreateKeyResult

}

trait Kong {
  import Kong._

  def createKey(username: String): Future[CreateKeyResult]

}

class KongClient(ws: WSClient) extends Kong {
  import Kong._

  def createKey(username: String): Future[CreateKeyResult] = {
    ws.url("http://52.18.126.249:8001/consumers").post(Map("username" -> Seq(username))).map {
      response =>
        response.status match {
          case 201 => response.json.validate[ConsumerInput] match {
            case JsSuccess(consumerInput, _) => Succeeded(consumerInput) // createNewUser(consumerInput, formData)
            case JsError(consumerError) => GenericError // displayGenericError()
          }
          case 409 => UsernameAlreadyTaken // displayConflictError()
          case _ => GenericError // displayGenericError()
        }
    }
  }

}
