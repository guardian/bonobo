package integration.fixtures

import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.{ BeforeAndAfterAll, Suite }
import scala.collection.JavaConverters._

trait BonoboLabelsTableFixture extends DynamoDbClientFixture with BeforeAndAfterAll { this: Suite =>

  val labelsTableName = randomTableName("integration-test-bonobo-labels")

  override def beforeAll() {
    val attributeDefinitions: List[AttributeDefinition] = List(
      new AttributeDefinition().withAttributeName("id").withAttributeType("S"))
    val keySchema: List[KeySchemaElement] = new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH) :: Nil

    val createTableRequest: CreateTableRequest = new CreateTableRequest()
      .withTableName(labelsTableName)
      .withKeySchema(keySchema.asJava)
      .withAttributeDefinitions(attributeDefinitions.asJava)
      .withProvisionedThroughput(new ProvisionedThroughput()
        .withReadCapacityUnits(5L)
        .withWriteCapacityUnits(5L))

    println(s"Creating users table $labelsTableName")
    dynamoClient.createTable(createTableRequest)
    waitForTableToBecomeActive(labelsTableName)

    super.beforeAll()
  }

  override def afterAll() {
    try super.afterAll()
    finally {
      println(s"Deleting users table $labelsTableName")
      dynamoClient.deleteTable(labelsTableName)
    }
  }
}
