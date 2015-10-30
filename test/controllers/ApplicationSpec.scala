package controllers

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
  val messagesApi = new DefaultMessagesApi(Environment.simple(), Configuration.reference, new DefaultLangs(Configuration.reference))

  "showKeys" should "contains some keys" in {
    val dynamo = new DB {

      def search(query: String, limit: Port): List[BonoboInfo] = ???
      def saveBonoboUser(bonoboUser: BonoboUser): Unit = ???
      def saveKongKey(kongKey: KongKey): Unit = ???
      def getNumberOfKeys: Long = 1
      def updateKongKey(kongKey: KongKey): Unit = ???
      def deleteKongKey(createdAt: String): Unit = ???
      def retrieveKey(id: String): Option[KongKey] = ???

      def getKeys(direction: String, range: Option[String]): ResultsPage[BonoboInfo] = {
        ResultsPage(List(BonoboInfo(KongKey("bonoboId", "kongId", "my-new-key", 10, 1, Tier.withName("Developer").get, "Active", new DateTime(), "rangekey"), BonoboUser("id", "name", "email", "product name", "product url", "company name", Some("company url")))), false)
      }

      def retrieveUser(userId: String): Option[BonoboUser] = ???
      def retrieveUserByEmail(email: String): Option[BonoboUser] = ???
      def updateBonoboUser(bonoboUser: BonoboUser): Unit = ???
      def getAllKeysWithId(id: String): List[KongKey] = ???
      def getUserWithId(id: String): BonoboUser = ???
      def getKeyForUser(userId: String): String = ???
      def getKeyForEmail(email: String): Option[BonoboUser] = ???
    }
    val application = new Application(dynamo, mockKong, messagesApi, null, false)
    val result: Future[Result] = application.showKeys("next", None).apply(FakeRequest())
    contentAsString(result) should include("my-new-key")
  }

  "brokenForm" should "check form validation works" in {
    val myRequest: (String, String) = ("name", "")
    val application = new Application(mockDynamo, mockKong, messagesApi, null, false)

    val result: Future[Result] = application.createUser.apply(FakeRequest().withFormUrlEncodedBody(myRequest))
    contentAsString(result) should include("This field is required")
  }

  "emptySearch" should "do not allow an empty search" in {
    val application = new Application(mockDynamo, mockKong, messagesApi, null, false)
    val result: Future[Result] = application.search.apply(FakeRequest().withFormUrlEncodedBody(Map("query" -> "").toSeq: _*))
    contentAsString(result) should include("Invalid search")
  }
}