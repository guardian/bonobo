package controllers

import models.{BonoboKeys, ConsumerInput}
import play.api.libs.json._
import play.api.mvc._
import store.Dynamo
import scalaj.http._


class Application(dynamo: Dynamo) extends Controller {

  def index = Action {
    Ok(views.html.index("Yo yo yo, your new application is ready."))
  }

  /* creates a new user and stores information on dynamoDB */
  def createKey = Action {

    val key = "test"
    val email = "batman-NEWWWWW@gothamcity.com"
    val name = "Bruce"
    val surname = "Wayne"
    val company = "Wayne Enterprises"
    val url = "http://www.batman.com"
    var requests_per_day = 100
    var requests_per_minute = 10
    var tier = "superhero"
    var status = "active"

    // try to add a new username
    val request: HttpResponse[String] = Http("http://52.18.126.249:8001/consumers").postForm(Seq("username" -> email)).asString

    val json_request: JsValue = Json.parse(request.body)

    println(request.headers.get("Status"))
    println(json_request)

    // create the consumer object from json
    val consumer = json_request.as[ConsumerInput]

    def matchResponse(x: String): Result = x match {
      case "HTTP/1.1 201 Created" => createNewUser()
      case "HTTP/1.1 409 Conflict" => displayConflictError()
      case _ => displayGenericError()
    }

    def createNewUser(): Result = {
      val new_entry = new BonoboKeys(consumer.id.get, key, email, name, surname, company, url, requests_per_day,
        requests_per_minute, tier, status )
      dynamo.save(new_entry)

      Ok(views.html.createKey("A new object has been saved to DynamoDB"))
    }

    def displayConflictError(): Result = {
      Ok(views.html.createKey("Username already taken"))
    }

    def displayGenericError(): Result = {
      Ok(views.html.createKey("Something horrible happened"))
    }

    matchResponse(request.headers.get("Status").get)
  }


  def showKeys = Action {
    val keys: List[BonoboKeys] = dynamo.getAllKeys()
    Ok(views.html.showKeys(keys))
  }
}
