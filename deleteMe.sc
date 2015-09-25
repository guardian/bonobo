
class Upper {
  def up(strings: String*): Seq[String] = {
    strings.map((s: String) => s.toUpperCase())
  }
}

val up = new Upper
println(up.up("Hello", "world"))