package com.iaphub

internal class ProductPricing: Parsable {

  val id: String
  val price: Double
  val currency: String
  val introPrice: Double?

  constructor(data: Map<String, Any>): super(data) {
    this.id = data["id"] as String
    this.price = data["price"] as Double
    this.currency = data["currency"] as String
    this.introPrice = data["introPrice"] as? Double
  }

  constructor(id: String, price: Double, currency: String, introPrice: Double? = null): super(mapOf()) {
    this.id = id
    this.price = price
    this.currency = currency
    this.introPrice = introPrice
  }

  fun getData(): Map<String, Any> {
    var dic = mutableMapOf(
      "id" to this.id as Any,
      "price" to this.price as Any,
      "currency" to this.currency as Any
    )
    // Add only intro price if defined
    if (this.introPrice != null) {
      dic["introPrice"] = this.introPrice as Any
    }
    return dic
  }
}