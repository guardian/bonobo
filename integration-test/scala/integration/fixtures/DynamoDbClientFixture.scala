package integration.fixtures

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.TableStatus
import org.scalatest.{ Suite, BeforeAndAfterAll }
import util.AWSConstants._

import scala.annotation.tailrec
import scala.util.Random

trait DynamoDbClientFixture extends BeforeAndAfterAll { self: Suite =>

  val dynamoClient: AmazonDynamoDBClient = {
    val config = new ClientConfiguration()
    config.setMaxErrorRetry(20)
    new AmazonDynamoDBClient(CredentialsProvider, config).withEndpoint("http://localhost:8500")
  }

  def randomTableName(prefix: String): String = s"$prefix-${Random.alphanumeric.take(10).mkString}"

  @tailrec
  final def waitForTableToBecomeActive(tableName: String): Unit = {
    // Seriously, polling is the only way to do this :(
    Option(dynamoClient.describeTable(tableName).getTable) match {
      case Some(desc) if desc.getTableStatus == TableStatus.ACTIVE.toString => ()
      case _ =>
        println(s"Waiting for $tableName to become ACTIVE ...")
        Thread.sleep(1000L)
        waitForTableToBecomeActive(tableName)
    }
  }

}
