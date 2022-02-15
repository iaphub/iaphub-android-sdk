package com.iaphub

internal class BuyRequest {

  val sku: String
  val options: Map<String, String?>
  val completion: (IaphubError?, ReceiptTransaction?) -> Unit

  constructor(sku: String, options: Map<String, String?>, completion: (IaphubError?, ReceiptTransaction?) -> Unit) {
    this.sku = sku
    this.options = options
    this.completion = completion
  }
}