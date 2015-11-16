package integration

import models.{BonoboUser, MasheryRegistration, AdditionalUserInfo, MasheryUser}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{OptionValues, Matchers, FlatSpec}
import play.api.Logger
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.io.Source
import scala.util.{Failure, Success}

class MigrationIntegrationTests extends FlatSpec with Matchers with OptionValues with IntegrationSpecBase with ScalaFutures with Eventually {

  private def checkConsumerExistsOnKong(consumerId: String): Future[Boolean] = {
    wsClient.url(s"$kongUrl/consumers/$consumerId").get().map {
      response =>
        (response.json \\ "id").headOption match {
          case Some(JsString(id)) if id == consumerId => true
          case _ => false
        }
    }
  }

  behavior of "migrate users from Mashery"

  it should "take a list of users from Mashery and save them in Kong and Dynamo" in {
    val masheryJson: JsValue = Json.parse(Source.fromFile("integration-test/resources/mashery.json").mkString)
    masheryJson should not be JsNull

    val result = route(FakeRequest(POST, "/migrate").withJsonBody(masheryJson)).get
    status(result) shouldBe 200

    result.onComplete{
      case Success(_) => {
        masheryJson.validate[List[MasheryUser]] match {
          case JsSuccess(j, _) => j.foreach(checkSave)
          case JsError(errorMessage) => Logger.warn(s"Integration Tests: Error parsing json $errorMessage")
        }
      }
      case Failure(_) => Logger.warn(s"Integration Tests: Error completing request")
    }
  }

  private def checkSave(user: MasheryUser): Unit = {
    val bonoboUser = dynamo.getUserWithEmail(user.email)
    Logger.info(s"Integration Tests: bonoboUser = $bonoboUser")

    val bonoboId: Option[String] = bonoboUser match {
      case Some(u) => {
        Logger.info(s"Integration Tests: bonoboId = ${Some(u.bonoboId)}")
        Some(u.bonoboId)
      }
      case None => None
    }
    bonoboId shouldBe defined

    val additionalInfo = AdditionalUserInfo(user.createdAt, MasheryRegistration)
    val expectedUser = Option(BonoboUser("random_id", user.email, user.name, user.productName, user.productUrl, user.companyName, user.companyUrl, additionalInfo).copy(bonoboId = bonoboId.value))
    bonoboUser shouldBe expectedUser

    val kongKeys = dynamo.getKeysWithUserId(bonoboUser.value.bonoboId)
    kongKeys foreach (key => Await.result(checkConsumerExistsOnKong(key.kongId), atMost = 10.seconds) shouldBe true)
  }
}
