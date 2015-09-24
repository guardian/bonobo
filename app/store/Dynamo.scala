package store

import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item}
import models._

import scala.collection.JavaConverters._

class Dynamo(db: DynamoDB, tableName: String) {

  private val bonoboTable = db.getTable(tableName)

  def save(bonoboKeys: BonoboKeys): Unit = {
    val item = toItem(bonoboKeys)
    bonoboTable.putItem(item)
  }

  def getAllKeys(): List[BonoboKeys] = {
    val it = bonoboTable.scan(new ScanSpec()).iterator().asScala
    it.map(fromItem(_)).toList.sortBy(_.created_at).reverse
  }

  def toItem(bonoboKeys: BonoboKeys): Item = {
    new Item()
      .withPrimaryKey("Id", bonoboKeys.Id)
      .withString("key", bonoboKeys.key)
      .withString("name", bonoboKeys.name)
      .withString("surname", bonoboKeys.surname)
      .withString("company", bonoboKeys.company)
      .withString("email", bonoboKeys.email)
      .withInt("requests_per_day", bonoboKeys.requests_per_day)
      .withInt("requests_per_minute", bonoboKeys.requests_per_minute)
      .withString("status", bonoboKeys.status)
      .withString("tier", bonoboKeys.tier)
      .withString("url", bonoboKeys.url)
      .withLong("created_at", bonoboKeys.created_at)
  }

  def fromItem(item: Item): BonoboKeys = {
    BonoboKeys(
      Id = item.getString("Id"),
      key = item.getString("key"),
      name = item.getString("name"),
      surname = item.getString("surname"),
      company = item.getString("company"),
      email = item.getString("email"),
      requests_per_day = item.getInt("requests_per_day"),
      requests_per_minute = item.getInt("requests_per_minute"),
      status = item.getString("status"),
      tier = item.getString("tier"),
      url = item.getString("url"),
      created_at = item.getLong("created_at")
    )
  }
}
