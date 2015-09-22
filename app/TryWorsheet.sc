

case class Resident(name: String, age: Int) extends Serializable{
  override def toString = s"{ name: $name, age: $age}"
}

val res: Resident = new Resident("maria", 22)

println(res)

import java.util.Random

import play.api.libs.json._

val json: JsValue = JsObject(Seq(
  "name" -> JsString(res.name),
  "age" -> JsNumber(4)
))

println(json)


val jsonString: String = Json.stringify(json)

println(jsonString)

var x = new Random(10)

println(x.nextLong())