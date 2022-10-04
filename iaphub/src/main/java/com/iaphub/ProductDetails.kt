package com.iaphub;

open class ProductDetails: Parsable {

  // Product sku
  var sku: String
  // Product localized title
  var localizedTitle: String? = null
  // Product localized description
  var localizedDescription: String? = null
  // Product price
  var price: Double? = null
  // Product currency
  var currency: String? = null
  // Product localized price
  var localizedPrice: String? = null
  // Duration of the subscription cycle specified in the ISO 8601 format
  var subscriptionDuration: String? = null
  // Subscription intro phases
  var subscriptionIntroPhases: List<SubscriptionIntroPhase>? = null

  constructor(data: Map<String, Any?>, allowEmptySku: Boolean = false): super(data) {
    this.sku = if (allowEmptySku) data["sku"] as? String ?: "" else data["sku"] as String
    this.localizedTitle = data["localizedTitle"] as? String
    this.localizedDescription = data["localizedDescription"] as? String
    this.price = data["price"] as? Double
    this.currency = data["currency"] as? String
    this.localizedPrice = data["localizedPrice"] as? String
    this.subscriptionDuration = data["subscriptionDuration"] as? String
    this.subscriptionIntroPhases = if (data["subscriptionIntroPhases"] != null) Util.parseItems(data["subscriptionIntroPhases"], true) { err, item ->
      IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.intro_phase_parsing_failed, "\n\n${err.stackTraceToString()}", mapOf("item" to item))
    } else null
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
      "subscriptionIntroPhases" to this.subscriptionIntroPhases?.map { introPhase -> introPhase.getData() }
    )
  }

  open fun setDetails(details: ProductDetails) {
    this.localizedTitle = details.localizedTitle
    this.localizedDescription = details.localizedDescription
    this.price = details.price
    this.currency = details.currency
    this.localizedPrice = details.localizedPrice
    this.subscriptionDuration = details.subscriptionDuration
    this.subscriptionIntroPhases = details.subscriptionIntroPhases
  }


}