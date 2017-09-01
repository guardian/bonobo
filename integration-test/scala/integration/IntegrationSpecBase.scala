package integration

import java.io.File

import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.simpleemail.model.SendEmailResult
import email.MailClient
import models.{BonoboUser, LabelProperties}
import play.api.libs.json.{JsNumber, JsString}
import play.api.mvc.RequestHeader
import store.Dynamo
import kong.KongClient
import components._
import integration.fixtures._
import org.scalatest.TestSuite
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.ApplicationLoader.Context
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.{ ApplicationLoader, BuiltInComponentsFromContext, Environment, NoHttpFiltersComponents, Mode }
import controllers.AssetsComponents

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Base trait for integration tests.
 * Builds a Play app that integrates with real DynamoDB tables and a real Kong instance (running in Docker).
 *
 * The Dynamo tables and Kong instance are created (i.e. empty) before the first test in the file,
 * and destroyed after the last test in the file has run.
 */
class FakeEmailClient extends MailClient {
  def sendEmailCommercialRequestToModerators(user: BonoboUser, productName: String, productUrl: String)(implicit request: RequestHeader): Future[SendEmailResult] = Future.failed(new Exception("Error when sending emails for commercial request to the moderators"))

  def sendEmailCommercialRequestToUser(toEmail: String): Future[SendEmailResult] = Future.failed(new Exception("Error when sending emails for commercial request to user"))

  def sendEmailNewKey(toEmail: String, key: String): Future[SendEmailResult] = Future.failed(new Exception("Error when sending emails for new key"))
}

trait IntegrationSpecBase
    extends BonoboKeysTableFixture
    with BonoboUserTableFixture
    with BonoboLabelsTableFixture
    with DynamoDbClientFixture
    with DynamoDbLocalServerFixture
    with KongFixture
    with OneAppPerSuiteWithComponents { self: TestSuite =>

  val dynamo = new Dynamo(new DynamoDB(dynamoClient), usersTableName, keysTableName, labelsTableName)

  trait FakeDynamoComponent extends DynamoComponent {
    val dynamo = self.dynamo
  }
  trait FakeKongComponent extends KongComponent { self: AhcWSComponents =>
    val kong = new KongClient(wsClient, kongUrl, kongApiName)
  }
  trait FakeAwsEmailComponent extends AwsEmailComponent {
    val awsEmail = new FakeEmailClient()
  }
  trait FakeLabelsComponent extends LabelsComponent {
    val labelsMap = Map("id-label-1" -> LabelProperties("label-1", "#121212"),
      "id-label-2" -> LabelProperties("label-2", "#123123"),
      "id-label-3" -> LabelProperties("label-3", "#123412"))
  }
  
  class TestComponents(context: Context)
      extends BuiltInComponentsFromContext(context)
      with AhcWSComponents
      with GoogleAuthComponent
      with AuthorisationComponent
      with FakeDynamoComponent
      with FakeKongComponent
      with FakeAwsEmailComponent
      with FakeLabelsComponent
      with ControllersComponent
      with NoHttpFiltersComponents
      with AssetsComponents

  override lazy val context = ApplicationLoader.createContext(
    new Environment(new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
  )
  
  override def components = new TestComponents(context)

  val wsClient = components.wsClient

  override implicit lazy val app = components.application

  def checkConsumerExistsOnKong(consumerId: String): Future[Boolean] = {
    wsClient.url(s"$kongUrl/consumers/$consumerId").get().map {
      response =>
        (response.json \\ "id").headOption match {
          case Some(JsString(id)) if id == consumerId => true
          case _ => false
        }
    }
  }

  def checkKeyExistsOnKong(consumerId: String): Future[Boolean] = {
    wsClient.url(s"$kongUrl/consumers/$consumerId/key-auth").get().map {
      response =>
        (response.json \\ "key").headOption match {
          case Some(JsString(key)) => true
          case _ => false
        }
    }
  }

  def getKeyForConsumerId(consumerId: String): Future[String] = {
    wsClient.url(s"$kongUrl/consumers/$consumerId/key-auth").get().map {
      response =>
        (response.json \\ "key").headOption match {
          case Some(JsString(key)) => key
          case _ => fail()
        }
    }
  }

  def checkRateLimitsMatch(consumerId: String, minutes: Int, day: Int): Future[Boolean] = {
    wsClient.url(s"$kongUrl/apis/$kongApiName/plugins")
      .withQueryStringParameters(("consumer_id" -> consumerId)).get().map {
      response =>
        val maybeDay = (response.json \\ "day").headOption
        val maybeMinutes = (response.json \\ "minute").headOption

        (maybeDay, maybeMinutes) match {
          case (Some(JsNumber(day)), Some(JsNumber(minute)))  if day.toInt == day && minute.toInt == minutes => true
          case _ => false
        }
    }
  }
}
