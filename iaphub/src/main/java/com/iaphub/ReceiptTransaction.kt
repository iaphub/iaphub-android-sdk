package com.iaphub

import java.util.LinkedHashMap

open class ReceiptTransaction : ActiveProduct {

  // Transaction webhook status
  val webhookStatus: String?
  // User id
  val user: String?

  constructor(data: Map<String, Any?>): super(data) {
    this.webhookStatus = data["webhookStatus"] as? String
    this.user = data["user"] as? String
  }

  override fun getData(): Map<String, Any?> {
    var data1 = super.getData()
    var data2 = mapOf(
      "webhookStatus" to this.webhookStatus,
      "user" to this.user
    )

    return LinkedHashMap(data1).apply { putAll(data2) }
  }

}