package controllers

import email.MailClient
import models._
import com.gu.googleauth.UserIdentity
import org.joda.time.DateTime
import org.scalatest._
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n._
import play.api.mvc._
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.test._
import play.api.test.Helpers._
import play.api.{ Configuration, Environment }
import store._
import kong._
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class ApplicationSpec extends FlatSpec with Matchers with MockitoSugar with StubBodyParserFactory with StubControllerComponentsFactory {

  implicit val lang = Lang("en")
  val mockDynamo = mock[DB]
  val mockKong = mock[Kong]
  val mockEmail = mock[MailClient]
  val mockAssetsFinder = mock[AssetsFinder]
  val mockMessages = new DefaultMessagesApi(
    Map("en" ->
      Map("error.required" -> "This field is required")))
  val mockCC = stubControllerComponents(messagesApi = mockMessages)

  "showKeys" should "contains some keys" in {
    val dynamo = new DB {

      def search(query: String, limit: Int = 20): List[BonoboInfo] = ???

      def saveUser(bonoboKey: BonoboUser): Unit = ???

      def deleteUser(bonoboUser: BonoboUser): Unit = ???

      def updateUser(bonoboUser: BonoboUser): Unit = ???

      def getUserWithId(id: String): Option[BonoboUser] = ???

      def isEmailInUse(email: String): Boolean = ???

      def getUserWithEmail(email: String): Option[BonoboUser] = ???

      def updateKey(kongKey: KongKey): Unit = ???

      def deleteKey(kongKey: KongKey): Unit = ???

      def getKeys(direction: String, range: Option[String], limit: Int = 20, filterLabels: Option[List[String]]): ResultsPage[BonoboInfo] = {
        ResultsPage(List(BonoboInfo(
          KongKey("bonoboId", "kongConsumerId", "my-new-key", 10, 1, Tier.Developer, "Active", new DateTime(), "product name", Some("product url"), "rangekey"),
          BonoboUser("id", "name", "email", Some("company name"), Some("company url"),
            AdditionalUserInfo(DateTime.now(), ManualRegistration), List.empty))), false)
      }

      def getKeyWithUserId(id: String): Option[KongKey] = ???

      def isKeyPresent(key: String): Boolean = ???

      def getKeyWithValue(key: String): Option[KongKey] = ???

      def getKeysWithUserId(id: String): List[KongKey] = ???

      def getNumberOfKeys(): Long = 1

      def getEmails(tier: String, status: String): List[String] = ???

      /**
       * The following methods are used for labeling an user
       */
      def getLabels(): List[Label] = ???

      def saveKey(kongKey: KongKey, labelIds: List[String]): Unit = ???

      def getLabelsFor(bonoboId: String): List[String] = ???

    }
    val application = new Application(mockCC, dynamo, mockKong, mockEmail, Map.empty, null, mockAssetsFinder, false)
    val result: Future[Result] = application.showKeys(List.empty, "next", None).apply(FakeRequest())
    contentAsString(result) should include("my-new-key")
  }

  "brokenForm" should "check form validation works" in {
    val myRequest: (String, String) = ("name", "")
    val application = new Application(mockCC, mockDynamo, mockKong, mockEmail, Map.empty, null, mockAssetsFinder, false)

    val result: Future[Result] = application.createUser.apply(FakeRequest().withFormUrlEncodedBody(myRequest))
    contentAsString(result) should include("This field is required")
  }

  "emptySearch" should "do not allow an empty search" in {
    val application = new Application(mockCC, mockDynamo, mockKong, mockEmail, Map.empty, null, mockAssetsFinder, false)
    val result: Future[Result] = application.search.apply(FakeRequest().withFormUrlEncodedBody(Map("query" -> "").toSeq: _*))
    contentAsString(result) should include("Invalid search")
  }
}
