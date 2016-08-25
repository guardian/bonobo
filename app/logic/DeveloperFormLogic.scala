package logic

import controllers.Forms.DeveloperCreateKeyFormData
import kong.KongWrapper
import kong.Kong.ConflictFailure
import models._
import play.api.Logger
import store.DB

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeveloperFormLogic(dynamo: DB, kong: KongWrapper) {

  /**
   * Creates a consumer and key on Kong and a Bonobo user,
   * and saves the user and key in Dynamo.
   * The key will be randomly generated, the tier is Developer
   * and the default rate limits are being used.
   *
   * @return a Future of the newly created Kong consumer's key
   */

  def createUser(form: DeveloperCreateKeyFormData): Future[String] = {
    def saveUserAndKeyOnDB(consumer: ConsumerCreationResult, formData: DeveloperCreateKeyFormData): Unit = {
      Logger.info(s"OpenFormLogic: Creating user with name ${form.name}")
      val newBonoboUser = BonoboUser(consumer.id, formData)
      dynamo.saveUser(newBonoboUser)

      val newKongKey = KongKey(consumer.id, consumer, None, Tier.Developer.rateLimit, Tier.Developer, formData.productName, formData.productUrl)
      dynamo.saveKey(newKongKey, List.empty)
    }

    if (dynamo.isEmailInUse(form.email))
      Future.failed(ConflictFailure("Email already taken."))
    else {
      kong.existingKong.createConsumerAndKey(Tier.Developer, Tier.Developer.rateLimit, key = None) map {
        consumer =>
          saveUserAndKeyOnDB(consumer, form)
          consumer.key
      }
    }
  }
}
