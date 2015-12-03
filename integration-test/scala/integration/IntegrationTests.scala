package integration

import models._
import org.joda.time.DateTime
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ Matchers, OptionValues, FlatSpec }
import play.api.test.Helpers._
import play.api.test.FakeRequest

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._

class IntegrationTests extends FlatSpec with Matchers with OptionValues with IntegrationSpecBase with ScalaFutures with Eventually {

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
      "key" -> "123124-13434-32323-3439",
      "sendEmail" -> "false"
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

  it should "show error message when the email hasn't been sent" in {
    val result = route(FakeRequest(POST, "/user/create").withFormUrlEncodedBody(
      "email" -> "testing-email@wayneenterprises.com",
      "name" -> "Joe Bloggs",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "url" -> "http://wayneenterprises.com.co.uk",
      "tier" -> "RightsManaged",
      "key" -> "the-dark",
      "sendEmail" -> "true"
    )).get

    status(result) shouldBe 303 // on success it redirects to the "edit user" page
    flash(result).get("error") shouldBe defined
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
      "key" -> "",
      "sendEmail" -> "false"
    )).get

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
      "key" -> "the-dark-knight",
      "sendEmail" -> "false"
    )).get

    status(result) shouldBe 303 // on success it redirects to the "edit user" page

    val bonoboId = dynamo.getKeyWithValue("the-dark-knight").value.bonoboId

    val addKeyResult = route(FakeRequest(POST, s"/key/create/$bonoboId").withFormUrlEncodedBody(
      "tier" -> "RightsManaged",
      "productName" -> "Another Product",
      "productUrl" -> "http://anotherproduct.co.uk",
      "key" -> "the-dark-day",
      "sendEmail" -> "false"
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
      "key" -> "testing-inactive",
      "sendEmail" -> "false"
    )).get

    status(createUserResult) shouldBe 303 // on success it redirects to the "edit user" page

    val consumerId = dynamo.getKeyWithValue("testing-inactive").value.kongId

    // check the key exists on Kong
    Await.result(checkKeyExistsOnKong(consumerId), atMost = 10.seconds) shouldBe true

    val makeKeyInactiveResult = route(FakeRequest(POST, "/key/testing-inactive/edit").withFormUrlEncodedBody(
      "key" -> "testing-inactive",
      "productName" -> "Another product",
      "productUrl" -> "http://anotherproduct.co.uk",
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
      "key" -> "testing-inactive",
      "sendEmail" -> "false"
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
      "key" -> "testing-update-rate-limits",
      "sendEmail" -> "false"
    )).get

    status(request) shouldBe 303

    val update = route(FakeRequest(POST, "/key/testing-update-rate-limits/edit").withFormUrlEncodedBody(
      "key" -> "some-key",
      "productName" -> "Another product",
      "productUrl" -> "http://anotherproduct.co.uk",
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
      "key" -> "testing-duplicate-keys",
      "sendEmail" -> "false"
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
      "key" -> "testing-duplicate-keys",
      "sendEmail" -> "false"
    )).get

    // check we return a conflict error, and no user is added on Bonobo-Keys
    status(req2) shouldBe 409
    dynamo.isEmailInUse("user-2@email.com") should be (false)
  }

  behavior of "creating a new user using the open registration form"

  it should "add a Bonobo user and key to Dynamo" in {
    val result = route(FakeRequest(POST, "/register/developer").withFormUrlEncodedBody(
      "name" -> "Joe Bloggs",
      "email" -> "test@openform.com",
      "companyName" -> "The Test Company",
      "companyUrl" -> "http://thetestcompany.co.uk",
      "productName" -> "http://blabla",
      "productUrl" -> "http://blabla",
      "acceptTerms" -> "true"
    )).get

    status(result) shouldBe 303 // on success it redirects to the show key page

    val dynamoBonoboUser = dynamo.getUserWithEmail("test@openform.com")
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

  behavior of "submit a commercial request using the form"

  it should "add a Bonobo user to Dynamo" in {
    val userToSave = new BonoboUser(
      bonoboId = "id",
      name = "Joe Bloggs",
      email = "test@commercialform.com",
      companyName = "The Test Company",
      companyUrl = "http://thetestcompany.co.uk",
      additionalInfo = AdditionalUserInfo(
        businessArea = Some("News"),
        monthlyUsers = Some("100"),
        commercialModel = Some("Model"),
        content = Some("News"),
        contentFormat = Some("Text"),
        articlesPerDay = Some("20"),
        createdAt = DateTime.now(),
        registrationType = CommercialRegistration))
    val result = route(FakeRequest(POST, "/register/commercial").withFormUrlEncodedBody(
      "name" -> userToSave.name,
      "email" -> userToSave.email,
      "companyName" -> userToSave.companyName,
      "companyUrl" -> userToSave.companyUrl,
      "productName" -> "Product",
      "productUrl" -> "http://product.co.uk",
      "businessArea" -> userToSave.additionalInfo.businessArea.value,
      "monthlyUsers" -> userToSave.additionalInfo.monthlyUsers.value,
      "commercialModel" -> userToSave.additionalInfo.commercialModel.value,
      "content" -> userToSave.additionalInfo.content.value,
      "contentFormat" -> userToSave.additionalInfo.contentFormat.value,
      "articlesPerDay" -> userToSave.additionalInfo.articlesPerDay.value,
      "acceptTerms" -> "true"
    )).get

    status(result) shouldBe 303 // on success it redirects to the message page

    val dynamoBonoboUser = dynamo.getUserWithEmail("test@commercialform.com")

    dynamoBonoboUser.value shouldBe userToSave.copy(bonoboId = dynamoBonoboUser.value.bonoboId, additionalInfo = userToSave.additionalInfo.copy(createdAt = dynamoBonoboUser.value.additionalInfo.createdAt))
  }

  it should "show error message when the email hasn't been sent" in {
    val userToSave = new BonoboUser(
      bonoboId = "id",
      name = "Joe Bloggs",
      email = "test-email@commercialform.com",
      companyName = "The Test Company",
      companyUrl = "http://thetestcompany.co.uk",
      additionalInfo = AdditionalUserInfo(
        businessArea = Some("News"),
        monthlyUsers = Some("100"),
        commercialModel = Some("Model"),
        content = Some("News"),
        contentFormat = Some("Text"),
        articlesPerDay = Some("20"),
        createdAt = DateTime.now(),
        registrationType = CommercialRegistration))
    val result = route(FakeRequest(POST, "/register/commercial").withFormUrlEncodedBody(
      "name" -> userToSave.name,
      "email" -> userToSave.email,
      "companyName" -> userToSave.companyName,
      "companyUrl" -> userToSave.companyUrl,
      "productName" -> "Product",
      "productUrl" -> "http://product.co.uk",
      "businessArea" -> userToSave.additionalInfo.businessArea.value,
      "monthlyUsers" -> userToSave.additionalInfo.monthlyUsers.value,
      "commercialModel" -> userToSave.additionalInfo.commercialModel.value,
      "content" -> userToSave.additionalInfo.content.value,
      "contentFormat" -> userToSave.additionalInfo.contentFormat.value,
      "articlesPerDay" -> userToSave.additionalInfo.articlesPerDay.value,
      "acceptTerms" -> "true"
    )).get

    status(result) shouldBe 303 // on success it redirects to the message page
    flash(result).get("error") shouldBe defined
  }
}

