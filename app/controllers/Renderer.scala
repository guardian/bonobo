package controllers

import scala.xml.{ Group, Node }

object Renderer {

  def renderTitle(title: String): Node = {
    <div class="page-header">
      <h3 class="text-primary">{ title }</h3>
    </div>
  }

  def renderMessages(success: Option[String], error: Option[String]): Node = {
    <div>
      {
        Group(Seq(
          success map { message => <p class="alert alert-success">{ message }</p> } getOrElse Group(Nil),
          error map { message => <p class="alert alert-danger">{ message }</p> } getOrElse Group(Nil)
        ))
      }
    </div>
  }
}
