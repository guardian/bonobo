package controllers

import scala.concurrent.Future

import models.{ConsumerInput, BonoboKeys}
import play.api.data.Forms._
import play.api.libs.json._
import play.api.data._
import play.api.mvc._
import store.Dynamo
import play.api.libs.ws._
import scala.concurrent.ExecutionContext.Implicits.global


class Application(dynamo: Dynamo, ws: WSClient) extends Controller {

  def index = Action {
    Ok(views.html.index("Yo yo yo, your new application is ready."))
  }

  val form = Form(
    tuple(
      "key" -> text,
      "email" -> text,
      "name" -> text,
      "surname" -> text,
      "company" -> text,
      "url" -> text,
      "requests_per_day" -> number,
      "requests_per_minute" -> number,
      "tier" -> text,
      "status" -> text
    )
  )

  def createKeyForm = Action {
    Ok(views.html.createKey("Enter your details"))
  }

  /* creates a new user and stores information on dynamoDB */
  def createKey = Action.async { implicit request =>
    val (key, email, name, surname, company, url, requests_per_day, requests_per_minute, tier, status) = form.bindFromRequest.get

    // make the POST request
    val response: Future[WSResponse] = ws.url("http://52.18.126.249:8001/consumers").post(Map("username" -> Seq("value")))

    def createNewUser(consumer: ConsumerInput): Result = {
      val newEntry = new BonoboKeys(consumer.id, key.toString, email.toString, name.toString, surname.toString,
        company, url, requests_per_day, requests_per_minute, tier, status, consumer.created_at)
      dynamo.save(newEntry)

      Ok(views.html.createKey("A new object has been saved to DynamoDB"))
    }

    def displayConflictError(): Result = {
      Ok(views.html.createKey("Email already taken"))
    }

    def displayGenericError(): Result = {
      Ok(views.html.createKey("Something horrible happened"))
    }

    response.map {
      response =>
        response.status match {
          case 201 => response.json.validate[ConsumerInput] match {
            case JsSuccess(consumerInput, _) => createNewUser(consumerInput)
            case JsError(consumerError) => displayGenericError()
          }
          case 409 => displayConflictError()
          case _ => displayGenericError()
        }
    }
  }


  def showKeys = Action {
    val keys: List[BonoboKeys] = dynamo.getAllKeys()
    Ok(views.html.showKeys(keys))
  }
}
