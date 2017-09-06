package integration.fixtures

import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.{ BeforeAndAfterAll, Suite }
import scala.collection.JavaConverters._

trait BonoboUserTableFixture extends DynamoDbClientFixture with BeforeAndAfterAll { this: Suite =>

  val usersTableName = randomTableName("integration-test-bonobo-users")

  override def beforeAll() {
    val attributeDefinitions: List[AttributeDefinition] = List(
      new AttributeDefinition().withAttributeName("id").withAttributeType("S"),
      new AttributeDefinition().withAttributeName("email").withAttributeType("S"))
    val keySchema: List[KeySchemaElement] = new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH) :: Nil
    val indexKeySchema: List[KeySchemaElement] = new KeySchemaElement().withAttributeName("email").withKeyType(KeyType.HASH) :: Nil

    val createTableRequest: CreateTableRequest = new CreateTableRequest()
      .withTableName(usersTableName)
      .withKeySchema(keySchema.asJava)
      .withAttributeDefinitions(attributeDefinitions.asJava)
      .withProvisionedThroughput(new ProvisionedThroughput()
        .withReadCapacityUnits(5L)
        .withWriteCapacityUnits(5L))
      .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
        .withIndexName("email-index")
        .withKeySchema(indexKeySchema.asJava)
        .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY))
        .withProvisionedThroughput(new ProvisionedThroughput()
          .withReadCapacityUnits(5L)
          .withWriteCapacityUnits(5L)))

    println(s"Creating users table $usersTableName")
    dynamoClient.createTable(createTableRequest)
    waitForTableToBecomeActive(usersTableName)

    super.beforeAll()
  }

  override def afterAll() {
    try super.afterAll()
    finally {
      println(s"Deleting users table $usersTableName")
      dynamoClient.deleteTable(usersTableName)
    }
  }
}
