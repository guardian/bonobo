package integration

import org.scalatest.concurrent.{ PatienceConfiguration, Eventually, ScalaFutures }
import org.scalatest.time._
import org.scalatest.{ Matchers, OptionValues, FlatSpec }
import play.api.test.Helpers._
import play.api.test.FakeRequest

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._

class CreateUserSpec extends FlatSpec with Matchers with OptionValues with IntegrationSpecBase with ScalaFutures with Eventually {

  /* HELPER FUNCTIONS */
  def waitForDyanmo[A](key: String)(f: => A): A = {
    eventually(PatienceConfiguration.Timeout(Span(9, Seconds)))(dynamo.retrieveKey(key) shouldBe defined)
    f
  }

  def checkConsumerExistsOnKong(consumerId: String): Future[Boolean] = {
    wsClient.url(s"$kongUrl/consumers/$consumerId").get().flatMap {
      response =>
        (response.json \\ "id").headOption match {
          case Some(JsString(id)) if id == consumerId => Future.successful(true)
          case _ => Future.successful(false)
        }
    }
  }

  def checkKeyExistsOnKong(consumerId: String): Future[Boolean] = {
    wsClient.url(s"$kongUrl/consumers/$consumerId/key-auth").get().flatMap {
      response =>
        (response.json \\ "key").headOption match {
          case Some(JsString(key)) => Future.successful(true)
          case _ => Future.successful(false)
        }
    }
  }

  def getKeyForConsumerId(consId: String): Future[String] = {
    wsClient.url(s"$kongUrl/consumers/$consId/key-auth").get().flatMap {
      response =>
        (response.json \\ "key").headOption match {
          case Some(JsString(key)) => Future.successful(key)
          case _ => fail()
        }
    }
  }

  /* ACTUAL TESTS */

  behavior of "creating a new user with a custom key"

