package integration

import controllers.Migration
import models._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{OptionValues, Matchers, FlatSpec}
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.io.Source

class MigrationIntegrationTests extends FlatSpec with Matchers with OptionValues with IntegrationSpecBase with ScalaFutures with Eventually {

  behavior of "migrate users from Mashery"

  it should "take a list of users from Mashery and save them in Kong and Dynamo" in {
    val initialResult = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "test@thetestcompany.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "tier" -> "RightsManaged",
      "key" -> "second-key-2"
    )).get

    status(initialResult) shouldBe 303 // on success it redirects to the "edit user" page

    val masheryJson = Json.parse(Source.fromFile("integration-test/resources/mashery.json").mkString)
    masheryJson should not be JsNull

    val result = route(FakeRequest(POST, "/migrate").withJsonBody(masheryJson)).get
    status(result) shouldBe 200

    contentAsJson(result).validate[MigrationResult] match {
      case JsSuccess(count, _) => {
        whenReady(result){ r =>
          masheryJson.validate[List[MasheryUser]] match {
            case JsSuccess(masheryUsers, _) => masheryUsers.foreach(user => if(!count.userConflicts.contains(EmailConflict(user.email))) checkIfSaved(user))
            case JsError(errorMessage) => fail(s"Integration Tests: Error parsing json $errorMessage")
          }
        }
      }
      case JsError(errorMessage) => fail(s"Integration Tests: Error parsing json $errorMessage")
    }

  }

  private def checkIfSaved(masheryUser: MasheryUser): Unit = {
    val bonoboUser = dynamo.getUserWithEmail(masheryUser.email)
    val bonoboId: Option[String] = bonoboUser match {
      case Some(u) => Some(u.bonoboId)
      case None => None
    }
    bonoboId shouldBe defined

    val additionalInfo = AdditionalUserInfo(masheryUser.createdAt, MasheryRegistration)
    val expectedUser = Option(BonoboUser("random_id", masheryUser.email, masheryUser.name, Migration.unspecifiedIfEmpty(masheryUser.companyName), Migration.unspecifiedIfEmpty(masheryUser.companyUrl), additionalInfo).copy(bonoboId = bonoboId.value))
    bonoboUser shouldBe expectedUser

    val expectedKeys = masheryUser.keys.map(key => KongKey(bonoboId.value, "random", key.key, key.requestsPerDay, key.requestsPerMinute, key.tier, key.status, key.createdAt, key.productName, key.productUrl, "range"))
    val kongKeys = dynamo.getKeysWithUserId(bonoboId.value)
    kongKeys foreach (key => key shouldBe expectedKeys.find(k => k.key == key.key).value.copy(kongId = key.kongId, rangeKey = key.rangeKey))
    kongKeys foreach (key => Await.result(checkConsumerExistsOnKong(key.kongId), atMost = 10.seconds) shouldBe true)
    kongKeys foreach (key => Await.result(checkKeyExistsOnKong(key.kongId), atMost = 10.seconds) shouldBe key.status == KongKey.Active)
  }
}
