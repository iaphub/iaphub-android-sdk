package com.iaphub

import java.util.Date

class Receipt {

  // Product id
  val token: String
  // Product sku
  val sku: String
  // Receipt context
  val context: String
  // Receipt is finished
  var isFinished: Boolean
  // Receipt process date
  var processDate: Date?

  constructor(token: String, sku: String, context: String) {
    this.token = token
    this.sku = sku
    this.context = context
    this.isFinished = false
    this.processDate = null
  }

  fun getData(): Map<String, Any> {
    return mapOf(
      "token" to this.token as Any,
      "sku" to this.sku as Any,
      "context" to this.context as Any
    )
  }
}