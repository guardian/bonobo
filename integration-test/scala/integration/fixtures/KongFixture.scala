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
    s"nc -z $containersHost 5434".! match {
      case 0 => ()
      case _ =>
        println(s"Waiting for Postgres to start listening ...")
        Thread.sleep(1000L)
        waitForPostgresToStart()
    }
  }

  private def configureKong(): Unit = {
    println("Registering the API with Kong")
    s"curl -sS -X POST $kongUrl/apis -d name=$kongApiName -d request_host=foo.com -d upstream_url=http://example.com".!

    println("Enabling the key-auth plugin")
    s"curl -sS -X POST $kongUrl/apis/$kongApiName/plugins/ -d name=key-auth".!
  }

  override def beforeAll(): Unit = {
    "docker create -p 5434:5432 -e POSTGRES_USER=kong -e POSTGRES_DB=kong --name postgres postgres:12.3".!
    println(s"Created Postgres container")

    "docker create -p 8000:8000 -p 8001:8001 -p 8443:8443 -p 7946:7946 -p 7946:7946/udp --name kong --link postgres:postgres -e KONG_DATABASE=postgres -e KONG_PG_HOST=postgres kong:0.14".!
    println(s"Created Kong container")

    "docker start postgres".!
    println(s"Started Postgres container")
    waitForPostgresToStart()

    "docker start kong".!
    println(s"Started Kong container")
    waitForKongToStart()

    configureKong()

    super.beforeAll()
  }

  override def afterAll(): Unit = {
    try super.afterAll()
    finally {
      Try {
        "docker kill kong".!!
        println("Killed Kong container")
        Thread.sleep(2000L)
      } recover {
        case e => println(s"Failed to kill Kong container. Exception: $e}")
      }

      Try {
        "docker kill postgres".!!
        println("Killed Postgres container")
        Thread.sleep(2000L)
      } recover {
        case e => println(s"Failed to kill Postgres container. Exception: $e}")
      }

      Try {
        "docker rm kong".!!
        println("Removed Kong container")
      } recover {
        case e => println(s"Failed to remove Kong container. Exception: $e}")
      }

      Try {
        "docker rm postgres".!!
        println("Removed Postgres container")
      } recover {
        case e => println(s"Failed to remove Postgres container. Exception: $e}")
      }
    }
  }

}
