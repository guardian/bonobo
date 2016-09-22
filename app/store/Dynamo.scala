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

  def isEmailInUse(email: String): Boolean

  /**
   * Note: Only use this method if you actually need to look up the user.
   * If you only want to check if the email is registered, use [[isEmailInUse()]], which is much faster.
   */
  def getUserWithEmail(email: String): Option[BonoboUser]

  def saveKey(kongKey: KongKey, labelIds: List[String]): Unit

  def updateKey(kongKey: KongKey): Unit

  def getKeys(direction: String, range: Option[String], limit: Int = 20, filterLabels: Option[List[String]] = None): ResultsPage[BonoboInfo]

  def isKeyPresent(key: String): Boolean

  /**
   * Note: Only use this method if you actually need to look up the key.
   * If you only want to check if the key is already registered, use [[isKeyPresent()]], which is much faster.
   */
  def getKeyWithValue(key: String): Option[KongKey]

  def getKeysWithUserId(id: String): List[KongKey]

  def getNumberOfKeys(): Long

  /**
   * The following methods are used for labeling an user
   */
  def getLabels(): List[Label]

  def getLabelsFor(bonoboId: String): List[String]

  def getEmails(tier: String, status: String): List[String]
}

class Dynamo(db: DynamoDB, usersTable: String, keysTable: String, labelTable: String) extends DB {
  import Dynamo._

  private val BonoboTable = db.getTable(usersTable)
  private val KongTable = db.getTable(keysTable)
  private val LableTable = db.getTable(labelTable)

