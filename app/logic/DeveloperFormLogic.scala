package logic

import controllers.Forms.DeveloperCreateKeyFormData
import kong.Kong
import kong.Kong.ConflictFailure
import models._
import org.joda.time.DateTime
import play.api.Logger
import store.DB

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeveloperFormLogic(dynamo: DB, kong: Kong) {

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
      val newBonoboUser = BonoboUser(consumer.kongConsumerId, formData)
      dynamo.saveUser(newBonoboUser)

      val newKongKey = KongKey(consumer.kongConsumerId, consumer, Tier.Developer.rateLimit, Tier.Developer, formData.productName, formData.productUrl)
      dynamo.saveKey(newKongKey, List.empty)
    }

    if (dynamo.isEmailInUse(form.email))
      Future.failed(ConflictFailure("Email already taken."))
    else {
      kong.createConsumerAndKey(Tier.Developer, Tier.Developer.rateLimit, key = None) map {
        consumer =>
          saveUserAndKeyOnDB(consumer, form)
          consumer.key
      }
    }
  }

  def deleteKeys(id: String): Future[Unit] = {
    dynamo.getUserWithId(id).fold(userNotFound(id): Future[Unit]) { user =>
      val keys = dynamo.getKeysWithUserId(id)
      Future.traverse(keys) { key =>
        for {
          _ <- kong.deleteKey(key.kongConsumerId)
          _ <- kong.deleteConsumer(key.kongConsumerId)
        } yield {
          dynamo.deleteKey(key)
        }
      }.map(_ => ())
    }
  }

  def extendKeys(id: String): Future[Unit] = {
    dynamo.getUserWithId(id).fold(userNotFound(id): Future[Unit]) { user =>
      val keys = dynamo.getKeysWithUserId(id)
      val now = Some(DateTime.now())
      Future.traverse(keys)(key => Future.successful(dynamo.updateKey(key.copy(extendedAt = now)))).map { _ => () }
    }
  }

  private def userNotFound(id: String) = Future.failed(new Throwable(s"User $id not found"))
}
