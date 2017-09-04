package integration.fixtures

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.TableStatus
import org.scalatest.{ BeforeAndAfterAll, Suite }
import util.AWSConstants._

import scala.annotation.tailrec
import scala.sys.process._
import scala.util.Random

/** Starts an in-memory DynamoDB Local server in a separate process before the tests, and kills it after the tests. */
trait DynamoDbLocalServerFixture extends BeforeAndAfterAll { self: Suite =>

  private var dynamoServer: Process = _

  override def beforeAll(): Unit = {
    dynamoServer = "java -Djava.library.path=dynamodb-local/DynamoDBLocal_lib -jar dynamodb-local/DynamoDBLocal.jar -inMemory -port 8500".run()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    try super.afterAll()
    finally dynamoServer.destroy()
  }

}
