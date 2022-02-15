package com.iaphub;

import java.math.BigDecimal

open class ProductDetails: Parsable {

  // Product sku
  var sku: String
  // Product localized title
  var localizedTitle: String? = null
  // Product localized description
  var localizedDescription: String? = null
  // Product price
  var price: BigDecimal? = null
  // Product currency
  var currency: String? = null
  // Product localized price
  var localizedPrice: String? = null
  // Duration of the subscription cycle specified in the ISO 8601 format
  var subscriptionDuration: String? = null
  // Duration of the trial specified in the ISO 8601 format
  var subscriptionTrialDuration: String? = null
  // Localized introductory price
  var subscriptionIntroPrice: BigDecimal? = null
  // Introductory price amount
  var subscriptionIntroLocalizedPrice: String? = null
  // Duration of an introductory cycle specified in the ISO 8601 format
  var subscriptionIntroDuration: String? = null
  // Number of cycles in the introductory offer
  var subscriptionIntroCycles: Int? = null
  // Payment type of the introductory offer ("as_you_go", "upfront")
  var subscriptionIntroPayment: String? = null

  constructor(sku: String) : super(mapOf()) {
    this.sku = sku
  }

  constructor(data: Map<String, Any?>): super(data) {
    this.sku = data["sku"] as String
    this.localizedTitle = data["localizedTitle"] as? String
    this.localizedDescription = data["localizedDescription"] as? String
    this.price = (data["price"] as? Double)?.toBigDecimal()
    this.currency = data["currency"] as? String
    this.localizedPrice = data["localizedPrice"] as? String
    this.subscriptionDuration = data["subscriptionDuration"] as? String
    this.subscriptionTrialDuration = data["subscriptionTrialDuration"] as? String
    this.subscriptionIntroPrice = (data["subscriptionIntroPrice"] as? Double)?.toBigDecimal()
    this.subscriptionIntroLocalizedPrice = data["subscriptionIntroLocalizedPrice"] as? String
    this.subscriptionIntroDuration = data["subscriptionIntroDuration"] as? String
    this.subscriptionIntroCycles = data["subscriptionIntroCycles"] as? Int
    this.subscriptionIntroPayment = data["subscriptionIntroPayment"] as? String
  }

  open fun getData(): Map<String, Any?> {
    return mapOf(
      "sku" to this.sku as? Any?,
      "price" to this.price as? Any?,
      "currency" to this.currency as? Any?,
      "localizedPrice" to this.localizedPrice as? Any?,
      "localizedTitle" to this.localizedTitle as? Any?,
      "localizedDescription" to this.localizedDescription as? Any?,
      "subscriptionDuration" to this.subscriptionDuration as? Any?,
      "subscriptionIntroPrice" to this.subscriptionIntroPrice as? Any?,
      "subscriptionIntroLocalizedPrice" to this.subscriptionIntroLocalizedPrice as? Any?,
      "subscriptionIntroPayment" to this.subscriptionIntroPayment as? Any?,
      "subscriptionIntroDuration" to this.subscriptionIntroDuration as? Any?,
      "subscriptionIntroCycles" to this.subscriptionIntroCycles as? Any?,
      "subscriptionTrialDuration" to this.subscriptionTrialDuration as? Any?
    )
  }

  open fun setDetails(details: ProductDetails) {
    this.localizedTitle = details.localizedTitle
    this.localizedDescription = details.localizedDescription
    this.price = details.price
    this.currency = details.currency
    this.localizedPrice = details.localizedPrice
    this.subscriptionDuration = details.subscriptionDuration
    this.subscriptionTrialDuration = details.subscriptionTrialDuration
    this.subscriptionIntroPrice = details.subscriptionIntroPrice
    this.subscriptionIntroLocalizedPrice = details.subscriptionIntroLocalizedPrice
    this.subscriptionIntroDuration = details.subscriptionIntroDuration
    this.subscriptionIntroCycles = details.subscriptionIntroCycles
    this.subscriptionIntroPayment = details.subscriptionIntroPayment
  }

}