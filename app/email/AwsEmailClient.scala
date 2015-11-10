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
    val message = s"Sent at: ${user.additionalInfo.createdAt.toString("dd-MM-yyyy hh:mma")}\n" +
      s"Name: ${user.name}\n" +
      s"Email: ${user.email}\n" +
      s"Product name: ${user.productName}\n" +
      s"Product URL: ${user.productUrl}\n" +
      s"Company name: ${user.companyName}\n" +
      s"Company URL: ${user.companyUrl.getOrElse('-')}\n" +
      s"Business area: ${user.additionalInfo.businessArea.getOrElse('-')}\n" +
      s"Commercial model: ${user.additionalInfo.commercialModel.getOrElse('-')}\n" +
      s"Content type: ${user.additionalInfo.content.getOrElse('-')}\n" +
      s"Monthly users: ${user.additionalInfo.monthlyUsers.getOrElse('-')}\n" +
      s"Articles per day: ${user.additionalInfo.articlesPerDay.getOrElse('-')}\n" +
      s"http://localhost:9000/user/${user.bonoboId}/edit"
    sendEmail("maria-livia.chiorean@guardian.co.uk", "Commercial Key Request", message)
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