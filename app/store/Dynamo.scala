package store

import com.amazonaws.services.dynamodbv2.document.spec.{ QuerySpec, ScanSpec }
import com.amazonaws.services.dynamodbv2.document.utils.{ NameMap, ValueMap }
import com.amazonaws.services.dynamodbv2.document._
import models._
import org.joda.time.DateTime

import scala.collection.JavaConverters._

trait DB {

  val limit = 4 // items per page to be displayed

  def search(query: String, limit: Int = 20): List[BonoboInfo]

  def saveBonoboUser(bonoboKey: BonoboUser): Unit

  def saveKongKey(kongKey: KongKey): Unit

  def getKeys(direction: String, range: String): (List[BonoboInfo], Boolean)

  def getNumberOfKeys: Long

  def retrieveKey(id: String): KongKey

  def retrieveUser(id: String): BonoboUser

  def updateBonoboUser(bonoboUser: BonoboUser): Unit

  def updateKongKey(kongKey: KongKey): Unit

  def deleteKongKey(createdAt: String): Unit
}

class Dynamo(db: DynamoDB, usersTable: String, keysTable: String) extends DB {

  import Dynamo._

  private val BonoboTable = db.getTable(usersTable)
  private val KongTable = db.getTable(keysTable)

  def getKeys(direction: String, range: String): (List[BonoboInfo], Boolean) = {
    direction match {
      case "previous" => getKeysBefore(range)
      case "next" => getKeysAfter(range)
      case _ => (List.empty, false)
    }
  }

  private def getUserWithId(id: String): BonoboUser = {
    val userQuery = new QuerySpec()
      .withKeyConditionExpression(":i = id")
      .withValueMap(new ValueMap().withString(":i", id))
      .withMaxResultSize(1)
    BonoboTable.query(userQuery).asScala.toList.map(fromItem).head
  }

  private def getKeyWithId(id: String): KongKey = {
    val keyQuery = new QuerySpec()
      .withKeyConditionExpression(":h = hashkey")
      .withFilterExpression(":i = bonoboId")
      .withValueMap(new ValueMap().withString(":i", id).withString(":h", "hashkey"))
      .withMaxResultSize(1)
      .withScanIndexForward(false)
    KongTable.query(keyQuery).asScala.toList.map(fromKongItem).head
  }

  private def getUsersForKeys(keys: List[KongKey]): List[BonoboUser] = {
    keys.map {
      kongKey => getUserWithId(kongKey.bonoboId)
    }
  }

  private def getKeysForUsers(users: List[BonoboUser]): List[KongKey] = {
    users.map {
      user => getKeyWithId(user.bonoboId)
    }
  }

  private def matchKeysWithUsers(keys: List[KongKey], users: List[BonoboUser]): List[BonoboInfo] = {
    keys.flatMap { key =>
      val bonoboId = key.bonoboId
      val maybeUser = users.find(_.bonoboId == bonoboId)
      maybeUser map { BonoboInfo(key, _) } orElse None //TODO: Log that user doesn't exist
    }
  }

