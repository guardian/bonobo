package controllers

import models.BonoboKeys
import play.api.mvc._
import store.Dynamo

class Application(dynamo: Dynamo) extends Controller {

  def index = Action {
    Ok(views.html.index("Yo yo yo, your new application is ready."))
  }

  def createKey = Action {
    val testObj = new BonoboKeys("key", "1", "2", "3", "4", "5", 6, 7, "8", "9")
    dynamo.save(testObj)
    Ok(views.html.createKey("The new key has been saved to DynamoDB", testObj))
  }

  def showKeys = Action {
    val keys: List[BonoboKeys] = dynamo.getAllKeys()
    Ok(views.html.showKeys(keys))
  }

}
