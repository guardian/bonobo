package integration.fixtures

import com.amazonaws.ClientConfiguration
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.TableStatus
import org.scalatest.{ Suite, BeforeAndAfterAll }
import util.AWSConstants._

import scala.annotation.tailrec
import scala.util.Random

trait DynamoDbClientFixture extends BeforeAndAfterAll { self: Suite =>

  val dynamoClient: AmazonDynamoDB = {
    val clientBuilder = AmazonDynamoDBClientBuilder.standard()
    val config = new ClientConfiguration()
    config.setMaxErrorRetry(20)
    val endpoint = new EndpointConfiguration("http://localhost:8500", "eu-west-1")
    clientBuilder
      .withCredentials(CredentialsProvider)
      .withClientConfiguration(config)
      .withEndpointConfiguration(endpoint)
      .build()
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
