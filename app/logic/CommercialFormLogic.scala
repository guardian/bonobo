package logic

import controllers.Forms.CommercialRequestKeyFormData
import kong.Kong
import models.BonoboUser
import play.api.Logger
import store.DB

class CommercialFormLogic(dynamo: DB, kong: Kong) {

  /**
   * Checks if a user with the same email already exists,
   * and if not saves the user in Dynamo.
   *
   * @return Unit in case of success or a message in case of error.
   */

  def sendRequest(form: CommercialRequestKeyFormData): Either[String, BonoboUser] = {
    def saveUserOnDB(formData: CommercialRequestKeyFormData): BonoboUser = {
      Logger.info(s"CommercialFormLogic: User with email ${form.email} has sent a new request.")
      val newBonoboUser = BonoboUser(formData)
      dynamo.saveUser(newBonoboUser)
      newBonoboUser
    }

    dynamo.getUserWithEmail(form.email) match {
      case Some(a) => Left("Email already taken.")
      case None => Right(saveUserOnDB(form))
    }
  }
}