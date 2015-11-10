package email

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClient
import com.amazonaws.services.simpleemail.model._
import models.BonoboUser
import play.api.Logger

trait MailClient {
  def sendEmail(address: String, subject: String, message: String): Unit
}

class AwsEmailClient(amazonMailClient: AmazonSimpleEmailServiceAsyncClient, fromAddress: String, responseHandler: AsyncHandler[SendEmailRequest, SendEmailResult] = AwsMailClient.asyncHandler) extends MailClient {

  override def sendEmail(address: String, subject: String, message: String): Unit = {
    Logger.debug(s"Sending $subject to $address")

    val destination = new Destination().withToAddresses(address)

    val emailSubject = new Content().withData(subject)
    val textBody = new Content().withData(message)
    val body = new Body().withText(textBody)

    val emailMessage = new Message().withSubject(emailSubject).withBody(body)

    val request = new SendEmailRequest().withSource(fromAddress).withDestination(destination).withMessage(emailMessage)

    amazonMailClient.sendEmailAsync(request, responseHandler)
  }

  def sendEmailCommercialRequest(user: BonoboUser): Unit = {
    sendEmail("maria-livia.chiorean@guardian.co.uk", "Commercial Key Request", s"http://localhost:9000/user/${user.bonoboId}/edit")
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