package com.iaphub

class RestoreResponse {

  // New purchases
  val newPurchases: List<ReceiptTransaction>
  // Existing active products transferred to the user
  val transferredActiveProducts: List<ActiveProduct>

  constructor(newPurchases: List<ReceiptTransaction>, transferredActiveProducts: List<ActiveProduct>) {
    this.newPurchases = newPurchases
    this.transferredActiveProducts = transferredActiveProducts
  }

  fun getData(): Map<String, Any?> {
    return mapOf(
      "newPurchases" to this.newPurchases.map { purchase -> purchase.getData() },
      "transferredActiveProducts" to this.transferredActiveProducts.map { product -> product.getData() }
    )
  }

}