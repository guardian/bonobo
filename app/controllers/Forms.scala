package controllers

import models.Tier

object Forms {

  case class CreateUserFormData(name: String, email: String, companyName: String, companyUrl: String, productName: String, productUrl: String, tier: Tier, key: Option[String] = None)

  case class EditUserFormData(name: String, email: String, companyName: String, companyUrl: String)

  case class CreateKeyFormData(key: Option[String], tier: Tier, productName: String, productUrl: String)

  case class EditKeyFormData(key: String, productName: String, productUrl: String, requestsPerDay: Int, requestsPerMinute: Int, tier: Tier, defaultRequests: Boolean, status: String) {
    def validateRequests: Boolean = requestsPerDay >= requestsPerMinute
  }

  case class SearchFormData(query: String)

  case class DeveloperCreateKeyFormData(name: String, email: String, productName: String, productUrl: String, companyName: String, companyUrl: String, acceptTerms: Boolean)

  case class CommercialRequestKeyFormData(name: String, email: String, productName: String, productUrl: String, companyName: String, companyUrl: String,
    businessArea: String, monthlyUsers: Int, commercialModel: String, content: String, articlesPerDay: Int, contentFormat: String, acceptTerms: Boolean)

}
