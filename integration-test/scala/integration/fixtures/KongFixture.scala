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
  val kongApiName = s"integration-test-${Random.alphanumeric.take(10).mkString}"
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
    // Setup using the deprecated api interface
    println("Registering the API with Kong")
    s"curl -sS -X POST $kongUrl/apis -d name=$kongApiName -d hosts=$apiPublicHostname -d upstream_url=$apiBackendUrl".!

    println("Enabling the key-auth plugin for API")
    s"curl -sS -X POST $kongUrl/apis/$kongApiName/plugins/ -d name=key-auth".!

    // Setup a service and routing pair
    println("Creating Kong service")
    val service = Await.result(wsClient.url(s"$kongUrl/services").post(Json.obj(
      "name" -> kongApiName,
      "url" -> apiBackendUrl
    )).map {
      response =>
        println("Create Kong service replied: " + response.body)
        response.json
    }, atMost = 10.seconds)

    println("Creating Kong route for service: " + service)
    val route = Await.result(wsClient.url(s"$kongUrl/routes").post(Json.obj(
      "hosts" -> Seq(apiPublicHostname),
      "service" -> service
    )).map { response =>
      println("Create Kong route replied: " + response.body)
      response.json
    }, atMost = 10.seconds)

    val routeId = (route \ "id").get.as[String]
    println("Enabling key-auth plugin for route: " + routeId)
    Await.result(wsClient.url(s"$kongUrl/routes/$routeId/plugins").post(Json.obj(
      "name" -> "key-auth"
    )).map {
      response =>
        println("Kong enable route replied: " + response.body)
        response.json
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
