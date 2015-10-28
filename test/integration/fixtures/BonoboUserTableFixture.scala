package integration.fixtures

import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.{ BeforeAndAfterAll, Suite }
import scala.collection.JavaConverters._

trait BonoboUserTableFixture extends DynamoDbFixture with BeforeAndAfterAll { this: Suite =>

  val usersTableName = randomTableName("Integration-test-bonobo-users")

  override def beforeAll() {
    val attributeDefinitions: List[AttributeDefinition] = new AttributeDefinition().withAttributeName("id").withAttributeType("S") :: Nil
    val keySchema: List[KeySchemaElement] = new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH) :: Nil

    val createTableRequest: CreateTableRequest = new CreateTableRequest()
      .withTableName(usersTableName)
      .withKeySchema(keySchema.asJava)
      .withAttributeDefinitions(attributeDefinitions.asJava)
      .withProvisionedThroughput(new ProvisionedThroughput()
        .withReadCapacityUnits(5L)
        .withWriteCapacityUnits(5L))

    println(s"Creating users table $usersTableName")
    client.createTable(createTableRequest)
    waitForTableToBecomeActive(usersTableName)

    super.beforeAll()
  }

  override def afterAll() {
    try super.afterAll()
    finally {
      println(s"Deleting users table $usersTableName")
      client.deleteTable(usersTableName)
    }
  }
}
