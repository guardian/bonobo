package integration

import com.amazonaws.services.dynamodbv2.document.DynamoDB
import controllers.Application
import integration.fixtures.{BonoboKeysTableFixture, BonoboUserTableFixture, DynamoDbFixture}
import kong.Kong
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{Result}
import play.api.test.FakeRequest
import play.api.{Configuration, Environment}
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import store.Dynamo
import scala.concurrent.Future


class CreateUserSpec extends FlatSpec
  with Matchers
  with DynamoDbFixture
  with BonoboKeysTableFixture
  with BonoboUserTableFixture
  with MockitoSugar {

  val mockKong = mock[Kong]
  val messagesApi = new DefaultMessagesApi(Environment.simple(), Configuration.reference, new DefaultLangs(Configuration.reference))
  val dynamo = new Dynamo(new DynamoDB(client), usersTableName, keysTableName)
  val application = new Application(dynamo, mockKong, messagesApi, null, false)

  behavior of "creating a new user"

    it should "" in {
      val createUserForm = Map(
        "email" -> "test@thetestcompany.com",
        "name" -> "Joe Bloggs",
        "company" -> "The Test Company",
        "url" -> "http://thetestcompany.co.uk",
        "tier" -> "developer",
        "key" -> "123124-13434-32323-3439"
      )

      val result: Future[Result] = application.createUser.apply(FakeRequest().withFormUrlEncodedBody(createUserForm.toSeq: _*))
      /* Test outcome from controller, and then by side effect, expectations of DB. */

    }

}


