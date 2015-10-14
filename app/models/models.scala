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

object BonoboKey {
  def apply(formData: CreateFormData, rateLimits: RateLimits, id: String, createdAt: String): BonoboKey = {
    /* Notice that the second field here is the key, which by default is the same as the user's id;
       if we decide to use a custom key instead *at creation time* we need some refactoring
     */
    new BonoboKey(id, id, formData.email, formData.name, formData.company,
      formData.url, rateLimits.requestsPerDay, rateLimits.requestsPerMinute, formData.tier, formData.status, createdAt)
  }
}

case class KongCreateConsumerResponse(id: String, created_at: Long)

object KongCreateConsumerResponse {
  implicit val consumerRead = Json.reads[KongCreateConsumerResponse]
}

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
