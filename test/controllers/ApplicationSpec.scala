package controllers

import kong.Kong.Happy
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

      def search(query: String, limit: Port): List[KongKey] = ???
      def saveBonoboUser(bonoboUser: BonoboUser): Unit = ???
      def saveKongKey(kongKey: KongKey): Unit = ???
      def updateKongKey(kongKey: KongKey): Unit = ???
      def deleteKongKey(createdAt: String): Unit = ???
      def retrieveKey(id: String): KongKey = ???

      def getKeys(direction: String, range: String): (List[KongKey], Boolean) = {
        (List(KongKey("id", "my-new-key", 10, 1, "dev", "Active", "some date")), false)
      }
    }
    val application = new Application(dynamo, mockKong, messagesApi, null, false)
    val result: Future[Result] = application.showKeys("next", "").apply(FakeRequest())
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

  /* TODO: this test is pretty useless but I'll leave it here as a reference for now */
  /*
  "insertNewKey" should "insert a new key" in {
    val myRequest = Map("id" -> "1234", "key" -> "123", "name" -> "Bruce Wayne", "email" -> "batman@ddd.com",
      "company" -> "Wayne Enterprises", "url" -> "www.lol.com", "requestsPerDay" -> "200", "requestsPerMinute" -> "10",
      "tier" -> "Internal", "status" -> "active", "created_at" -> "1231321123")

    val kong = new Kong {
      def registerUser(username: String, rateLimit: RateLimits): Future[UserCreationResult] = {
        Future.successful(UserCreationResult(id = "31231231", createdAt = new DateTime(1444830845000L), "my-random-key"))
      }
      def updateUser(id: String, newRateLimit: RateLimits): Future[Happy.type] = ???
      def createKey(consumerId: String, customKey: Option[String] = None): Future[String] = ???
      def deleteKey(consumerId: String): Future[Happy.type] = ???
    }
    val application = new Application(mockDynamo, kong, messagesApi, null, false)

    val result: Future[Result] = application.createUser.apply(FakeRequest().withFormUrlEncodedBody(myRequest.toSeq: _*))
    contentAsString(result) should include("A new user has been successfully added")
  }
  */
}