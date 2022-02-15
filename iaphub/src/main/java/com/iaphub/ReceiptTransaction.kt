package com.iaphub

open class ReceiptTransaction : ActiveProduct {

  // Transaction webhook status
  val webhookStatus: String?
  // User id
  val user: String?

  constructor(data: Map<String, Any?>): super(data) {
    this.webhookStatus = data["webhookStatus"] as? String
    this.user = data["user"] as? String
  }

}