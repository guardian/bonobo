package store

import com.amazonaws.services.dynamodbv2.document.spec.{ QuerySpec, ScanSpec }
import com.amazonaws.services.dynamodbv2.document.utils.{ NameMap, ValueMap }
import com.amazonaws.services.dynamodbv2.document._
import models.Tier.Developer
import models._
import org.joda.time.DateTime
import play.api.Logger

import scala.collection.JavaConverters._

trait DB {
  def search(query: String, limit: Int = 20): List[BonoboInfo]

  def saveUser(bonoboKey: BonoboUser): Unit

  def updateUser(bonoboUser: BonoboUser): Unit

  def getUserWithId(id: String): Option[BonoboUser]

  def getUserWithEmail(email: String): Option[BonoboUser]

  def saveKey(kongKey: KongKey): Unit

  def updateKey(kongKey: KongKey): Unit

  def getKeys(direction: String, range: Option[String], limit: Int = 20): ResultsPage[BonoboInfo]

  def getKeyWithValue(key: String): Option[KongKey]

  def getKeysWithUserId(id: String): List[KongKey]

  def getNumberOfKeys(): Long
}

class Dynamo(db: DynamoDB, usersTable: String, keysTable: String) extends DB {
  import Dynamo._

  private val BonoboTable = db.getTable(usersTable)
  private val KongTable = db.getTable(keysTable)

  def search(query: String, limit: Int = 20): List[BonoboInfo] = {
    val keysScan = new ScanSpec()
      .withFilterExpression("contains (#1, :s)")
      .withNameMap(new NameMap()
        .`with`("#1", "keyValue")
      )
      .withValueMap(new ValueMap().withString(":s", query))
      .withMaxResultSize(limit)
    val keys = KongTable.scan(keysScan).asScala.toList.map(fromKongItem)
    val usersForKeysSearch = getUsersForKeys(keys)
    val resultForKeysSearch = matchKeysWithUsers(keys, usersForKeysSearch)
    Logger.info(s"DynamoDB: Searching '${query}' found ${resultForKeysSearch.length} matching key(s)")

    val userScan = new ScanSpec()
      .withFilterExpression("contains (#1, :s) OR contains (#2, :s) OR contains (#3, :s) OR contains (#4, :s)")
      .withNameMap(new NameMap()
        .`with`("#1", "email")
        .`with`("#2", "name")
        .`with`("#3", "companyName")
        .`with`("#4", "productName")
      )
      .withValueMap(new ValueMap().withString(":s", query))
      .withMaxResultSize(limit)
    val users = BonoboTable.scan(userScan).asScala.toList.map(fromBonoboItem)
    val keysForUsersSearch = getKeysForUsers(users)
    val resultForUsersSearch = matchKeysWithUsers(keysForUsersSearch, users)
    Logger.info(s"DynamoDB: Searching '${query}' found ${resultForUsersSearch.length} matching user(s)")

    (resultForKeysSearch ++ resultForUsersSearch).distinct.sortBy(_.kongKey.createdAt.getMillis).reverse
  }

  def saveUser(bonoboUser: BonoboUser): Unit = {
    val item = toBonoboItem(bonoboUser)
    BonoboTable.putItem(item)
    Logger.info(s"DynamoDB: User ${bonoboUser.name} has been saved with the id ${bonoboUser.bonoboId}")
  }

  def updateUser(bonoboUser: BonoboUser): Unit = {
    BonoboTable.updateItem(new PrimaryKey("id", bonoboUser.bonoboId),
      new AttributeUpdate("email").put(bonoboUser.email),
      new AttributeUpdate("name").put(bonoboUser.name),
      new AttributeUpdate("companyName").put(bonoboUser.companyName),
      new AttributeUpdate("companyName").put(bonoboUser.companyUrl)
    )
    Logger.info(s"DynamoDB: User ${bonoboUser.bonoboId} has been updated")
  }

  def getUserWithId(id: String): Option[BonoboUser] = {
    val userQuery = new QuerySpec()
      .withKeyConditionExpression("id = :i")
      .withValueMap(new ValueMap().withString(":i", id))
      .withMaxResultSize(1)
    BonoboTable.query(userQuery).asScala.toList.map(fromBonoboItem).headOption
  }

  def getUserWithEmail(email: String): Option[BonoboUser] = {
    val userScan = new ScanSpec()
      .withFilterExpression("email = :e")
      .withValueMap(new ValueMap().withString(":e", email))
      .withMaxResultSize(1)
    BonoboTable.scan(userScan).asScala.toList.map(fromBonoboItem).headOption
  }

