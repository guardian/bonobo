package models

import java.util.UUID
import controllers.Forms._
import org.joda.time.DateTime

/* model used for saving the users on Bonobo */
case class BonoboUser(
  bonoboId: String,
  email: String,
  name: String,
  productName: String,
  productUrl: String,
  companyName: String,
  companyUrl: Option[String],
  additionalInfo: AdditionalUserInfo)

object BonoboUser {
  def apply(id: String, formData: CreateUserFormData): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(DateTime.now(), ManualRegistration, None, None, None, None, None)
    new BonoboUser(id, formData.email, formData.name, formData.productName, formData.productUrl, formData.companyName, formData.companyUrl, additionalInfo)
  }
  def apply(id: String, formData: EditUserFormData, createdAt: DateTime, registrationType: RegistrationType): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(createdAt, registrationType, None, None, None, None, None)
    new BonoboUser(id, formData.email, formData.name, formData.productName, formData.productUrl, formData.companyName, formData.companyUrl, additionalInfo)
  }
  def apply(id: String, formData: OpenCreateKeyFormData): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(DateTime.now(), DeveloperRegistration, None, None, None, None, None)
    new BonoboUser(id, formData.email, formData.name, formData.productName, formData.productUrl, formData.companyName, formData.companyUrl, additionalInfo)
  }
  def apply(formData: CommercialRequestKeyFormData): BonoboUser = {
    val additionalInfo = AdditionalUserInfo(DateTime.now(), CommercialRegistration, formData.businessArea, Some(formData.monthlyUsers.toString), formData.commercialModel, formData.content, Some(formData.articlesPerDay.toString))
    new BonoboUser(java.util.UUID.randomUUID().toString, formData.email, formData.name, formData.productName, formData.productUrl, formData.companyName, formData.companyUrl, additionalInfo)
  }
}

case class AdditionalUserInfo(
  createdAt: DateTime,
  registrationType: RegistrationType,
  businessArea: Option[String],
  monthlyUsers: Option[String],
  commercialModel: Option[String],
  content: Option[String],
  articlesPerDay: Option[String])

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
  rangeKey: String)

object KongKey {
  val Active = "Active"
  val Inactive = "Inactive"

  private def uniqueRangeKey(createdAt: DateTime): String = s"${createdAt.getMillis}_${UUID.randomUUID}"

  def apply(bonoboId: String, kongId: String, form: EditKeyFormData, createdAt: DateTime, rateLimits: RateLimits, rangeKey: String): KongKey = {
    new KongKey(bonoboId, kongId, form.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, form.tier, form.status, createdAt, rangeKey)
  }

  def apply(bonoboId: String, consumer: ConsumerCreationResult, rateLimits: RateLimits, tier: Tier): KongKey = {
    new KongKey(bonoboId, consumer.id, consumer.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, tier, Active, consumer.createdAt, uniqueRangeKey(consumer.createdAt))
  }

}

/* model used for show all keys table */
case class BonoboInfo(kongKey: KongKey, bonoboUser: BonoboUser)

case class ResultsPage[A](items: List[A], hasNext: Boolean)

case class ConsumerCreationResult(id: String, createdAt: DateTime, key: String)

case class RateLimits(requestsPerMinute: Int, requestsPerDay: Int)

sealed trait Tier {
  def rateLimit: RateLimits
  def friendlyName: String
  def conciergeName: String
}

object Tier {
  def withName(tier: String): Option[Tier] = tier match {
    case "Developer" => Some(Developer)
    case "RightsManaged" => Some(RightsManaged)
    case "Internal" => Some(Internal)
    case _ => None
  }

  def isValid(tier: String): Boolean = withName(tier).isDefined
}

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

sealed trait RegistrationType {
  def friendlyName: String
}

object RegistrationType {
  def withName(registrationType: String): Option[RegistrationType] = registrationType match {
    case "Developer" => Some(DeveloperRegistration)
    case "Commercial" => Some(CommercialRegistration)
    case "Manual" => Some(ManualRegistration)
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

