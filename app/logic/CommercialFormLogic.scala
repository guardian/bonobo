package logic

import controllers.Forms.CommercialRequestKeyFormData
import kong.Kong
import kong.Kong.ConflictFailure
import models.{ Developer, KongKey, BonoboUser, ConsumerCreationResult }
import play.api.Logger
import store.DB

import scala.concurrent.Future

class CommercialFormLogic(dynamo: DB, kong: Kong) {

  /**
   * Checks if a user with the same email already exists,
   * and if not saves the user in Dynamo.
   *
   * @return Unit in case of success or a message in case of error.
   */

  def sendRequest(form: CommercialRequestKeyFormData): Either[String, Unit] = {
    def saveUserOnDB(formData: CommercialRequestKeyFormData): Unit = {
      Logger.info(s"CommercialFormLogic: User with email ${form.email} has sent a new request.")
      val newBonoboUser = BonoboUser(formData)
      dynamo.saveUser(newBonoboUser)
    }

    dynamo.getUserWithEmail(form.email) match {
      case Some(a) => Left("Email already taken.")
      case None => Right(saveUserOnDB(form))
    }
  }
}