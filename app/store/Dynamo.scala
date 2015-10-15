package store

import com.amazonaws.services.dynamodbv2.document.spec.{ QuerySpec, ScanSpec, UpdateItemSpec }
import com.amazonaws.services.dynamodbv2.document.utils.{ NameMap, ValueMap }
import com.amazonaws.services.dynamodbv2.document._
import models._

import scala.collection.JavaConverters._

trait DB {

  val limit = 4

  def search(query: String, limit: Int = 20): List[KongKey]

  def saveOnBonobo(bonoboKey: BonoboKey): Unit

  def saveOnKong(kongKey: KongKey): Unit

  def getKeys(direction: String, range: String): (List[KongKey], Boolean)

  def retrieveKey(id: String): KongKey

  def updateKongKey(kongKey: KongKey): Unit

  def deleteKongKey(createdAt: String): Unit
}

class Dynamo(db: DynamoDB, bonoboBonoboTable: String, bonoboKongTable: String) extends DB {

  import Dynamo._

  private val BonoboTable = db.getTable(bonoboBonoboTable)
  private val KongTable = db.getTable(bonoboKongTable)

  def search(query: String, limit: Int = 20): List[KongKey] = {
    val scan = new ScanSpec()
      .withFilterExpression("#1 = :s OR #2 = :s OR #3 = :s OR #4 = :s OR #5 = :s")
      .withNameMap(new NameMap()
        .`with`("#1", "key")
        .`with`("#2", "email")
        .`with`("#3", "name")
        .`with`("#4", "company")
        .`with`("#5", "url")
      )
      .withValueMap(new ValueMap().withString(":s", query))
      .withMaxResultSize(limit)
    KongTable.scan(scan).asScala.toList.map(fromKongItem)
  }

  def getKeys(direction: String, range: String): (List[KongKey], Boolean) = {
    direction match {
      case "previous" => getKeysBefore(range)
      case "next" => getKeysAfter(range)
      case _ => (List.empty, false)
    }
  }

  private def getKeysAfter(afterRange: String): (List[KongKey], Boolean) = {
    def createQuerySpec(range: String): QuerySpec = {
      range match {
        case "" => new QuerySpec()
          .withKeyConditionExpression(":h = hashkey")
          .withValueMap(new ValueMap().withString(":h", "hashkey"))
          .withMaxResultSize(limit)
        case other => new QuerySpec()
          .withKeyConditionExpression(":h = hashkey")
          .withValueMap(new ValueMap().withString(":h", "hashkey"))
          .withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "createdAt", other))
          .withMaxResultSize(limit)
      }
    }
    val query = createQuerySpec(afterRange)
    val result = KongTable.query(query).asScala.toList.map(fromKongItem)
    if (result.length == 0) (result, false)
    else {
      val testQuery = createQuerySpec(result.last.createdAt) //TODO: improve query using COUNT
      val testResult = KongTable.query(testQuery).asScala.toList
      testResult.length match {
        case 0 => (result, false)
        case _ => (result, true)
      }
    }
  }

  private def getKeysBefore(beforeRange: String): (List[KongKey], Boolean) = {
    def createQuerySpec(range: String): QuerySpec = {
      new QuerySpec()
        .withKeyConditionExpression(":h = hashkey")
        .withValueMap(new ValueMap().withString(":h", "hashkey"))
        .withScanIndexForward(false)
        .withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "createdAt", range))
        .withMaxResultSize(limit)
    }
    val query = createQuerySpec(beforeRange)
    val result = KongTable.query(query).asScala.toList.map(fromKongItem).reverse
    val testQuery = createQuerySpec(result.head.createdAt) //TODO: improve query using COUNT
    val testResult = KongTable.query(testQuery).asScala.toList.reverse
    testResult.length match {
      case 0 => (result, false)
      case _ => (result, true)
    }
  }

  def retrieveKey(id: String): KongKey = {
    val query = new QuerySpec()
      .withKeyConditionExpression("hashkey = :h")
      .withFilterExpression("id = :i")
      .withValueMap(new ValueMap().withString(":i", id).withString(":h", "hashkey"))
    val item = KongTable.query(query).asScala.toList.head
    fromKongItem(item)
  }

  def saveOnBonobo(bonoboKey: BonoboKey): Unit = {
    val item = toItem(bonoboKey)
    BonoboTable.putItem(item)
  }

  // save stuff on the Kong table
  def saveOnKong(kongKey: KongKey): Unit = {
    val item = toKongItem(kongKey)
    KongTable.putItem(item)
  }

  def updateKongKey(kongKey: KongKey): Unit = {
    KongTable.updateItem(new PrimaryKey("hashkey", "hashkey", "createdAt", kongKey.createdAt),
      new AttributeUpdate("name").put(kongKey.name),
      new AttributeUpdate("requests_per_day").put(kongKey.requestsPerDay),
      new AttributeUpdate("requests_per_minute").put(kongKey.requestsPerMinute),
      new AttributeUpdate("status").put(kongKey.status),
      new AttributeUpdate("tier").put(kongKey.tier)
    )
  }

  def deleteKongKey(createdAt: String): Unit = {
    BonoboTable.deleteItem(new PrimaryKey("hashkey", "hashkey", "createdAt", createdAt))
  }
}

object Dynamo {

  def toItem(bonoboKey: BonoboKey): Item = {
    new Item()
      .withPrimaryKey("hashkey", "hashkey")
      .withString("id", bonoboKey.id)
      .withString("key", bonoboKey.key)
      .withString("name", bonoboKey.name)
      .withString("company", bonoboKey.company)
      .withString("email", bonoboKey.email)
      .withInt("requests_per_day", bonoboKey.requestsPerDay)
      .withInt("requests_per_minute", bonoboKey.requestsPerMinute)
      .withString("status", bonoboKey.status)
      .withString("tier", bonoboKey.tier)
      .withString("url", bonoboKey.url)
      .withString("createdAt", bonoboKey.createdAt)
  }

  def fromItem(item: Item): BonoboKey = {
    BonoboKey(
      id = item.getString("id"),
      key = item.getString("key"),
      name = item.getString("name"),
      company = item.getString("company"),
      email = item.getString("email"),
      requestsPerDay = item.getInt("requests_per_day"),
      requestsPerMinute = item.getInt("requests_per_minute"),
      status = item.getString("status"),
      tier = item.getString("tier"),
      url = item.getString("url"),
      createdAt = item.getString("createdAt")
    )
  }

  def toKongItem(kongKey: KongKey): Item = {
    new Item()
      .withPrimaryKey("hashkey", "hashkey")
      .withString("id", kongKey.id)
      .withString("key", kongKey.key)
      .withString("name", kongKey.name)
      .withInt("requests_per_day", kongKey.requestsPerDay)
      .withInt("requests_per_minute", kongKey.requestsPerMinute)
      .withString("status", kongKey.status)
      .withString("tier", kongKey.tier)
      .withString("createdAt", kongKey.createdAt)
  }

  def fromKongItem(item: Item): KongKey = {
    KongKey(
      id = item.getString("id"),
      key = item.getString("key"),
      name = item.getString("name"),
      requestsPerDay = item.getInt("requests_per_day"),
      requestsPerMinute = item.getInt("requests_per_minute"),
      status = item.getString("status"),
      tier = item.getString("tier"),
      createdAt = item.getString("createdAt")
    )
  }
}
