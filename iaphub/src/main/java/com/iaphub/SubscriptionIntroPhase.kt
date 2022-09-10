package com.iaphub

class SubscriptionIntroPhase: Parsable {

  // Phase type (Possible values: 'trial', 'intro')
  var type: String
  // Phase price
  var price: Double
  // Phase currency
  var currency: String
  // Phase localized price
  var localizedPrice: String
  // Phase duration cycle specified in the ISO 8601 format
  var cycleDuration: String
  // Phase cycle count
  var cycleCount: Int
  // Phase payment type (Possible values: 'as_you_go', 'upfront')
  var payment: String

  constructor(data: Map<String, Any?>): super(data) {
    this.type = data["type"] as String
    this.price = data["price"] as Double
    this.currency = data["currency"] as String
    this.localizedPrice = data["localizedPrice"] as String
    this.cycleDuration = data["cycleDuration"] as String
    this.cycleCount = data["cycleCount"] as? Int ?: (data["cycleCount"] as Double).toInt() // Gson converts int to double, we must support both types
    this.payment = data["payment"] as String
  }

  open fun getData(): Map<String, Any> {
    return mapOf(
      "type" to this.type,
      "price" to this.price,
      "currency" to this.currency,
      "localizedPrice" to this.localizedPrice,
      "cycleDuration" to this.cycleDuration,
      "cycleCount" to this.cycleCount,
      "payment" to this.payment
    )
  }

}