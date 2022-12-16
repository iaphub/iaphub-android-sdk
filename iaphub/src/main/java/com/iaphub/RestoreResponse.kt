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

}