package com.iaphub

import java.math.BigDecimal

internal class ProductPricing: Parsable {

  val id: String
  val price: BigDecimal
  val currency: String

  constructor(data: Map<String, Any>): super(data) {
    this.id = data["id"] as String
    this.price = (data["price"] as Double).toBigDecimal()
    this.currency = data["currency"] as String
  }

  constructor(id: String, price: BigDecimal, currency: String): super(mapOf()) {
    this.id = id
    this.price = price
    this.currency = currency
  }

  fun getData(): Map<String, Any> {
    return mapOf(
      "id" to this.id as Any,
      "price" to this.price as Any,
      "currency" to this.currency as Any
    )
  }
}