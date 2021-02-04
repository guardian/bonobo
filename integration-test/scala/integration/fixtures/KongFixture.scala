package integration.fixtures

import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.sys.process._
import scala.util.{Random, Try}

trait KongFixture extends BeforeAndAfterAll { this: Suite =>

  def wsClient: WSClient

  val containersHost = "localhost"
  val kongUrl = s"http://$containersHost:8001"
  val kongServiceName = s"integration-test-${Random.alphanumeric.take(10).mkString}"
  val apiBackendUrl = "http://example.com"
  val apiPublicHostname = "foo.com"

  @tailrec
  private def waitForKongToStart(): Unit = {
    s"curl -s -q $kongUrl".! match {
      case 0 => ()
      case _ =>
        println(s"Waiting for Kong to start listening ...")
        Thread.sleep(1000L)
        waitForKongToStart()
    }
  }

  @tailrec
  private def waitForPostgresToStart(): Unit = {
    s"nc -z $containersHost 5432".! match {
      case 0 => ()
      case _ =>
        println(s"Waiting for Postgres to start listening ...")
        Thread.sleep(1000L)
        waitForPostgresToStart()
    }
  }

  private def configureKong(): Unit = {
    // Setup a service and routing pair
    println("Creating Kong service")
    Await.result(wsClient.url(s"$kongUrl/services").post(Json.obj(
      "name" -> kongServiceName,
      "url" -> apiBackendUrl
    )).map {
      response =>
        println("Create Kong service replied: " + response.body)
    }, atMost = 10.seconds)

    println("Enabling key-auth plugin for service")
    Await.result(wsClient.url(s"$kongUrl/routes/$kongServiceName/plugins").post(Json.obj(
      "name" -> "key-auth"
    )).map {
      response =>
        println("Kong enable route replied: " + response.body)
    }, atMost = 10.seconds)

    println("Creating Kong route for service")
    val route = Await.result(wsClient.url(s"$kongUrl/services/$kongServiceName/routes").post(Json.obj(
      "hosts" -> Seq(apiPublicHostname)
    )).map { response =>
      println("Create Kong route replied: " + response.body)
    }, atMost = 10.seconds)
  }

  override def beforeAll(): Unit = {
    println(s"Creating containers")
    "docker-compose -f scripts/docker-compose.yml up -d".!

    println(s"Waiting for Postgres container")
    waitForPostgresToStart()

    println(s"Waiting for Kong container")
    waitForKongToStart()

    configureKong()

    super.beforeAll()
  }

  override def afterAll(): Unit = {
    try super.afterAll()
    finally {
      Try {
        "docker-compose -f scripts/docker-compose.yml down".!!
        println("Killed containers")
        Thread.sleep(2000L)
      } recover {
        case e => println(s"Failed to kill containers. Exception: $e}")
      }
    }
  }

}