  def search(query: String, limit: Int = 20): List[BonoboInfo] = {
    val keysScan = new ScanSpec()
      .withFilterExpression("contains (#1, :s) OR contains (#2, :s)")
      .withNameMap(new NameMap()
        .`with`("#1", "keyValue")
        .`with`("#2", "productName")
      )
      .withValueMap(new ValueMap().withString(":s", query))
      .withMaxResultSize(limit)
    val keys = KongTable.scan(keysScan).asScala.toList.map(fromKongItem)
    val usersForKeysSearch = getUsersForKeys(keys)
    val resultForKeysSearch = matchKeysWithUsers(keys, usersForKeysSearch)
    Logger.info(s"DynamoDB: Searching '$query' found ${resultForKeysSearch.length} matching key(s)")

    val userScan = new ScanSpec()
      .withFilterExpression("contains (#1, :s) OR contains (#2, :s) OR contains (#3, :s)")
      .withNameMap(new NameMap()
        .`with`("#1", "email")
        .`with`("#2", "name")
        .`with`("#3", "companyName")
      )
      .withValueMap(new ValueMap().withString(":s", query))
      .withMaxResultSize(limit)
    val users = BonoboTable.scan(userScan).asScala.toList.map(fromBonoboItem)
    val keysForUsersSearch = getKeysForUsers(users)
    val resultForUsersSearch = matchKeysWithUsers(keysForUsersSearch, users)
    Logger.info(s"DynamoDB: Searching '$query' found ${resultForUsersSearch.length} matching user(s)")

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
      bonoboUser.companyUrl match {
        case Some(url) => new AttributeUpdate("companyUrl").put(url)
        case None => new AttributeUpdate("companyUrl").delete()
      },
      bonoboUser.companyName match {
        case Some(url) => new AttributeUpdate("companyName").put(url)
        case None => new AttributeUpdate("companyName").delete()
      },
      bonoboUser.labelIds match {
        case Nil => new AttributeUpdate("labelIds").delete()
        case ids: List[String] => new AttributeUpdate("labelIds").put(ids.asJava)
      }
    )
    val keys = getKeysWithUserId(bonoboUser.bonoboId)
    keys.foreach(kongKey => updateKeyLabelIds(kongKey, bonoboUser.labelIds))
    Logger.info(s"DynamoDB: User ${bonoboUser.bonoboId} has been updated")
  }

  private def updateKeyLabelIds(kongKey: KongKey, labelIds: List[String]): Unit = {
    KongTable.updateItem(new PrimaryKey("hashkey", "hashkey", "rangekey", kongKey.rangeKey),
      labelIds match {
        case Nil => new AttributeUpdate("labelIds").delete()
        case ids: List[String] => new AttributeUpdate("labelIds").put(ids.asJava)
      }
    )
  }

  def getUserWithId(id: String): Option[BonoboUser] = {
    val userQuery = new QuerySpec()
      .withKeyConditionExpression("id = :i")
      .withValueMap(new ValueMap().withString(":i", id))
      .withMaxResultSize(1)
    BonoboTable.query(userQuery).asScala.toList.map(fromBonoboItem).headOption
  }

  def isEmailInUse(email: String): Boolean = {
    val emailQuery = new QuerySpec()
      .withKeyConditionExpression("email = :e")
      .withValueMap(new ValueMap().withString(":e", email))
      .withMaxResultSize(1)
    BonoboTable.getIndex("email-index").query(emailQuery).iterator().hasNext
  }

  def getUserWithEmail(email: String): Option[BonoboUser] = {
    val userScan = new ScanSpec()
      .withFilterExpression("email = :e")
      .withValueMap(new ValueMap().withString(":e", email))
      .withMaxResultSize(1)
    BonoboTable.scan(userScan).asScala.toList.map(fromBonoboItem).headOption
  }

  def saveKey(kongKey: KongKey, labelIds: List[String]): Unit = {
    val item = toKongItem(kongKey, labelIds)
    KongTable.putItem(item)
    Logger.info(s"DynamoDB: Key ${kongKey.key} has been saved for the user with id ${kongKey.bonoboId}")
  }

  def updateKey(kongKey: KongKey): Unit = {
    KongTable.updateItem(new PrimaryKey("hashkey", "hashkey", "rangekey", kongKey.rangeKey),
      new AttributeUpdate("productName").put(kongKey.productName),
      new AttributeUpdate("requests_per_day").put(kongKey.requestsPerDay),
      new AttributeUpdate("requests_per_minute").put(kongKey.requestsPerMinute),
      new AttributeUpdate("status").put(kongKey.status),
      new AttributeUpdate("tier").put(kongKey.tier.toString),
      kongKey.productUrl match {
        case Some(url) => new AttributeUpdate("productUrl").put(url)
        case None => new AttributeUpdate("productUrl").delete()
      }
    )
    Logger.info(s"DynamoDB: Key ${kongKey.key} has been updated")
  }

  def getKeys(direction: String, range: Option[String], limit: Int = 20, filterLabels: Option[List[String]] = None): ResultsPage[BonoboInfo] = {
    direction match {
      case "previous" => getKeysBefore(range, limit, filterLabels)
      case "next" => getKeysAfter(range, limit, filterLabels)
      case _ => ResultsPage(List.empty, hasNext = false)
    }
  }

  def isKeyPresent(keyValue: String): Boolean = {
    val query = new QuerySpec()
      .withKeyConditionExpression("keyValue = :k")
      .withValueMap(new ValueMap().withString(":k", keyValue))
    KongTable.getIndex("keyValue-index").query(query).iterator().hasNext
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

  private def addLabelFiltersIfNecessary(querySpec: QuerySpec, filterLabels: Option[List[String]]): QuerySpec = {
    val initialValueMap = new ValueMap().withString(":h", "hashkey")
    if (filterLabels.isDefined) {
      val labelIds = filterLabels.get
      val placeholders = labelIds.zipWithIndex.map { case (id, i) => s"s$i" }
      val clauses = placeholders.map(p => s"contains(#1, :$p)")
      val filterExpression = clauses.reduce[String] { case (a, b) => s"$a OR $b" }
      val valueMapElements = placeholders.zip(labelIds)
      val valueMap = valueMapElements.foldLeft(initialValueMap) { (acc, elem) => acc.withString(s":${elem._1}", elem._2) }
      querySpec.withFilterExpression(filterExpression).withValueMap(valueMap).withNameMap(new NameMap().`with`("#1", "labelIds"))
    } else querySpec.withValueMap(initialValueMap)
  }

  private def getKeysAfter(afterRange: Option[String], limit: Int, filterLabels: Option[List[String]]): ResultsPage[BonoboInfo] = {
    def createQuerySpec(range: Option[String]): QuerySpec = {
      val querySpec = new QuerySpec()
        .withKeyConditionExpression("hashkey = :h")
        .withMaxResultSize(limit)
        .withScanIndexForward(false)
      range.fold(querySpec) { value => querySpec.withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "rangekey", value)) }

      addLabelFiltersIfNecessary(querySpec, filterLabels)
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

  private def getKeysBefore(beforeRange: Option[String], limit: Int, filterLabels: Option[List[String]]): ResultsPage[BonoboInfo] = {
    def createQuerySpec(range: Option[String]): QuerySpec = {
      val querySpec = new QuerySpec()
        .withKeyConditionExpression("hashkey = :h")
        .withValueMap(new ValueMap().withString(":h", "hashkey"))
        .withMaxResultSize(limit)
      range.fold(querySpec) { value => querySpec.withExclusiveStartKey(new PrimaryKey("hashkey", "hashkey", "rangekey", value)) }

      addLabelFiltersIfNecessary(querySpec, filterLabels)
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

  def getEmails(tier: String, status: String): List[String] = {
    val query = new ScanSpec()
      .withFilterExpression("#sts = :s AND tier = :t")
      .withNameMap(new NameMap()
        .`with`("#sts", "status")
      )
      .withValueMap(new ValueMap().withString(":s", status.capitalize).withString(":t", tier.capitalize))

    val userIds = KongTable.scan(query).asScala.toList.map(_.getString("bonoboId"))
    val allUsers = BonoboTable.scan(new ScanSpec).asScala.toList.map(fromBonoboItem)

    userIds.flatMap(id => allUsers.find(_.bonoboId == id)).map(_.email).distinct
  }

  /**
   * The following methods are used for labeling an user
   */
  def getLabels(): List[Label] = {
    LableTable.scan(new ScanSpec()).asScala.toList.map(toLabel)
  }

  def getLabelsFor(bonoboId: String): List[String] = {
    BonoboTable.query(new QuerySpec().withKeyConditionExpression("id = :i")
      .withValueMap(new ValueMap().withString(":i", bonoboId))
      .withMaxResultSize(1)).asScala.toList.map(fromBonoboItem).flatMap(_.labelIds)
  }

}

object Dynamo {

  def toBonoboItem(bonoboKey: BonoboUser): Item = {
    val item = new Item()
      .withPrimaryKey("id", bonoboKey.bonoboId)
      .withString("name", bonoboKey.name)
      .withString("email", bonoboKey.email)
      .withLong("createdAt", bonoboKey.additionalInfo.createdAt.getMillis)
      .withString("registrationType", bonoboKey.additionalInfo.registrationType.friendlyName)
      .withList("labelIds", bonoboKey.labelIds.asJava)

    bonoboKey.companyName.foreach(companyName => item.withString("companyName", companyName))
    bonoboKey.companyUrl.foreach(companyUrl => item.withString("companyUrl", companyUrl))

    bonoboKey.additionalInfo.businessArea.foreach(businessArea => item.withString("businessArea", businessArea))
    bonoboKey.additionalInfo.monthlyUsers.foreach(monthlyUsers => item.withString("monthlyUsers", monthlyUsers))
    bonoboKey.additionalInfo.commercialModel.foreach(commercialModel => item.withString("commercialModel", commercialModel))
    bonoboKey.additionalInfo.content.foreach(content => item.withString("content", content))
    bonoboKey.additionalInfo.contentFormat.foreach(contentFormat => item.withString("contentFormat", contentFormat))
    bonoboKey.additionalInfo.articlesPerDay.foreach(articlesPerDay => item.withString("articlesPerDay", articlesPerDay))

    item
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
      companyName = Option(item.getString("companyName")),
      companyUrl = Option(item.getString("companyUrl")),
      additionalInfo = AdditionalUserInfo(createdAt = new DateTime(item.getString("createdAt").toLong),
        registrationType = toRegistrationType(item.getString("registrationType")),
        businessArea = Option(item.getString("businessArea")),
        monthlyUsers = Option(item.getString("monthlyUsers")),
        commercialModel = Option(item.getString("commercialModel")),
        content = Option(item.getString("content")),
        contentFormat = Option(item.getString("contentFormat")),
        articlesPerDay = Option(item.getString("articlesPerDay"))),
      labelIds = Option(item.getList[String]("labelIds")) map (_.asScala.toList) getOrElse List.empty
    )
  }
  def toKongItem(kongKey: KongKey, labelIds: List[String]): Item = {
    val item = new Item()
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
      .withList("labelIds", labelIds.asJava)

    kongKey.productUrl.foreach(productUrl => item.withString("productUrl", productUrl))
    kongKey.kongConsumerId.foreach(kongConsumerId => item.withString("kongConsumerId", kongConsumerId))
    item
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
      kongConsumerId = Option(item.getString("kongConsumerId")),
      key = item.getString("keyValue"),
      requestsPerDay = item.getInt("requests_per_day"),
      requestsPerMinute = item.getInt("requests_per_minute"),
      status = item.getString("status"),
      tier = toTier(item.getString("tier")),
      createdAt = new DateTime(item.getString("createdAt").toLong),
      productName = item.getString("productName"),
      productUrl = Option(item.getString("productUrl")),
      rangeKey = item.getString("rangekey")
    )
  }

  def toLabel(item: Item): Label = {
    Label(
      id = item.getString("id"),
      properties = LabelProperties(
        name = item.getString("name"),
        colour = item.getString("colour"))
    )
  }

  def fromLabel(label: Label): Item = {
    new Item()
      .withPrimaryKey("id", label.id)
      .withString("name", label.properties.name)
      .withString("colour", label.properties.colour)
  }
}
