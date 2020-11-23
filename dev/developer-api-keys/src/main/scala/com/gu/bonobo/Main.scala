package com.gu.bonobo

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import models.Tier
import scopt.OParser
import store.Dynamo
import util.AWSConstants

import scala.io.Source
import scala.util.Try

case class Config(
   usersTable: String,
   keysTable: String,
   labelsTable: String,
   apiKeyUsageFile: String,
   usageCount: Int
)

object Config {

  val code: Config = Config(
    usersTable = "bonobo-CODE-users",
    keysTable = "bonobo-CODE-keys",
    labelsTable = "bonobo-CODE-labels",
    apiKeyUsageFile = "",
    usageCount = 1
  )

  private val builder = OParser.builder[Config]

  private val parser = {
    import builder._
    OParser.sequence(
      head(
        "Given a csv file with API key usage,\n",
        "query the Bonobo keys database to understand which of these are in the Developer tier.\n",
        "This is relevant since Gibbons deletes keys in the Developer tier,\n",
        "so we want to eyeball the generated list for any that might be used in PROD."
      ),
      programName("run"),
      help("help"),
      opt[String]("users")
        .action((usersTable, config) => config.copy(usersTable = usersTable)),
      opt[String]("keys")
        .action((keysTable, config) => config.copy(keysTable = keysTable)),
      opt[String]("labels")
        .action((labelsTable, config) => config.copy(labelsTable = labelsTable)),
      opt[String]("api-key-usage")
        .text("see README on how this file can be generated; never check this file into version control")
        .action((apiKeyUsageFile, config) => config.copy(apiKeyUsageFile = apiKeyUsageFile))
        .required(),
      opt[Int]("usage-count")
        .text("not interested in API keys with usage count < this number")
        .action((usageCount, config) => config.copy(usageCount = usageCount))
    )
  }

  def parse(args: Seq[String]): Config = {
    OParser.parse(parser, args, Config.code).getOrElse(throw new RuntimeException("invalid args"))
  }
}

object ApiKeyUsage {

  case class Item(apiKey: String, count: Int)

  def fromFile(filename: String): List[Item] = {
    val source = Source.fromFile(filename)
    try {
      source.getLines.flatMap { line =>
        Try {
          val fields = line.split(",")
          Item(fields(0), fields(1).toInt)
        }.toOption
      }.toList
    } finally {
      source.close()
    }
  }
}

case class HighRiskApiKey(email: String, productName: String, usageCount: Int)

object Main extends App {

  val config = Config.parse(args)

  val dynamoDbClient = AmazonDynamoDBClientBuilder.standard()
    .withCredentials(AWSConstants.CredentialsProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()

  val dynamoDb = new DynamoDB(dynamoDbClient)

  val dynamo = new Dynamo(
    new DynamoDB(dynamoDbClient),
    usersTable = config.usersTable,
    keysTable = config.keysTable,
    labelTable = config.labelsTable
  )

  val highRiskApiKeys = ApiKeyUsage.fromFile(config.apiKeyUsageFile)
    .foldLeft(List.empty[HighRiskApiKey]) { case (keys, usage) =>
      println("processing API key")
      val highRiskApiKey =
        for {
          kongKey <- dynamo.getKeyWithValue(usage.apiKey) if usage.count >= config.usageCount
          user <- dynamo.getUserWithId(kongKey.bonoboId) if kongKey.tier == Tier.Developer
        } yield {
          HighRiskApiKey(user.email, kongKey.productName, usage.count)
        }
      highRiskApiKey.fold(keys)(_ :: keys)
    }

  for {
    apiKey <- highRiskApiKeys
  } yield {
    println(s"${apiKey.email}, ${apiKey.productName}, ${apiKey.usageCount}")
  }

  dynamoDb.shutdown()
  dynamoDbClient.shutdown()
}
