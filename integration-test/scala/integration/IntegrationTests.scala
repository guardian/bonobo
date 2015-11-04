package integration

import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ Matchers, OptionValues, FlatSpec }
import play.api.test.Helpers._
import play.api.test.FakeRequest

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._

class IntegrationTests extends FlatSpec with Matchers with OptionValues with IntegrationSpecBase with ScalaFutures with Eventually {

  /* HELPER FUNCTIONS */

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
      .withQueryString("consumer_id" -> consumerId).get().map {
      response =>
        (response.json \\ "day").headOption match {
          case Some(JsNumber(config)) if config.toInt == day => true
          case _ => false
        }
    }
  }

  /* ACTUAL TESTS */

  behavior of "creating a new user with a custom key"

  it should "add a Bonobo user and key to Dynamo" in {
    val result = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "test@thetestcompany.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "tier" -> "RightsManaged",
      "key" -> "123124-13434-32323-3439"
    )).get

    status(result) shouldBe 303 // on success it redirects to the "edit user" page

    val dynamoKongKey = dynamo.getKeyWithValue("123124-13434-32323-3439")
    val consumerId = dynamoKongKey.value.kongId

    // check the consumerId in dynamo matches the one on Kong
    Await.result(checkConsumerExistsOnKong(consumerId), atMost = 10.seconds) shouldBe true

    // check Kong's key value matches Bonobo-Keys.keyValue
    val keyValue = Await.result(getKeyForConsumerId(consumerId), atMost = 10.seconds)
    keyValue shouldBe dynamoKongKey.value.key

    // check Bonobo-Keys.bonoboId is the same as Bonobo-Keys.kongId; this is only true for the first key of every user we create
    dynamoKongKey.value.bonoboId shouldBe dynamoKongKey.value.kongId

    // check Bonobo-Users.id matches Bonobo-Keys.kongId
    dynamo.getUserWithId(consumerId).value.bonoboId shouldBe dynamoKongKey.value.kongId
  }

  behavior of "creating a new user without specifying a custom key"

  it should "add a Bonobo user and a randomly generated key" in {
    val result = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "fsdlfkjsd@email.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "tier" -> "RightsManaged",
      "key" -> "23492342-2342342")).get

    status(result) shouldBe 303 // on success it redirects to the "edit user" page

    val dynamoBonoboUser = dynamo.getUserWithEmail("fsdlfkjsd@email.com")
    val consumerId = dynamoBonoboUser.value.bonoboId

    // check the consumerId in dynamo matches the one on Kong
    Await.result(checkConsumerExistsOnKong(consumerId), atMost = 10.seconds) shouldBe true

    // check Kong's key value matches Bonobo-Keys.keyValue
    val keyValue = Await.result(getKeyForConsumerId(consumerId), atMost = 10.seconds)
    val dynamoKongKey = dynamo.getKeyWithValue(keyValue)
    keyValue shouldBe dynamoKongKey.value.key

    // check Bonobo-Users.id matches Bonobo-Keys.kongId
    dynamo.getUserWithId(consumerId).value.bonoboId shouldBe dynamoKongKey.value.kongId
  }

  behavior of "adding a second key to an existing user"

  it should "add a new key for the existing user" in {
    val result = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "bruce.wayne@wayneenterprises.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "url" -> "http://wayneenterprises.com.co.uk",
      "tier" -> "RightsManaged",
      "key" -> "the-dark-knight"
    )).get

    status(result) shouldBe 303 // on success it redirects to the "edit user" page

    val bonoboId = dynamo.getKeyWithValue("the-dark-knight").value.bonoboId

    val addKeyResult = route(FakeRequest(POST, s"/key/create/$bonoboId").withFormUrlEncodedBody(
      "tier" -> "RightsManaged",
      "key" -> "the-dark-day"
    )).get

    status(addKeyResult) shouldBe 303 // on success it redirects to the "edit user" page

    val firstKongId = dynamo.getKeyWithValue("the-dark-knight").value.kongId
    val secondKongId = dynamo.getKeyWithValue("the-dark-day").value.kongId

    // check the consumerId in dynamo matches the one on Kong
    Await.result(checkConsumerExistsOnKong(secondKongId), atMost = 10.seconds) shouldBe true

    bonoboId shouldBe firstKongId
    bonoboId should not be secondKongId

    // the bonoboId for the new key should be same as the bonoboId for the first one
    bonoboId shouldBe dynamo.getKeyWithValue("the-dark-day").value.bonoboId
  }

  behavior of "making a key inactive"

  it should "delete the key from Kong and set it has inactive on Bonobo" in {
    val createUserResult = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "sldkjfdslk@sdlkfjsl.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "url" -> "http://wayneenterprises.com.co.uk",
      "tier" -> "RightsManaged",
      "key" -> "testing-inactive"
    )).get

    status(createUserResult) shouldBe 303 // on success it redirects to the "edit user" page

    val consumerId = dynamo.getKeyWithValue("testing-inactive").value.kongId

    // check the key exists on Kong
    Await.result(checkKeyExistsOnKong(consumerId), atMost = 10.seconds) shouldBe true

    val makeKeyInactiveResult = route(FakeRequest(POST, "/key/testing-inactive/edit").withFormUrlEncodedBody(
      "key" -> "testing-inactive",
      "requestsPerDay" -> "10000",
      "requestsPerMinute" -> "720",
      "tier" -> "RightsManaged",
      "status" -> "Inactive"
    )).get

    status(makeKeyInactiveResult) shouldBe 303 // on success it redirects to the "edit key" page

    // check the key doesn't exist on Kong anymore
    Await.result(checkKeyExistsOnKong(consumerId), atMost = 10.seconds) shouldBe false

    // check the key is marked as inactive on Bonobo-Keys
    dynamo.getKeyWithValue("testing-inactive").value.status shouldBe "Inactive"

    // trying to create a new key with the same value as an inactive key should fail
    val failUser = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "bruce.wayne@wayneenterprises.com-2",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "url" -> "http://wayneenterprises.com.co.uk-2",
      "tier" -> "RightsManaged",
      "key" -> "testing-inactive"
    )).get

    // check we return a conflict error, and that the key table is still inactive
    status(failUser) shouldBe 409
    dynamo.getKeyWithValue("testing-inactive").value.status shouldBe "Inactive"
  }

  behavior of "updating the rate limits for a key"

  it should "update the rate limits on the Bonobo-Keys table, as well as the consumer on Kong" in {
    val request = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "some-user@email.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "url" -> "some url",
      "tier" -> "RightsManaged",
      "key" -> "testing-update-rate-limits"
    )).get

    status(request) shouldBe 303

    val update = route(FakeRequest(POST, "/key/testing-update-rate-limits/edit").withFormUrlEncodedBody(
      "key" -> "some-key",
      "requestsPerDay" -> "444",
      "requestsPerMinute" -> "44",
      "tier" -> "Internal",
      "status" -> "active"
    )).get

    status(update) shouldBe 303

    dynamo.getKeyWithValue("testing-update-rate-limits").value.requestsPerDay shouldBe 444

    val consumerId = dynamo.getKeyWithValue("testing-update-rate-limits").value.kongId
    Await.result(checkRateLimitsMatch(consumerId, 44, 444), atMost = 10.seconds) shouldBe true
  }

  behavior of "creating a duplicate key"

  it should "fail" in {
    val req1 = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "user-1@email.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "url" -> "some url",
      "tier" -> "RightsManaged",
      "key" -> "testing-duplicate-keys"
    )).get

    status(req1) shouldBe 303

    val req2 = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "user-2@email.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "url" -> "some url",
      "tier" -> "RightsManaged",
      "key" -> "testing-duplicate-keys"
    )).get

    // check we return a conflict error, and not user is added on Bonobo-Keys
    status(req2) shouldBe 409
    dynamo.getUserWithEmail("user-2@email.com") should not be defined
  }
}

