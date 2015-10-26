package models

import controllers.Application.{ EditKeyFormData, CreateUserFormData, EditUserFormData }
import org.joda.time.DateTime

/* model used for saving the users on Bonobo */
case class BonoboUser(
  bonoboId: String,
  email: String,
  name: String,
  company: String,
  url: String)

object BonoboUser {
  def apply(id: String, formData: CreateUserFormData): BonoboUser = {
    new BonoboUser(id, formData.email, formData.name, formData.company, formData.url)
  }
  def apply(id: String, formData: EditUserFormData): BonoboUser = {
    new BonoboUser(id, formData.email, formData.name, formData.company, formData.url)
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
  createdAt: DateTime)

object KongKey {

  def apply(bonoboId: String, kongId: String, form: EditKeyFormData, createdAt: DateTime, rateLimits: RateLimits): KongKey = {
    new KongKey(bonoboId, kongId, form.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, form.tier, form.status, createdAt)
  }

  def apply(bonoboId: String, consumer: UserCreationResult, rateLimits: RateLimits, tier: Tier): KongKey = {
    new KongKey(bonoboId, consumer.id, consumer.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, tier, "Active", consumer.createdAt)
  }

}

/* model used for show all keys table */
case class BonoboInfo(kongKey: KongKey, bonoboUser: BonoboUser)

case class UserCreationResult(id: String, createdAt: DateTime, key: String)

case class RateLimits(requestsPerMinute: Int, requestsPerDay: Int)

sealed trait Tier {
  def rateLimit: RateLimits
  def friendlyName: String
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
}
case object RightsManaged extends Tier {
  def rateLimit: RateLimits = RateLimits(720, 10000)
  def friendlyName: String = "Rights managed"
}
case object Internal extends Tier {
  def rateLimit: RateLimits = RateLimits(720, 10000)
  def friendlyName: String = "Internal"
}

