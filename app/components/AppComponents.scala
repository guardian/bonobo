package components

import java.io.FileInputStream

import auth.{ GoogleGroupsAuthorisation, DummyAuthorisation, Authorisation }
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClient
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import controllers._
import com.gu.googleauth.{ GoogleServiceAccount, GoogleAuthConfig }
import controllers.csrf.CSRFFilter
import email.{ MailClient, AwsEmailClient }
import kong.{ Kong, KongClient }
import models.LabelProperties
import org.joda.time.Duration
import play.api.ApplicationLoader.Context
import play.api.i18n.{ DefaultLangs, DefaultMessagesApi, MessagesApi }
import play.api.libs.ws.ning.NingWSComponents
import play.api.routing.Router
import play.api.{ Logger, Mode, BuiltInComponents, BuiltInComponentsFromContext }
import play.filters.csrf.CSRF.ConfigTokenProvider
import play.filters.csrf.CSRFConfig
import store.Dynamo
import util.AWSConstants._
import router.Routes

trait GoogleAuthComponent { self: BuiltInComponents =>

  val googleAuthConfig = {
    def missingKey(description: String) =
      sys.error(s"$description missing. You can create an OAuth 2 client from the Credentials section of the Google dev console.")
    GoogleAuthConfig(
      clientId = configuration.getString("google.clientId") getOrElse missingKey("OAuth 2 client ID"),
      clientSecret = configuration.getString("google.clientSecret") getOrElse missingKey("OAuth 2 client secret"),
      redirectUrl = configuration.getString("google.redirectUrl") getOrElse missingKey("OAuth 2 callback URL"),
      domain = Some("guardian.co.uk"),
      maxAuthAge = Some(Duration.standardDays(90)),
      enforceValidity = true
    )
  }

}

trait AuthorisationComponent { self: BuiltInComponents =>

  val authorisation: Authorisation = {
    if (self.environment.mode == Mode.Prod) {
      Logger.info("Will use Google groups for user authorisation")
      val serviceAccount = {
        val certFile = new FileInputStream("/etc/gu/bonobo-google-service-account.json")
        val credential = GoogleCredential.fromStream(certFile)
        Logger.info(s"Loaded Google credentials. Service account ID: ${credential.getServiceAccountId}")
        val impersonatedUser = configuration.getString("google.impersonatedUser").getOrElse(sys.error("Missing key: google.impersonatedUser"))
        new GoogleServiceAccount(credential.getServiceAccountId, credential.getServiceAccountPrivateKey, impersonatedUser)
      }
      new GoogleGroupsAuthorisation(serviceAccount)
    } else {
      // Disable Google group checking when running on local machine or running tests,
      // because distributing the service account's private key is tricky.
      Logger.info("User authorisation is disabled!")
      DummyAuthorisation
    }
  }

}

trait DynamoComponent {
  def dynamo: Dynamo
}

trait DynamoComponentImpl extends DynamoComponent { self: BuiltInComponents =>
  val dynamo = {
    val awsRegion = Regions.fromName(configuration.getString("aws.region") getOrElse "eu-west-1")
    val usersTable = configuration.getString("aws.dynamo.usersTableName") getOrElse "Bonobo-Users"
    val keysTable = configuration.getString("aws.dynamo.keysTableName") getOrElse "Bonobo-Keys"
    val labelsTable = configuration.getString("aws.dynamo.labelsTableName") getOrElse "Bonobo-Labels"
    val client: AmazonDynamoDBClient = new AmazonDynamoDBClient(CredentialsProvider).withRegion(awsRegion)
    new Dynamo(new DynamoDB(client), usersTable, keysTable, labelsTable)
  }
}

trait KongComponent {
  def kong: Kong
}

trait KongComponentImpl extends KongComponent { self: BuiltInComponents with NingWSComponents =>
  val kong = {
    def confString(key: String) = configuration.getString(key) getOrElse sys.error(s"Missing configuration key: $key")
    val apiAddress = confString("kong.apiAddress")
    val apiName = confString("kong.apiName")
    new KongClient(wsClient, apiAddress, apiName)
  }
}

trait AwsEmailComponent {
  def awsEmail: MailClient

}

trait AwsEmailComponentImpl extends AwsEmailComponent { self: BuiltInComponents =>
  val awsEmail = {
    val awsRegion = Regions.fromName(configuration.getString("aws.region") getOrElse "eu-west-1")
    val amazonSesClient: AmazonSimpleEmailServiceAsyncClient = new AmazonSimpleEmailServiceAsyncClient(CredentialsProvider).withRegion(awsRegion)
    val fromAddress = "no-reply@open-platform.theguardian.com" //The open-platform.theguardian.com domain is verified, therefore any email can be used (e.g. test@open-platform.theguardian.com)
    val enableEmail = configuration.getBoolean("email.enabled") getOrElse false
    new AwsEmailClient(amazonSesClient, fromAddress, enableEmail)
  }
}

trait LabelsComponent {
  def labelsMap: Map[String, LabelProperties]
}

trait LabelsComponentImpl extends LabelsComponent with DynamoComponent {
  val labelsMap = {
    val labelsList = dynamo.getLabels()
    labelsList.foldLeft(Map.empty: Map[String, LabelProperties]) { (acc, label) =>
      acc + (label.id -> label.properties)
    }
  }
}

trait CSRFComponent { self: BuiltInComponents =>
  override lazy val httpFilters = Seq(
    CSRFFilter(CSRFConfig(), new ConfigTokenProvider(CSRFConfig())),
    HttpsRedirectFilter.fromConfiguration(configuration)
  )
}

trait ControllersComponent {
  self: BuiltInComponents with NingWSComponents with GoogleAuthComponent with AuthorisationComponent with DynamoComponent with KongComponent with AwsEmailComponent with LabelsComponent =>
  def enableAuth: Boolean
  def messagesApi: MessagesApi = new DefaultMessagesApi(environment, configuration, new DefaultLangs(configuration))
  def appController = new Application(dynamo, kong, awsEmail, labelsMap, messagesApi, googleAuthConfig, enableAuth)
  def authController = new Auth(googleAuthConfig, authorisation, wsApi)

  val developerFormController = new DeveloperForm(dynamo, kong, awsEmail, messagesApi)
  val commercialFormController = new CommercialForm(dynamo, kong, awsEmail, messagesApi)

  val assets = new controllers.Assets(httpErrorHandler)
  val router: Router = new Routes(httpErrorHandler, appController, developerFormController, commercialFormController, authController, assets)
}

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with NingWSComponents
    with GoogleAuthComponent
    with AuthorisationComponent
    with DynamoComponentImpl
    with KongComponentImpl
    with AwsEmailComponentImpl
    with LabelsComponentImpl
    with ControllersComponent
    with CSRFComponent {
  def enableAuth = true
}
