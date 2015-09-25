package controllers

import org.scalatest._
import org.scalatest.mock.MockitoSugar
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._

import store._
import kong._

import scala.concurrent.Future

class ApplicationSpec extends FlatSpec with Matchers with MockitoSugar {

  val mockDynamo = mock[DB]
  val mockKong = mock[Kong]

  val application = new Application(mockDynamo, mockKong, null)

  "index" should "say yo" in {
    val result: Future[Result] = application.index().apply(FakeRequest())
    contentAsString(result) should include("Yo yo yo")
  }

}
