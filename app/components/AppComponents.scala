package components

import java.io.FileInputStream

import akka.stream.Materializer
import auth.{ Authorisation, DummyAuthorisation, GoogleGroupsAuthorisation }
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClientBuilder
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsync
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import controllers._
import com.gu.googleauth.{ GoogleAuthConfig, GoogleServiceAccount }
import email.{ AwsEmailClient, MailClient }
import kong.{ Kong, KongClient }
import models.LabelProperties
import org.joda.time.Duration
import play.api.ApplicationLoader.Context
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{ ActionBuilder, ActionFunction, AnyContent, Filter, RequestHeader, Result, Results }
import play.api.routing.Router
import play.api.{ BuiltInComponents, BuiltInComponentsFromContext, Logger, Mode }
import play.filters.csrf.CSRFComponents
import play.filters.headers.{ SecurityHeadersConfig, SecurityHeadersFilter }
import store.Dynamo
import util.AWSConstants._
import router.Routes

import scala.concurrent.Future

trait GoogleAuthComponent { self: BuiltInComponents =>

  val googleAuthConfig = {
    def missingKey(description: String) =
      sys.error(s"$description missing. You can create an OAuth 2 client from the Credentials section of the Google dev console.")
    GoogleAuthConfig(
      clientId = configuration.get[String]("google.clientId"),
      clientSecret = configuration.get[String]("google.clientSecret"),
      redirectUrl = configuration.get[String]("google.redirectUrl"),
      domain = "guardian.co.uk",
      maxAuthAge = Some(Duration.standardDays(90)),
      enforceValidity = true)
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
        val impersonatedUser = configuration.get[String]("google.impersonatedUser")
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
    val awsRegion = Regions.fromName(configuration.getOptional[String]("aws.region") getOrElse "eu-west-1")
    val clientBuilder = AmazonDynamoDBClientBuilder.standard()
    val usersTable = configuration.getOptional[String]("aws.dynamo.usersTableName") getOrElse "Bonobo-Users"
    val keysTable = configuration.getOptional[String]("aws.dynamo.keysTableName") getOrElse "Bonobo-Keys"
    val labelsTable = configuration.getOptional[String]("aws.dynamo.labelsTableName") getOrElse "Bonobo-Labels"
    val client: AmazonDynamoDB = clientBuilder.withCredentials(CredentialsProvider).withRegion(awsRegion).build()
    new Dynamo(new DynamoDB(client), usersTable, keysTable, labelsTable)
  }
}

trait KongComponent {
  def kong: Kong
}

trait KongComponentImpl extends KongComponent { self: BuiltInComponents with AhcWSComponents =>

  def confString(key: String) = configuration.getOptional[String](key) getOrElse sys.error(s"Missing configuration key: $key")

  val kong = {
    val apiAddress = confString("kong.new.apiAddress")
    val apiName = confString("kong.new.apiName")
    new KongClient(wsClient, apiAddress, apiName)
  }
}

trait AwsEmailComponent {
  def awsEmail: MailClient

}

trait AwsEmailComponentImpl extends AwsEmailComponent { self: BuiltInComponents =>
  val awsEmail = {
    val awsRegion = Regions.fromName(configuration.getOptional[String]("aws.region") getOrElse "eu-west-1")
    val clientBuilder = AmazonSimpleEmailServiceAsyncClientBuilder.standard()
    val amazonSesClient: AmazonSimpleEmailServiceAsync = clientBuilder.withCredentials(CredentialsProvider).withRegion(awsRegion).build()
    val fromAddress = "no-reply@open-platform.theguardian.com" //The open-platform.theguardian.com domain is verified, therefore any email can be used (e.g. test@open-platform.theguardian.com)
    val enableEmail = configuration.getOptional[Boolean]("email.enabled") getOrElse false
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

trait FiltersComponent extends CSRFComponents { self: BuiltInComponents =>
  val contentSecurityPolicy = "script-src 'self' 'unsafe-inline' https://maxcdn.bootstrapcdn.com https://ajax.googleapis.com"
  override lazy val httpFilters = Seq(
    new MaintenanceFilter,
    csrfFilter,
    SecurityHeadersFilter(SecurityHeadersConfig(contentSecurityPolicy = Some(contentSecurityPolicy))),
    new CacheFilter)

  class MaintenanceFilter(implicit val mat: Materializer) extends Filter with Results {
    //Filter all requests while in maintenance mode
    def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
      if (request.path != "/healthcheck" && configuration.getOptional[Boolean]("maintenanceMode").getOrElse(false)) {
        Future.successful(ServiceUnavailable("503 - Server is down for maintenance, please try again later"))
      } else {
        next(request)
      }
    }
  }
}

trait ControllersComponent {
  self: BuiltInComponentsFromContext with AhcWSComponents with GoogleAuthComponent with AuthorisationComponent with DynamoComponent with KongComponent with AwsEmailComponent with LabelsComponent with AssetsComponents =>

  def enableAuth: Boolean

  def appController = new Application(
    controllerComponents,
    dynamo,
    kong,
    awsEmail,
    labelsMap,
    googleAuthConfig,
    assetsFinder,
    enableAuth)
  def authController = new Auth(controllerComponents, googleAuthConfig, wsClient)

  val developerFormController = new DeveloperForm(controllerComponents, dynamo, kong, awsEmail, assetsFinder)
  val commercialFormController = new CommercialForm(controllerComponents, dynamo, awsEmail, assetsFinder)

  val router: Router = new Routes(httpErrorHandler, appController, developerFormController, commercialFormController, authController, assets)
}

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
  with AhcWSComponents
  with GoogleAuthComponent
  with AuthorisationComponent
  with DynamoComponentImpl
  with KongComponentImpl
  with AwsEmailComponentImpl
  with LabelsComponentImpl
  with FiltersComponent
  with AssetsComponents
  with ControllersComponent {

  def enableAuth = true
}
