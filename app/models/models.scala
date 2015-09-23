package models

import play.api.libs.json.Json
import play.api.libs.functional.syntax._
import play.libs.F.Tuple

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


/*
case class ConsumerHeaders(AccessControlAllowOrigin: String,
Connection: String,
ContentType: String,
Date: String,
Server: String,
Status: String,
TransferEncoding: String)

object ConsumerHeaders {
  implicit val consumerRead = Json.reads[ConsumerInput]
}
*/