import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider, InstanceProfileCredentialsProvider, SystemPropertiesCredentialsProvider }
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import controllers.Application
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, MessagesApi}
import play.api.libs.ws.ning.NingWSComponents

//import play.Routes
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.routing.Router
import router.Routes
import store.Dynamo

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with NingWSComponents {
  val awsCreds = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider(),
    new SystemPropertiesCredentialsProvider(),
    new ProfileCredentialsProvider("capi"),
    new ProfileCredentialsProvider(),
    new InstanceProfileCredentialsProvider()
  )
  val awsRegion = Regions.fromName(configuration.getString("aws.region") getOrElse "eu-west-1")

  val dynamo = {
    val tableName = configuration.getString("aws.s3.dynamo.contributionsTableName") getOrElse "bonobo-keys"
    val client: AmazonDynamoDBClient = new AmazonDynamoDBClient(awsCreds).withRegion(awsRegion)
    new Dynamo(new DynamoDB(client), tableName)
  }

  val messagesApi: MessagesApi = new DefaultMessagesApi(environment, configuration, new DefaultLangs(configuration))
  val appController = new Application(dynamo, wsClient, messagesApi)
  val assets = new controllers.Assets(httpErrorHandler)
  val router: Router = new Routes(httpErrorHandler, appController, assets)

}
