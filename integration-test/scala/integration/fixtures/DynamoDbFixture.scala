package integration.fixtures

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.TableStatus
import util.AWSConstants._

import scala.annotation.tailrec
import scala.util.Random

trait DynamoDbFixture {

  //val dynamoClient: AmazonDynamoDBClient = new AmazonDynamoDBClient(CredentialsProvider).withRegion(Regions.fromName("eu-west-1"))
  val dynamoClient: AmazonDynamoDBClient = new AmazonDynamoDBClient(CredentialsProvider).withEndpoint("http://localhost:8000")

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
