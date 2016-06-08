package integration.fixtures

import org.scalatest.{ BeforeAndAfterAll, Suite }
import scala.annotation.tailrec
import scala.util.{Try, Random}
import sys.process._

trait KongFixture extends BeforeAndAfterAll { this: Suite =>

  val containersHost = {
    /*
    If we are running boot2docker on an OSX developer machine, $DOCKER_HOST will be set and will give us the VirtualBox VM's address.
    If we are on TeamCity, docker port forwarding should work properly so we can connect to Kong on localhost.
    */
    val DockerHostPattern = """tcp://(.+):\d+""".r
    sys.env.get("DOCKER_HOST").fold("localhost") {
      // DOCKER_HOST will look like tcp://192.168.99.100:2376. Extract the IP address from there.
      case DockerHostPattern(hostname) => hostname
      case other => fail(s"DOCKER_HOST had an unexpected format: $other")
    }
  }
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
  private def waitForCassandraToStart(): Unit = {
    s"nc -z $containersHost 9042".! match {
      case 0 => ()
      case _ =>
        println(s"Waiting for Cassandra to start listening ...")
        Thread.sleep(1000L)
        waitForCassandraToStart()
    }
  }

  private def configureKong(): Unit = {
    println("Registering the API with Kong")
    s"curl -sS -X POST $kongUrl/apis -d name=$kongApiName -d request_host=foo.com -d upstream_url=http://example.com".!

    println("Enabling the key-auth plugin")
    s"curl -sS -X POST $kongUrl/apis/$kongApiName/plugins/ -d name=key-auth".!
  }

  override def beforeAll(): Unit = {
    "docker create -p 9042:9042 --name cassandra mashape/cassandra".!
    println(s"Created Cassandra container")

    "docker create -p 8000:8000 -p 8001:8001 --name kong --link cassandra:cassandra mashape/kong:0.7.0".!
    println(s"Created Kong container")

    "docker start cassandra".!
    println(s"Started Cassandra container")
    waitForCassandraToStart()

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
        "docker kill cassandra".!!
        println("Killed Cassandra container")
        Thread.sleep(2000L)
      } recover {
        case e => println(s"Failed to kill Cassandra container. Exception: $e}")
      }

      Try {
        "docker rm kong".!!
        println("Removed Kong container")
      } recover {
        case e => println(s"Failed to remove Kong container. Exception: $e}")
      }

      Try {
        "docker rm cassandra".!!
        println("Removed Cassandra container")
      } recover {
        case e => println(s"Failed to remove Cassandra container. Exception: $e}")
      }
    }
  }

}
