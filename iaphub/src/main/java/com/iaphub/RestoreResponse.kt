package com.iaphub

class RestoreResponse {

  // New purchases
  internal val newPurchases: List<ReceiptTransaction>
  // Existing active products transferred to the user
  internal val transferredActiveProducts: List<ActiveProduct>

  constructor(newPurchases: List<ReceiptTransaction>, transferredActiveProducts: List<ActiveProduct>) {
    this.newPurchases = newPurchases
    this.transferredActiveProducts = transferredActiveProducts
  }

}