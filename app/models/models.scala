package models

import controllers.Application.CreateFormData
import org.joda.time.DateTime
import play.api.libs.json.Json

/* model used saving the users in Bonobo */
case class BonoboUser(bonoboId: String,
  email: String,
  name: String,
  company: String,
  url: String)

object BonoboUser {
  def apply(id: String, formData: CreateFormData): BonoboUser = {
    new BonoboUser(id, formData.email, formData.name, formData.company, formData.url)
  }
}

/* model used for saving the keys on Kong */
case class KongKey(bonoboId: String,
  key: String,
  requestsPerDay: Int,
  requestsPerMinute: Int,
  tier: String,
  status: String,
  createdAt: DateTime)

object KongKey {
  def apply(consumer: UserCreationResult, formData: CreateFormData, rateLimits: RateLimits): KongKey = {
    new KongKey(consumer.id, consumer.key, rateLimits.requestsPerDay, rateLimits.requestsPerMinute,
      formData.tier, "Active", consumer.createdAt)
  }
}

/* model used for show all keys table */
case class BonoboInfo(kongKey: KongKey, bonoboUser: BonoboUser)

/* model used to parse json after create user */
case class KongCreateConsumerResponse(id: String, created_at: Long)

object KongCreateConsumerResponse {
  implicit val consumerRead = Json.reads[KongCreateConsumerResponse]
}

case class UserCreationResult(id: String, createdAt: DateTime, key: String)

case class RateLimits(requestsPerMinute: Int, requestsPerDay: Int)

case class KongPluginConfig(id: String)

object KongPluginConfig {
  implicit val pluginsRead = Json.reads[KongPluginConfig]
}

/* These are used to extract the key.id from the json response of kong.getKeyIdForGivenUser(),
   which looks like this: { "data" : [ { "id": "<value>", ... }, ... ] }
 */

case class KongListConsumerKeysResponse(data: List[KongKeyResponse])

case class KongKeyResponse(id: String)

object KongKeyResponse {
  implicit val keyRead = Json.reads[KongKeyResponse]
}

object KongListConsumerKeysResponse {
  implicit val keyRead = Json.reads[KongListConsumerKeysResponse]

}
