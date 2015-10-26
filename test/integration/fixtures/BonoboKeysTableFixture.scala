package integration.fixtures

import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.{BeforeAndAfterAll, Suite}
import scala.collection.JavaConverters._

trait BonoboKeysTableFixture extends DynamoDbFixture with BeforeAndAfterAll { this: Suite =>

  val keysTableName = "Integration-test-bonobo-keys"

  override def beforeAll() {
    val attributeDefinitions: List[AttributeDefinition] = new AttributeDefinition()
      .withAttributeName("hashkey").withAttributeType("S")
      .withAttributeName("createdAt").withAttributeType("S") :: Nil

    val keySchema: List[KeySchemaElement] = new KeySchemaElement()
      .withAttributeName("hashkey").withKeyType(KeyType.HASH)
      .withAttributeName("createdAt").withKeyType(KeyType.RANGE):: Nil

    val createTableRequest: CreateTableRequest = new CreateTableRequest()
      .withTableName(keysTableName)
      .withKeySchema(keySchema.asJava)
      .withAttributeDefinitions(attributeDefinitions.asJava)
      .withProvisionedThroughput(new ProvisionedThroughput()
      .withReadCapacityUnits(1L)
      .withWriteCapacityUnits(1L))

    client.createTable(createTableRequest)

    super.beforeAll()
  }

  override def afterAll() {
    try super.afterAll()
    finally {
      client.deleteTable(keysTableName)
    }
  }
}
