package models

import controllers.Application.CreateFormData
import play.api.libs.json.Json

case class BonoboKey(id: String,
  key: String,
  email: String,
  name: String,
  company: String,
  url: String,
  requestsPerDay: Int,
  requestsPerMinute: Int,
  tier: String,
  status: String,
  createdAt: String)

case class KongCreateConsumerResponse(id: String, created_at: Long)

object KongCreateConsumerResponse {
  implicit val consumerRead = Json.reads[KongCreateConsumerResponse]
}

case class RateLimits(requestsPerMinute: Int, requestsPerDay: Int)

object BonoboKey {
  def apply(formData: CreateFormData, rateLimits: RateLimits, id: String, createdAt: String): BonoboKey = {
    val key: String = java.util.UUID.randomUUID.toString
    new BonoboKey(id, key, formData.email, formData.name, formData.company,
      formData.url, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, formData.tier, formData.status, createdAt)
  }
}
case class KongPluginConfig(id: String)

object KongPluginConfig {
  implicit val pluginsRead = Json.reads[KongPluginConfig]
}
