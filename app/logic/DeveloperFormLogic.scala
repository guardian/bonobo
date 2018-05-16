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

  def deleteUser(user: BonoboUser): Future[_] =
    Future { dynamo.getKeysWithUserId(user.bonoboId) } flatMap {
      Future.traverse(_) { key =>
        for {
          _ <- kong.deleteConsumer(key.kongConsumerId).recover {
            case Kong.GenericFailure(txt) if txt.contains("404") => ()
          }
        } yield {
          dynamo.deleteKey(key)
        }
      }.map { _ =>
        dynamo.deleteUser(user)
      }
    }

  def extendUser(user: BonoboUser): Future[_] = {
    val now = Some(DateTime.now.getMillis)
    val newUser = user.copy(additionalInfo = user.additionalInfo.copy(extendedAt = now, remindedAt = None))
    Future {
      dynamo.saveUser(newUser)
    }
  }
}
