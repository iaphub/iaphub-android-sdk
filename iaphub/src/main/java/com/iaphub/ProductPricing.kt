package com.iaphub

import java.math.BigDecimal

internal class ProductPricing: Parsable {

  val id: String
  val price: BigDecimal
  val currency: String
  val introPrice: BigDecimal?

  constructor(data: Map<String, Any>): super(data) {
    this.id = data["id"] as String
    this.price = (data["price"] as Double).toBigDecimal()
    this.currency = data["currency"] as String
    this.introPrice = (data["introPrice"] as? Double)?.toBigDecimal()
  }

  constructor(id: String, price: BigDecimal, currency: String, introPrice: BigDecimal? = null): super(mapOf()) {
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