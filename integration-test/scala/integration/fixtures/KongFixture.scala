package integration.fixtures

import org.scalatest.{ BeforeAndAfterAll, Suite }
import scala.annotation.tailrec
import scala.util.{ Try, Random }
import sys.process._

trait KongFixture extends BeforeAndAfterAll { this: Suite =>

  val containersHost = "localhost"
  val kongUrl = s"http://$containersHost:8001"
  val kongApiName = s"integration-test-${Random.alphanumeric.take(10).mkString}"

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
    println("Registering the API with Kong")
    s"curl -sS -X POST $kongUrl/apis -d name=$kongApiName -d hosts=foo.com -d upstream_url=http://example.com".!

    println("Enabling the key-auth plugin")
    s"curl -sS -X POST $kongUrl/apis/$kongApiName/plugins/ -d name=key-auth".!
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
