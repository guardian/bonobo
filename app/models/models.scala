package models

import play.api.libs.json.Json

case class BonoboKeys
(Id: String,
 key: String,
 email: String,
 name: String,
 surname: String,
 company: String,
 url: String,
 requests_per_day: Int,
 requests_per_minute: Int,
 tier: String,
 status: String)

case class ConsumerInput(id: Option[String], username: Option[String], message: Option[String])

object ConsumerInput {
 implicit val consumerRead = Json.reads[ConsumerInput]
}