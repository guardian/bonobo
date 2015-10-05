package store

import com.amazonaws.services.dynamodbv2.document.spec.{ QuerySpec, ScanSpec }
import com.amazonaws.services.dynamodbv2.document.utils.{ NameMap, ValueMap }
import com.amazonaws.services.dynamodbv2.document._
import models._

import scala.collection.JavaConverters._

trait DB {

  val limit = 4

  def search(query: String, limit: Int = 20): List[BonoboKey]

  def save(bonoboKey: BonoboKey): Unit

  def getKeys(direction: String, range: String): (List[BonoboKey], Boolean)

  def retrieveKey(id: String): BonoboKey

}

class Dynamo(db: DynamoDB, tableName: String) extends DB {

  import Dynamo._

  private val bonoboTable = db.getTable(tableName)

  def search(query: String, limit: Int = 20): List[BonoboKey] = {
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
    bonoboTable.scan(scan).asScala.toList.map(fromItem)
  }

  def getKeys(direction: String, range: String): (List[BonoboKey], Boolean) = {
    direction match {
      case "previous" => getKeysBefore(range)
      case "next" => getKeysAfter(range)
      case _ => (List.empty, false)
    }
  }

  private def getKeysAfter(afterRange: String): (List[BonoboKey], Boolean) = {
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
    val result = bonoboTable.query(query).asScala.toList.map(fromItem)
    val testQuery = createQuerySpec(result.last.createdAt) //TODO: improve query using COUNT
    val testResult = bonoboTable.query(testQuery).asScala.toList
    testResult.length match {
      case 0 => (result, false)
      case _ => (result, true)
    }
  }

  private def getKeysBefore(beforeRange: String): (List[BonoboKey], Boolean) = {
    def createQuerySpec(range: String): QuerySpec = {
      new QuerySpec()
        .withKeyConditionExpression(":h = hashkey")
        .withValueMap(new ValueMap().withString(":h", "hashkey"))
        .withScanIndexForward(false)
        .withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "createdAt", range))
        .withMaxResultSize(limit)
    }
    val query = createQuerySpec(beforeRange)
    val result = bonoboTable.query(query).asScala.toList.map(fromItem).reverse
    val testQuery = createQuerySpec(result.head.createdAt) //TODO: improve query using COUNT
    val testResult = bonoboTable.query(testQuery).asScala.toList.reverse
    testResult.length match {
      case 0 => (result, false)
      case _ => (result, true)
    }
  }

  def retrieveKey(id: String): BonoboKey = {
    val query = new QuerySpec()
      .withKeyConditionExpression("hashkey = :h")
      .withFilterExpression("id = :i")
      .withValueMap(new ValueMap().withString(":i", id).withString(":h", "hashkey"))
    val item = bonoboTable.query(query).asScala.toList.head
    fromItem(item)
  }

  def save(bonoboKey: BonoboKey): Unit = {
    val item = toItem(bonoboKey)
    bonoboTable.putItem(item)
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
}
