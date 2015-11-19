package email

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClient
import com.amazonaws.services.simpleemail.model._
import models.BonoboUser
import play.api.Logger
import play.api.mvc.RequestHeader

import scala.concurrent.{ Future, Promise }

trait MailClient {
  def sendEmailCommercialRequest(user: BonoboUser)(implicit request: RequestHeader): Future[SendEmailResult]

  def sendEmailNewKey(toEmail: String, key: String): Future[SendEmailResult]
}

class AwsEmailClient(amazonMailClient: AmazonSimpleEmailServiceAsyncClient, fromAddress: String, responseHandler: AsyncHandler[SendEmailRequest, SendEmailResult] = AwsMailClient.asyncHandler) extends MailClient {

  private def sendEmail(address: String, subject: String, message: String): Future[SendEmailResult] = {
    Logger.debug(s"Sending $subject to $address")

    val destination = new Destination().withToAddresses(address)

    val emailSubject = new Content().withData(subject)
    val textBody = new Content().withData(message)
    val body = new Body().withText(textBody)

    val emailMessage = new Message().withSubject(emailSubject).withBody(body)

    val request = new SendEmailRequest().withSource(fromAddress).withDestination(destination).withMessage(emailMessage)

    val promise = Promise[SendEmailResult]()
    val responseHandler = new AsyncHandler[SendEmailRequest, SendEmailResult] {
      override def onError(e: Exception) = {
        Logger.warn(s"Could not send mail: ${e.getMessage}")
        promise.failure(e)
      }

      override def onSuccess(request: SendEmailRequest, result: SendEmailResult) = {
        Logger.info("The email has been successfully sent")
        promise.success(result)
      }
    }
    amazonMailClient.sendEmailAsync(request, responseHandler)
    promise.future
  }

  def sendEmailCommercialRequest(user: BonoboUser)(implicit request: RequestHeader): Future[SendEmailResult] = {
    val message = s"""Sent at: ${user.additionalInfo.createdAt.toString("dd-MM-yyyy hh:mma")}
      |Name: ${user.name}
      |Email: ${user.email}
      |Product name: ${user.productName}
      |Product URL: ${user.productUrl}
      |Company name: ${user.companyName}
      |Company URL: ${user.companyUrl.getOrElse('-')}
      |Business area: ${user.additionalInfo.businessArea.getOrElse('-')}
      |Commercial model: ${user.additionalInfo.commercialModel.getOrElse('-')}
      |Content type: ${user.additionalInfo.content.getOrElse('-')}
      |Monthly users: ${user.additionalInfo.monthlyUsers.getOrElse('-')}
      |Articles per day: ${user.additionalInfo.articlesPerDay.getOrElse('-')}
      |${controllers.routes.Application.editUserPage(user.bonoboId).absoluteURL()}""".stripMargin
    sendEmail("maria-livia.chiorean@guardian.co.uk", "Commercial Key Request", message) //this should be eventually sent to content.delivery@guardian.co.uk instead
  }

  def sendEmailNewKey(toEmail: String, key: String): Future[SendEmailResult] = {
    val message = s"A new key has been created for you: $key"
    sendEmail(toEmail, "New Key Created", message)
  }
}

object AwsMailClient {
  val asyncHandler = new AsyncHandler[SendEmailRequest, SendEmailResult] {
    override def onError(e: Exception) = {
      Logger.warn(s"Could not send mail: ${e.getMessage}")
    }

    override def onSuccess(request: SendEmailRequest, result: SendEmailResult) =
      Logger.info("The email has been successfully sent")
  }
}