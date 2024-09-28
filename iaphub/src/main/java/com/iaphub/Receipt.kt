package com.iaphub

import java.util.Date

class Receipt {

  // Product id
  val token: String
  // Product sku
  val sku: String
  // Receipt context
  val context: String
  // Proration mode
  val prorationMode: String?
  // Receipt is finished
  var isFinished: Boolean
  // Receipt process date
  var processDate: Date?
  // Pricings
  var pricings: List<ProductPricing> = listOf()
  // Purchase intent id
  var purchaseIntent: String? = null

  constructor(token: String, sku: String, context: String, prorationMode: String? = null) {
    this.token = token
    this.sku = sku
    this.context = context
    this.prorationMode = prorationMode
    this.isFinished = false
    this.processDate = null
  }

  fun getData(): Map<String, Any> {
    var data = mutableMapOf(
      "token" to this.token as Any,
      "sku" to this.sku as Any,
      "context" to this.context as Any,
      "pricings" to this.pricings.map { pricing -> pricing.getData() }
    )

    if (this.prorationMode != null) {
      data["prorationMode"] = this.prorationMode as Any
    }

    if (this.purchaseIntent != null) {
      data["purchaseIntent"] = this.purchaseIntent as Any
    }

    return data
  }
}