package controllers

import models.BonoboKeys
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import store.Dynamo


class Application(dynamo: Dynamo) extends Controller {

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
  def createKey = Action { implicit request =>

    val (key, email, name, surname, company, url, requests_per_day, requests_per_minute, tier, status) = form.bindFromRequest.get
//    val key = "test"
//    val email = "batman-NEWWWWW@gothamcity.com"
//    val name = "Bruce"
//    val surname = "Wayne"
//    val company = "Wayne Enterprises"
//    val url = "http://www.batman.com"
//    val requests_per_day = 100
//    val requests_per_minute = 10
//    val tier = "superhero"
//    val status = "active"

    // try to add a new username
    //val response = Http("http://52.18.126.249:8001/consumers").postForm(Seq("username" -> email.toString)).asString
    //val json_request: JsValue = Json.parse(response.body)

    // create the consumer object from json
    //val consumer = json_request.as[ConsumerInput]

    def matchResponse(x: String): Result = x match {
      case "HTTP/1.1 201 Created" => createNewUser()
      case "HTTP/1.1 409 Conflict" => displayConflictError()
      case _ => displayGenericError()
    }

    def createNewUser(): Result = {
      //val newEntry = new BonoboKeys(consumer.id.get, key.toString, email.toString, name.toString, surname.toString, company, url, requests_per_day, requests_per_minute, tier, status )
      //dynamo.save(newEntry)
      Ok(views.html.createKey("A new object has been saved to DynamoDB"))
    }

    def displayConflictError(): Result = {
      Ok(views.html.createKey("Email already taken"))
    }

    def displayGenericError(): Result = {
      Ok(views.html.createKey("Something horrible happened"))
    }

    //matchResponse(request.headers.get("Status").get)

    def printFormContent(): String = {
      key + ", " + email + ", " + name + ", " + surname + ", " + company + ", " + url + ", " + requests_per_day + ", " + requests_per_minute + ", " + tier + ", " + status
    }

    Ok(views.html.createKey(printFormContent()))
  }


  def showKeys = Action {
    val keys: List[BonoboKeys] = dynamo.getAllKeys()
    Ok(views.html.showKeys(keys))
  }
}