  it should "add a Bonobo user and key to Dynamo" in {
    val result = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "test@thetestcompany.com",
      "name" -> "Joe Bloggs",
      "company" -> "The Test Company",
      "url" -> "http://thetestcompany.co.uk",
      "tier" -> "RightsManaged",
      "key" -> "123124-13434-32323-3439"
    )).get

    status(result) should be(SEE_OTHER) // on success it redirects to the "edit user" page

    val dynamoKongKey = dynamo.retrieveKey("123124-13434-32323-3439")
    val consumerId = dynamoKongKey.value.kongId

    // check the consumerId in dynamo matches the one on Kong
    Await.result(checkConsumerExistsOnKong(consumerId), atMost = 10.seconds) shouldBe true

    // check Kong's key value matches Bonobo-Keys.keyValue
    val keyValue = Await.result(getKeyForConsumerId(consumerId), atMost = 10.seconds)
    keyValue shouldBe dynamoKongKey.value.key

    // check Bonobo-Keys.bonoboId is the same as Bonobo-Keys.kongId; this is only true for the first key of every user we create
    dynamoKongKey.value.bonoboId shouldBe dynamoKongKey.value.kongId

    // check Bonobo-Users.id matches Bonobo-Keys.kongId
    val dynamoUser = dynamo.retrieveUser(consumerId)
    dynamoUser.value.bonoboId shouldBe dynamoKongKey.value.kongId
  }

  behavior of "creating a new user without specifying a custom key"

  it should "add a Bonobo user and a randomly generated key" in {
    val result = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "random@email.com",
      "name" -> "Some Dudes",
      "company" -> "The Guardian",
      "url" -> "http://thetestcompany132123123.co.uk",
      "tier" -> "RightsManaged",
      "key" -> ""
    )).get

    status(result) should be(SEE_OTHER) // on success it redirects to the "edit user" page

    val dynamoBonoboUser = dynamo.retrieveUserByEmail("random@email.com")
    val consumerId = dynamoBonoboUser.value.bonoboId

    // check the consumerId in dynamo matches the one on Kong
    Await.result(checkConsumerExistsOnKong(consumerId), atMost = 10.seconds) shouldBe true

    // check Kong's key value matches Bonobo-Keys.keyValue
    val keyValue = Await.result(getKeyForConsumerId(consumerId), atMost = 10.seconds)
    val dynamoKongKey = waitForDyanmo(keyValue)(dynamo.retrieveKey(keyValue))
    keyValue shouldBe dynamoKongKey.value.key

    // check Bonobo-Users.id matches Bonobo-Keys.kongId
    val dynamoUser = dynamo.retrieveUser(consumerId)
    dynamoUser.value.bonoboId shouldBe dynamoKongKey.value.kongId
  }

  behavior of "adding a second key to an existing user"

  it should "add a new keyfor the existing user" in {
    val result = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "bruce.wayne@wayneenterprises.com",
      "name" -> "Bruce Wayne",
      "company" -> "Wayne Enterprises",
      "url" -> "http://wayneenterprises.com.co.uk",
      "tier" -> "RightsManaged",
      "key" -> "the-dark-knight"
    )).get

    status(result) should be(SEE_OTHER) // on success it redirects to the "edit user" page

    val bonoboId = waitForDyanmo("the-dark-knight")(dynamo.retrieveKey("the-dark-knight").value.bonoboId)

    Thread.sleep(3000L) // this is needed because Dynamo doesn't like multiple writes one right after the other (...)
    val addKeyResult = route(FakeRequest(POST, s"/key/create/$bonoboId").withFormUrlEncodedBody(
      "tier" -> "RightsManaged",
      "key" -> "the-dark-day"
    )).get

    status(addKeyResult) should be(SEE_OTHER) // on success it redirects to the "edit user" page

    val firstKongId = waitForDyanmo("the-dark-knight")(dynamo.retrieveKey("the-dark-knight").value.kongId)
    val secondKongId = waitForDyanmo("the-dark-day")(dynamo.retrieveKey("the-dark-day").value.kongId)

    // check the consumerId in dynamo matches the one on Kong
    Await.result(checkConsumerExistsOnKong(secondKongId), atMost = 10.seconds) shouldBe true

    bonoboId shouldBe firstKongId
    bonoboId should not be secondKongId

    // the bonoboId for the new key should be same as the bonoboId for the first one
    bonoboId shouldBe dynamo.retrieveKey("the-dark-day").value.bonoboId
  }

  behavior of "making a key inactive"

  it should "delete the key from Kong and set it has inactive on Bonobo" in {
    Thread.sleep(3000L)
    val createUserResult = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "bruce.wayne@wayneenterprises.com",
      "name" -> "Bruce Wayne",
      "company" -> "Wayne Enterprises",
      "url" -> "http://wayneenterprises.com.co.uk",
      "tier" -> "RightsManaged",
      "key" -> "testing-inactive"
    )).get

    status(createUserResult) should be(SEE_OTHER) // on success it redirects to the "edit user" page

    val consumerId = waitForDyanmo("testing-inactive")(dynamo.retrieveKey("testing-inactive").value.kongId)

    // check the key exists on Kong
    Await.result(checkKeyExistsOnKong(consumerId), atMost = 10.seconds) shouldBe true

    Thread.sleep(3000L)
    val makeKeyInactiveResult = route(FakeRequest(POST, "/key/testing-inactive/edit").withFormUrlEncodedBody(
      "key" -> "testing-inactive",
      "requestsPerDay" -> "10000",
      "requestsPerMinute" -> "720",
      "tier" -> "RightsManaged",
      "status" -> "Inactive"
    )).get

    status(makeKeyInactiveResult) should be(SEE_OTHER) // on success it redirects to the "edit key" page

    // check the key doesn't exist on Kong anymore
    Await.result(checkKeyExistsOnKong(consumerId), atMost = 10.seconds) shouldBe false

  }
}