  def saveKey(kongKey: KongKey): Unit = {
    val item = toKongItem(kongKey)
    KongTable.putItem(item)
    Logger.info(s"DynamoDB: Key ${kongKey.key} has been saved for the user with id ${kongKey.bonoboId}")
  }

  def updateKey(kongKey: KongKey): Unit = {
    KongTable.updateItem(new PrimaryKey("hashkey", "hashkey", "rangekey", kongKey.rangeKey),
      new AttributeUpdate("productName").put(kongKey.productName),
      new AttributeUpdate("productUrl").put(kongKey.productUrl),
      new AttributeUpdate("requests_per_day").put(kongKey.requestsPerDay),
      new AttributeUpdate("requests_per_minute").put(kongKey.requestsPerMinute),
      new AttributeUpdate("status").put(kongKey.status),
      new AttributeUpdate("tier").put(kongKey.tier.toString)
    )
    Logger.info(s"DynamoDB: Key ${kongKey.key} has been updated")
  }

  def getKeys(direction: String, range: Option[String], limit: Int = 20): ResultsPage[BonoboInfo] = {
    direction match {
      case "previous" => getKeysBefore(range, limit)
      case "next" => getKeysAfter(range, limit)
      case _ => ResultsPage(List.empty, hasNext = false)
    }
  }

  def getKeyWithValue(keyValue: String): Option[KongKey] = {
    val query = new QuerySpec()
      .withConsistentRead(true)
      .withKeyConditionExpression("hashkey = :h")
      .withFilterExpression("keyValue = :k")
      .withValueMap(new ValueMap().withString(":h", "hashkey").withString(":k", keyValue))
    val result = KongTable.query(query)
    result.asScala.toList.map(fromKongItem).headOption
  }

  def getKeysWithUserId(bonoboId: String): List[KongKey] = {
    val keyQuery = new QuerySpec()
      .withKeyConditionExpression("hashkey = :h")
      .withFilterExpression("bonoboId = :i")
      .withValueMap(new ValueMap().withString(":i", bonoboId).withString(":h", "hashkey"))
    KongTable.query(keyQuery).asScala.toList.map(fromKongItem)
  }

  def getNumberOfKeys(): Long = KongTable.describe().getItemCount

  private def getUsersForKeys(keys: List[KongKey]): List[BonoboUser] = {
    keys.flatMap {
      kongKey => getUserWithId(kongKey.bonoboId)
    }
  }

  private def getKeysForUsers(users: List[BonoboUser]): List[KongKey] = {
    users.flatMap {
      user => getKeysWithUserId(user.bonoboId)
    }
  }

  private def matchKeysWithUsers(keys: List[KongKey], users: List[BonoboUser]): List[BonoboInfo] = {
    keys.flatMap { key =>
      val bonoboId = key.bonoboId
      val maybeUser = users.find(_.bonoboId == bonoboId)
      maybeUser map { BonoboInfo(key, _) } orElse {
        Logger.warn(s"DynamoDB: There is no user associated with the key ${key.key}")
        None
      }
    }
  }

  private def getKeysAfter(afterRange: Option[String], limit: Int): ResultsPage[BonoboInfo] = {
    def createQuerySpec(range: Option[String]): QuerySpec = {
      val querySpec = new QuerySpec()
        .withKeyConditionExpression("hashkey = :h")
        .withValueMap(new ValueMap().withString(":h", "hashkey"))
        .withMaxResultSize(limit)
        .withScanIndexForward(false)
      range.fold(querySpec) { value => querySpec.withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "rangekey", value)) }
    }
    val keysQuery = createQuerySpec(afterRange)
    val keys: List[KongKey] = KongTable.query(keysQuery).asScala.toList.map(fromKongItem)

    if (keys.isEmpty) ResultsPage(List.empty, hasNext = false)
    else {
      val users = getUsersForKeys(keys)
      val result = matchKeysWithUsers(keys, users)

      val testQuery = createQuerySpec(Some(keys.last.rangeKey)) //TODO: improve query using COUNT
      val nextPageSize = KongTable.query(testQuery).asScala.size
      Logger.info(s"DynamoDb: This page is showing the ${result.length} results after range $afterRange. The next page has $nextPageSize results.")
      nextPageSize match {
        case 0 => ResultsPage(result, hasNext = false)
        case _ => ResultsPage(result, hasNext = true)
      }
    }
  }

  private def getKeysBefore(beforeRange: Option[String], limit: Int): ResultsPage[BonoboInfo] = {
    def createQuerySpec(range: Option[String]): QuerySpec = {
      val querySpec = new QuerySpec()
        .withKeyConditionExpression("hashkey = :h")
        .withValueMap(new ValueMap().withString(":h", "hashkey"))
        .withMaxResultSize(limit)
      range.fold(querySpec) { value => querySpec.withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "rangekey", value)) }
    }
    val keysQuery = createQuerySpec(beforeRange)
    val keys = KongTable.query(keysQuery).asScala.toList.map(fromKongItem).reverse
    val users = getUsersForKeys(keys)
    val result = matchKeysWithUsers(keys, users)

    val testQuery = createQuerySpec(Some(keys.head.rangeKey)) //TODO: improve query using COUNT
    val nextPageSize = KongTable.query(testQuery).asScala.size
    Logger.info(s"DynamoDb: This page is showing the ${result.length} results before range $beforeRange. The previous page has $nextPageSize results.")
    nextPageSize match {
      case 0 => ResultsPage(result, hasNext = false)
      case _ => ResultsPage(result, hasNext = true)
    }
  }
}

