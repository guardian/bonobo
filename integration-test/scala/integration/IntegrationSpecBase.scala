package integration

import java.io.File

import com.amazonaws.services.dynamodbv2.document.DynamoDB
import email.{MailClient, AwsEmailClient}
import models.BonoboUser
import store.Dynamo
import kong.KongClient
import components._
import integration.fixtures._
import org.scalatest.Suite
import org.scalatestplus.play.OneAppPerSuite
import play.api.ApplicationLoader.Context
import play.api.libs.ws.ning.NingWSComponents
import play.api._

/**
 * Base trait for integration tests.
 * Builds a Play app that integrates with real DynamoDB tables and a real Kong instance (running in Docker).
 *
 * The Dynamo tables and Kong instance are created (i.e. empty) before the first test in the file,
 * and destroyed after the last test in the file has run.
 */
class FakeEmailClient extends MailClient {
  def sendEmailCommercialRequest(user: BonoboUser): Unit = println("Not sending emails for commercial request")

  def sendEmailNewKey(toEmail: String, key: String): Unit = println("Not sending emails for new key added")
}

trait IntegrationSpecBase
    extends BonoboKeysTableFixture
    with BonoboUserTableFixture
    with DynamoDbClientFixture
    with DynamoDbLocalServerFixture
    with KongFixture
    with OneAppPerSuite { self: Suite =>

  val dynamo = new Dynamo(new DynamoDB(dynamoClient), usersTableName, keysTableName)

  trait FakeDynamoComponent extends DynamoComponent {
    val dynamo = self.dynamo
  }
  trait FakeKongComponent extends KongComponent { self: NingWSComponents =>
    val kong = new KongClient(wsClient, kongUrl, kongApiName)
  }
  trait FakeAwsEmailComponent extends AwsEmailComponent {
    val awsEmail = new FakeEmailClient()
  }
  class TestComponents(context: Context)
      extends BuiltInComponentsFromContext(context)
      with NingWSComponents
      with GoogleAuthComponent
      with FakeDynamoComponent
      with FakeKongComponent
      with FakeAwsEmailComponent
      with ControllersComponent {
    def enableAuth = false
  }

  val context = ApplicationLoader.createContext(
    new Environment(new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
  )
  val components = new TestComponents(context)
  val wsClient = components.wsClient

  override implicit lazy val app = components.application

}