  private def getKeysAfter(afterRange: String): (List[BonoboInfo], Boolean) = {
    def createQuerySpec(range: String): QuerySpec = {
      range match {
        case "" => new QuerySpec()
          .withKeyConditionExpression(":h = hashkey")
          .withValueMap(new ValueMap().withString(":h", "hashkey"))
          .withMaxResultSize(limit)
          .withScanIndexForward(false)
        case other => new QuerySpec()
          .withKeyConditionExpression(":h = hashkey")
          .withValueMap(new ValueMap().withString(":h", "hashkey"))
          .withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "createdAt", other))
          .withMaxResultSize(limit)
          .withScanIndexForward(false)
      }
    }
    val keysQuery = createQuerySpec(afterRange)
    val keys: List[KongKey] = KongTable.query(keysQuery).asScala.toList.map(fromKongItem)

    if (keys.length == 0) (List.empty, false)
    else {
      val users = getUsersForKeys(keys)
      val result = matchKeysWithUsers(keys, users)

      val testQuery = createQuerySpec(keys.last.createdAt.toString) //TODO: improve query using COUNT
      KongTable.query(testQuery).asScala.size match {
        case 0 => (result, false)
        case _ => (result, true)
      }
    }
  }

  private def getKeysBefore(beforeRange: String): (List[BonoboInfo], Boolean) = {
    def createQuerySpec(range: String): QuerySpec = {
      new QuerySpec()
        .withKeyConditionExpression(":h = hashkey")
        .withValueMap(new ValueMap().withString(":h", "hashkey"))
        .withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "createdAt", range))
        .withMaxResultSize(limit)
    }
    val keysQuery = createQuerySpec(beforeRange)
    val keys = KongTable.query(keysQuery).asScala.toList.map(fromKongItem).reverse
    val users = getUsersForKeys(keys)
    val result = matchKeysWithUsers(keys, users)

    val testQuery = createQuerySpec(keys.head.createdAt.toString) //TODO: improve query using COUNT
    KongTable.query(testQuery).asScala.size match {
      case 0 => (result, false)
      case _ => (result, true)
    }
  }

  def getNumberOfKeys: Long = KongTable.describe().getItemCount

  def search(query: String, limit: Int = 20): List[BonoboInfo] = {
    val keysScan = new ScanSpec()
      .withFilterExpression("#1 = :s")
      .withNameMap(new NameMap()
        .`with`("#1", "key")
      )
      .withValueMap(new ValueMap().withString(":s", query))
      .withMaxResultSize(limit)
    val keys = KongTable.scan(keysScan).asScala.toList.map(fromKongItem)
    val usersForKeysSearch = getUsersForKeys(keys)
    val resultForKeysSearch = matchKeysWithUsers(keys, usersForKeysSearch)

    val userScan = new ScanSpec()
      .withFilterExpression("#1 = :s OR #2 = :s OR #3 = :s OR #4 = :s")
      .withNameMap(new NameMap()
        .`with`("#1", "email")
        .`with`("#2", "name")
        .`with`("#3", "company")
        .`with`("#4", "url")
      )
      .withValueMap(new ValueMap().withString(":s", query))
      .withMaxResultSize(limit)
    val users = BonoboTable.scan(userScan).asScala.toList.map(fromItem)
    val keysForUsersSearch = getKeysForUsers(users)
    val resultForUsersSearch = matchKeysWithUsers(keysForUsersSearch, users)

    resultForKeysSearch ++ resultForUsersSearch
  }

  def retrieveKey(id: String): KongKey = {
    val query = new QuerySpec()
      .withKeyConditionExpression("hashkey = :h")
      .withFilterExpression("bonoboId = :i")
      .withValueMap(new ValueMap().withString(":i", id).withString(":h", "hashkey"))
    val item = KongTable.query(query).asScala.toList.head
    fromKongItem(item)
  }

  def retrieveUser(id: String): BonoboUser = {
    val query = new QuerySpec()
      .withKeyConditionExpression("id = :i")
      //.withFilterExpression("bonoboId = :i")
      .withValueMap(new ValueMap().withString(":i", id))
    val item = BonoboTable.query(query).asScala.toList.head
    fromBonoboItem(item)
  }

  def saveBonoboUser(bonoboUser: BonoboUser): Unit = {
    val item = toBonoboItem(bonoboUser)
    BonoboTable.putItem(item)
  }

  def updateBonoboUser(bonoboUser: BonoboUser): Unit = {
    BonoboTable.updateItem(new PrimaryKey("id", bonoboUser.bonoboId),
      new AttributeUpdate("email").put(bonoboUser.email),
      new AttributeUpdate("name").put(bonoboUser.name),
      new AttributeUpdate("company").put(bonoboUser.company),
      new AttributeUpdate("url").put(bonoboUser.url)
    )
  }

  def saveKongKey(kongKey: KongKey): Unit = {
    val item = toKongItem(kongKey)
    KongTable.putItem(item)
  }

  def updateKongKey(kongKey: KongKey): Unit = {
    KongTable.updateItem(new PrimaryKey("hashkey", "hashkey", "createdAt", kongKey.createdAt),
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

  def toBonoboItem(bonoboKey: BonoboUser): Item = {
    new Item()
      .withPrimaryKey("id", bonoboKey.bonoboId)
      .withString("name", bonoboKey.name)
      .withString("company", bonoboKey.company)
      .withString("email", bonoboKey.email)
      .withString("url", bonoboKey.url)
  }

  def fromBonoboItem(item: Item): BonoboUser = {
    BonoboUser(
      bonoboId = item.getString("id"),
      name = item.getString("name"),
      company = item.getString("company"),
      email = item.getString("email"),
      url = item.getString("url")
    )
  }

  def toKongItem(kongKey: KongKey): Item = {
    new Item()
      .withPrimaryKey("hashkey", "hashkey")
      .withString("bonoboId", kongKey.bonoboId)
      .withString("key", kongKey.key)
      .withInt("requests_per_day", kongKey.requestsPerDay)
      .withInt("requests_per_minute", kongKey.requestsPerMinute)
      .withString("status", kongKey.status)
      .withString("tier", kongKey.tier)
      .withString("createdAt", kongKey.createdAt.toString)
  }

  def fromKongItem(item: Item): KongKey = {
    KongKey(
      bonoboId = item.getString("bonoboId"),
      key = item.getString("key"),
      requestsPerDay = item.getInt("requests_per_day"),
      requestsPerMinute = item.getInt("requests_per_minute"),
      status = item.getString("status"),
      tier = item.getString("tier"),
      createdAt = new DateTime(item.getString("createdAt"))
    )
  }
}
