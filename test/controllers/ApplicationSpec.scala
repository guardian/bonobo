package controllers

import email.MailClient
import models._
import org.joda.time.DateTime
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import play.api.i18n.{ DefaultLangs, DefaultMessagesApi }
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._
import play.api.{ Configuration, Environment }
import store._
import kong._
import scala.concurrent.Future

class ApplicationSpec extends FlatSpec with Matchers with MockitoSugar {

  val mockDynamo = mock[DB]
  val mockKong = mock[Kong]
  val mockEmail = mock[MailClient]
  val messagesApi = new DefaultMessagesApi(Environment.simple(), Configuration.reference, new DefaultLangs(Configuration.reference))

  "showKeys" should "contains some keys" in {
    val dynamo = new DB {

      def search(query: String, limit: Int = 20): List[BonoboInfo] = ???

      def saveUser(bonoboKey: BonoboUser): Unit = ???

      def updateUser(bonoboUser: BonoboUser): Unit = ???

      def getUserWithId(id: String): Option[BonoboUser] = ???

      def getUserWithEmail(email: String): Option[BonoboUser] = ???

      def saveKey(kongKey: KongKey): Unit = ???

      def updateKey(kongKey: KongKey): Unit = ???

      def getKeys(direction: String, range: Option[String], limit: Int = 20): ResultsPage[BonoboInfo] = {
        ResultsPage(List(BonoboInfo(KongKey("bonoboId", "kongId", "my-new-key", 10, 1, Tier.Developer, "Active", new DateTime(), "product name", "product url", "rangekey"),
          BonoboUser("id", "name", "email", "company name", "company url",
            AdditionalUserInfo(DateTime.now(), ManualRegistration)))), false)
      }

      def getKeyWithUserId(id: String): Option[KongKey] = ???

      def getKeyWithValue(key: String): Option[KongKey] = ???

      def getKeysWithUserId(id: String): List[KongKey] = ???

      def getNumberOfKeys(): Long = 1
    }
    val application = new Application(dynamo, mockKong, mockEmail, messagesApi, null, false)
    val result: Future[Result] = application.showKeys("next", None).apply(FakeRequest())
    contentAsString(result) should include("my-new-key")
  }

  "brokenForm" should "check form validation works" in {
    val myRequest: (String, String) = ("name", "")
    val application = new Application(mockDynamo, mockKong, mockEmail, messagesApi, null, false)

    val result: Future[Result] = application.createUser.apply(FakeRequest().withFormUrlEncodedBody(myRequest))
    contentAsString(result) should include("This field is required")
  }

  "emptySearch" should "do not allow an empty search" in {
    val application = new Application(mockDynamo, mockKong, mockEmail, messagesApi, null, false)
    val result: Future[Result] = application.search.apply(FakeRequest().withFormUrlEncodedBody(Map("query" -> "").toSeq: _*))
    contentAsString(result) should include("Invalid search")
  }
}
