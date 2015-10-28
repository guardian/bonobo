package integration

import org.scalatest._
import play.api.test.Helpers._
import play.api.test.FakeRequest

class CreateUserSpec extends FlatSpec with Matchers with OptionValues with IntegrationSpecBase {

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

    //println(contentAsString(result))
    status(result) should be(SEE_OTHER) // on success it redirects to the "edit user" page

    // TODO use `dynamoClient` to check the content of Dynamo
    // TODO could also make HTTP requests to Kong API to check the contents of Kong
  }

  // TODO write more tests! e.g. creating a user without specifying a key

}

