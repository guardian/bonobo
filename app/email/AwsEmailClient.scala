package email

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClient
import com.amazonaws.services.simpleemail.model._
import models.BonoboUser
import play.api.Logger
import play.api.mvc.RequestHeader

import scala.concurrent.{ Future, Promise }

trait MailClient {
  def sendEmailCommercialRequest(user: BonoboUser, productName: String, productUrl: String)(implicit request: RequestHeader): Future[SendEmailResult]

  def sendEmailNewKey(toEmail: String, key: String): Future[SendEmailResult]
}

class AwsEmailClient(amazonMailClient: AmazonSimpleEmailServiceAsyncClient, fromAddress: String, enableEmail: Boolean) extends MailClient {

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
        Logger.warn(s"Could not send mail: ${e.getMessage}\n" +
          s"The failing request was $request")
        promise.failure(e)
      }

      override def onSuccess(request: SendEmailRequest, result: SendEmailResult) = {
        Logger.info("The email has been successfully sent")
        promise.success(result)
      }
    }
    if (enableEmail) {
      amazonMailClient.sendEmailAsync(request, responseHandler)
    }
    else {
      Logger.info("Emails are not enabled. The email was not sent.")
    }
    promise.future
  }

  def sendEmailCommercialRequest(user: BonoboUser, productName: String, productUrl: String)(implicit request: RequestHeader): Future[SendEmailResult] = {
    val message = s"""Sent at: ${user.additionalInfo.createdAt.toString("dd-MM-yyyy hh:mma")}
      |Name: ${user.name}
      |Email: ${user.email}
      |Company name: ${user.companyName}
      |Company URL: ${user.companyUrl}
      |Product name: $productName
      |Product URL: $productUrl
      |Business area: ${user.additionalInfo.businessArea.getOrElse('-')}
      |Commercial model: ${user.additionalInfo.commercialModel.getOrElse('-')}
      |Content type: ${user.additionalInfo.content.getOrElse('-')}
      |Monthly users: ${user.additionalInfo.monthlyUsers.getOrElse('-')}
      |Articles per day: ${user.additionalInfo.articlesPerDay.getOrElse('-')}
      |${controllers.routes.Application.editUserPage(user.bonoboId).absoluteURL()}""".stripMargin
    sendEmail("content.delivery@theguardian.com", "Commercial Key Request", message)
  }

  def sendEmailNewKey(toEmail: String, key: String): Future[SendEmailResult] = {
    val message =
      s"""Hello from the Guardian.
         |
         |Thank you for registering with the open platform.
         |
         |A new key has been created for you: $key
         |
         |You can try this key by accessing https://content.guardianapis.com/search?api-key=$key in your browser.
         |
         |For more details on how to use the open platform API, check out the documentation available at http://open-platform.theguardian.com/documentation/
         |""".stripMargin
    sendEmail(toEmail, "New Key Created", message)
  }
}