object Dynamo {

  def toBonoboItem(bonoboKey: BonoboUser): Item = {
    val item = new Item()
      .withPrimaryKey("id", bonoboKey.bonoboId)
      .withString("name", bonoboKey.name)
      .withString("email", bonoboKey.email)
      .withString("companyName", bonoboKey.companyName)
      .withString("companyUrl", bonoboKey.companyUrl)
      .withLong("createdAt", bonoboKey.additionalInfo.createdAt.getMillis)
      .withString("registrationType", bonoboKey.additionalInfo.registrationType.friendlyName)

    bonoboKey.additionalInfo.businessArea.fold(item) { businessArea => item.withString("businessArea", businessArea) }
    bonoboKey.additionalInfo.monthlyUsers.fold(item) { monthlyUsers => item.withString("monthlyUsers", monthlyUsers) }
    bonoboKey.additionalInfo.commercialModel.fold(item) { commercialModel => item.withString("commercialModel", commercialModel) }
    bonoboKey.additionalInfo.content.fold(item) { content => item.withString("content", content) }
    bonoboKey.additionalInfo.contentFormat.fold(item) { contentFormat => item.withString("contentFormat", contentFormat) }
    bonoboKey.additionalInfo.articlesPerDay.fold(item) { articlesPerDay => item.withString("articlesPerDay", articlesPerDay) }
  }

  def fromBonoboItem(item: Item): BonoboUser = {
    def toRegistrationType(registrationType: String): RegistrationType = {
      RegistrationType.withName(registrationType).getOrElse {
        Logger.warn(s"Invalid registration type in DynamoDB: $registrationType")
        ManualRegistration
      }
    }
    BonoboUser(
      bonoboId = item.getString("id"),
      name = item.getString("name"),
      email = item.getString("email"),
      companyName = item.getString("companyName"),
      companyUrl = item.getString("companyUrl"),
      additionalInfo = AdditionalUserInfo(createdAt = new DateTime(item.getString("createdAt").toLong),
        registrationType = toRegistrationType(item.getString("registrationType")),
        businessArea = Option(item.getString("businessArea")),
        monthlyUsers = Option(item.getString("monthlyUsers")),
        commercialModel = Option(item.getString("commercialModel")),
        content = Option(item.getString("content")),
        contentFormat = Option(item.getString("contentFormat")),
        articlesPerDay = Option(item.getString("articlesPerDay")))
    )
  }

  def toKongItem(kongKey: KongKey): Item = {
    new Item()
      .withPrimaryKey("hashkey", "hashkey", "rangekey", kongKey.rangeKey)
      .withString("bonoboId", kongKey.bonoboId)
      .withString("kongId", kongKey.kongId)
      .withString("keyValue", kongKey.key)
      .withInt("requests_per_day", kongKey.requestsPerDay)
      .withInt("requests_per_minute", kongKey.requestsPerMinute)
      .withString("status", kongKey.status)
      .withString("tier", kongKey.tier.toString)
      .withLong("createdAt", kongKey.createdAt.getMillis)
      .withString("productName", kongKey.productName)
      .withString("productUrl", kongKey.productUrl)
  }

  def fromKongItem(item: Item): KongKey = {
    def toTier(tier: String): Tier = {
      Tier.withNameOption(tier).getOrElse {
        Logger.warn(s"Invalid tier in DynamoDB: $tier")
        Developer
      }
    }
    KongKey(
      bonoboId = item.getString("bonoboId"),
      kongId = item.getString("kongId"),
      key = item.getString("keyValue"),
      requestsPerDay = item.getInt("requests_per_day"),
      requestsPerMinute = item.getInt("requests_per_minute"),
      status = item.getString("status"),
      tier = toTier(item.getString("tier")),
      createdAt = new DateTime(item.getString("createdAt").toLong),
      productName = item.getString("productName"),
      productUrl = item.getString("productUrl"),
      rangeKey = item.getString("rangekey")
    )
  }
}
