package controllers

import models.{ KongCreateConsumerResponse, RateLimits, BonoboKey }
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

  "index" should "say yo" in {
    val application = new Application(mockDynamo, mockKong, null)
    val result: Future[Result] = application.index.apply(FakeRequest())
    contentAsString(result) should include("Yo yo yo")
  }

  "showKeys" should "contains some keys" in {
    val dynamo = new DB {
      def search(query: String, limit: Port): List[BonoboKey] = ???
      def save(bonoboKey: BonoboKey): Unit = ???
      def getKeys(direction: String, range: String): (List[BonoboKey], Boolean) = {
        (List(BonoboKey("id", "key", "email@some.com", "name", "company", "url", 200, 2, "1", "Active", "293029")), false)
      }
      def retrieveKey(id: String): BonoboKey = ???
    }
    val application = new Application(dynamo, mockKong, messagesApi)
    val result: Future[Result] = application.showKeys("next", "").apply(FakeRequest())
    contentAsString(result) should include("email@some.com")
  }

  "brokenForm" should "check form validation works" in {
    val myRequest: (String, String) = ("name", "")
    val application = new Application(mockDynamo, mockKong, messagesApi)

    val result: Future[Result] = application.createKey.apply(FakeRequest().withFormUrlEncodedBody(myRequest))
    contentAsString(result) should include("This field is required")
  }

  "emptySearch" should "do not allow an empty search" in {
    val application = new Application(mockDynamo, mockKong, messagesApi)
    val result: Future[Result] = application.search.apply(FakeRequest().withFormUrlEncodedBody(Map("query" -> "").toSeq: _*))
    contentAsString(result) should include("Invalid search")
  }

  /* TODO: this test is pretty useless but I'll leave it here as a reference for now */
  "insertNewKey" should "insert a new key" in {
    val myRequest = Map("id" -> "1234", "key" -> "123", "name" -> "Bruce Wayne", "email" -> "batman@ddd.com",
      "company" -> "Wayne Enterprises", "url" -> "www.lol.com", "requestsPerDay" -> "200", "requestsPerMinute" -> "10",
      "tier" -> "1", "status" -> "active", "created_at" -> "1231321123")

    val kong = new Kong {
      def registerUser(username: String, rateLimit: RateLimits): Future[KongCreateConsumerResponse] = {
        Future.successful(KongCreateConsumerResponse(id = "31231231", created_at = 43242342))
      }
    }
    val application = new Application(mockDynamo, kong, messagesApi)

    val result: Future[Result] = application.createKey.apply(FakeRequest().withFormUrlEncodedBody(myRequest.toSeq: _*))
    contentAsString(result) should include("A new user has been successfully added")
  }
}