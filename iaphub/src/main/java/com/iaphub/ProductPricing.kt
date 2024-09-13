package com.iaphub

class ProductPricing {

  val id: String?
  val sku: String
  val price: Double
  val currency: String
  val introPrice: Double?

  constructor(id: String?, sku: String, price: Double, currency: String, introPrice: Double? = null) {
    this.id = id
    this.sku = sku
    this.price = price
    this.currency = currency
    this.introPrice = introPrice
  }

  fun getData(): Map<String, Any> {
    var dic = mutableMapOf(
      "sku" to this.sku as Any,
      "price" to this.price as Any,
      "currency" to this.currency as Any
    )
    // Add id if defined
    if (this.id != null) {
      dic["id"] = this.id as Any
    }
    // Add intro price if defined
    if (this.introPrice != null) {
      dic["introPrice"] = this.introPrice as Any
    }
    return dic
  }
}