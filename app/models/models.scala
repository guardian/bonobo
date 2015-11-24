package models

import java.util.UUID
import controllers.Forms._
import enumeratum.{ PlayJsonEnum, Enum, EnumEntry }
import org.joda.time.DateTime
import play.api.libs.json.{ Writes, Reads, Json }

/* model used for saving the users on Bonobo */
case class BonoboUser(
  bonoboId: String,
  email: String,
  name: String,
  companyName: String,
  companyUrl: String,
  additionalInfo: AdditionalUserInfo)

object BonoboUser {
  /* Method used when manually creating a new user */
  def apply(id: String, formData: CreateUserFormData): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(DateTime.now(), ManualRegistration)
    new BonoboUser(id, formData.email, formData.name, formData.companyName, formData.companyUrl, additionalInfo)
  }
  /* Method used when editing a new user */
  def apply(id: String, formData: EditUserFormData, createdAt: DateTime, registrationType: RegistrationType): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(createdAt, registrationType)
    new BonoboUser(id, formData.email, formData.name, formData.companyName, formData.companyUrl, additionalInfo)
  }
  /* Method used when using the developer form for creating a new user */
  def apply(id: String, formData: DeveloperCreateKeyFormData): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(DateTime.now(), DeveloperRegistration)
    new BonoboUser(id, formData.email, formData.name, formData.companyName, formData.companyUrl, additionalInfo)
  }
  /* Method used when using the commercial form for creating a new user */
  def apply(formData: CommercialRequestKeyFormData): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(DateTime.now(), CommercialRegistration, Some(formData.businessArea), Some(formData.monthlyUsers.toString), Some(formData.commercialModel), Some(formData.content), Some(formData.contentFormat), Some(formData.articlesPerDay.toString))
    new BonoboUser(java.util.UUID.randomUUID().toString, formData.email, formData.name, formData.companyName, formData.companyUrl, additionalInfo)
  }
}

case class AdditionalUserInfo(
  createdAt: DateTime,
  registrationType: RegistrationType,
  businessArea: Option[String],
  monthlyUsers: Option[String],
  commercialModel: Option[String],
  content: Option[String],
  contentFormat: Option[String],
  articlesPerDay: Option[String])

object AdditionalUserInfo {
  def apply(date: DateTime, registrationType: RegistrationType): AdditionalUserInfo = {
    new AdditionalUserInfo(date, registrationType, None, None, None, None, None, None)
  }
}

/* model used for saving the keys on Kong */
case class KongKey(
  bonoboId: String,
  kongId: String,
  key: String,
  requestsPerDay: Int,
  requestsPerMinute: Int,
  tier: Tier,
  status: String,
  createdAt: DateTime,
  productName: String,
  productUrl: String,
  rangeKey: String)

object KongKey {
  val Active = "Active"
  val Inactive = "Inactive"

  private def uniqueRangeKey(createdAt: DateTime): String = s"${createdAt.getMillis}_${UUID.randomUUID}"

  def apply(bonoboId: String, kongId: String, form: EditKeyFormData, createdAt: DateTime, rateLimits: RateLimits, rangeKey: String): KongKey = {
    new KongKey(bonoboId, kongId, form.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, form.tier, form.status, createdAt, form.productName, form.productUrl, rangeKey)
  }

  def apply(bonoboId: String, consumer: ConsumerCreationResult, rateLimits: RateLimits, tier: Tier, productName: String, productUrl: String): KongKey = {
    new KongKey(bonoboId, consumer.id, consumer.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, tier, Active, consumer.createdAt, productName, productUrl, uniqueRangeKey(consumer.createdAt))
  }

  /* model used in the migration endpoint */
  def apply(bonoboId: String, consumer: ConsumerCreationResult, rateLimits: RateLimits, tier: Tier, productName: String, productUrl: String, status: String, date: DateTime): KongKey = {
    new KongKey(bonoboId, consumer.id, consumer.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, tier, status, date, productName, productUrl, uniqueRangeKey(date))
  }
}

/* model used for show all keys table */
case class BonoboInfo(kongKey: KongKey, bonoboUser: BonoboUser)

case class ResultsPage[A](items: List[A], hasNext: Boolean)

case class ConsumerCreationResult(id: String, createdAt: DateTime, key: String)

case class RateLimits(requestsPerMinute: Int, requestsPerDay: Int)

sealed trait Tier extends EnumEntry {
  def rateLimit: RateLimits
  def friendlyName: String
  def conciergeName: String
}

object Tier extends Enum[Tier] with PlayJsonEnum[Tier] {

  val values = findValues

  case object Developer extends Tier {
    def rateLimit: RateLimits = RateLimits(720, 5000)
    def friendlyName: String = "Developer"
    def conciergeName: String = "developer"
  }
  case object RightsManaged extends Tier {
    def rateLimit: RateLimits = RateLimits(720, 10000)
    def friendlyName: String = "Rights managed"
    def conciergeName: String = "rights-managed"
  }
  case object Internal extends Tier {
    def rateLimit: RateLimits = RateLimits(720, 10000)
    def friendlyName: String = "Internal"
    def conciergeName: String = "internal"
  }

  def isValid(tier: String): Boolean = withNameOption(tier).isDefined
}

sealed trait RegistrationType {
  def friendlyName: String
}

object RegistrationType {
  def withName(registrationType: String): Option[RegistrationType] = registrationType match {
    case "Developer" => Some(DeveloperRegistration)
    case "Commercial" => Some(CommercialRegistration)
    case "Manual" => Some(ManualRegistration)
    case "Imported from Mashery" => Some(MasheryRegistration)
    case _ => None
  }
}

case object DeveloperRegistration extends RegistrationType {
  def friendlyName: String = "Developer"
}
case object CommercialRegistration extends RegistrationType {
  def friendlyName: String = "Commercial"
}
case object ManualRegistration extends RegistrationType {
  def friendlyName: String = "Manual"
}
case object MasheryRegistration extends RegistrationType {
  def friendlyName: String = "Imported from Mashery"
}

case class MasheryUser(
  name: String,
  email: String,
  companyName: String,
  companyUrl: String,
  createdAt: DateTime,
  keys: List[MasheryKey])

object MasheryKey {
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val keyRead = Json.reads[MasheryKey]
}

object MasheryUser {
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val userRead = Json.reads[MasheryUser]
}

case class MasheryKey(
  key: String,
  productName: String,
  productUrl: String,
  requestsPerDay: Int,
  requestsPerMinute: Int,
  tier: Tier,
  status: String,
  createdAt: DateTime)

case class MigrationResult(successfullyManagedUsers: Int, successfullyManagedKeys: Int, userConflicts: List[EmailConflict], keyConflicts: List[KeyConflict])

sealed trait MigrateUserResult
case class MigratedUser(keyResults: List[MigrateKeyResult]) extends MigrateUserResult
case class EmailConflict(email: String) extends MigrateUserResult

sealed trait MigrateKeyResult
case object MigratedKey extends MigrateKeyResult
case class KeyConflict(key: String) extends MigrateKeyResult

case object EmailConflict {
  implicit val emailConflictWrites = Json.writes[EmailConflict]
  implicit val emailConflictReads = Json.reads[EmailConflict]
}

case object KeyConflict {
  implicit val keyConflictWrites = Json.writes[KeyConflict]
  implicit val keyConflictReads = Json.reads[KeyConflict]
}

case object MigrationResult {
  implicit val migrationResultWrites = Json.writes[MigrationResult]
  implicit val migrationResultReads = Json.reads[MigrationResult]
}
