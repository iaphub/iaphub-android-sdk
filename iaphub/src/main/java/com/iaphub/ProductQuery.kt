package com.iaphub;

import android.util.Log

class ProductQuery {

  val sku: String
  val details: com.android.billingclient.api.ProductDetails

  constructor(sku: String, details: com.android.billingclient.api.ProductDetails) {
    this.sku = sku
    this.details = details
  }

  /**
   * Get the subscription offer
   */
  fun getSubscriptionOffer():  com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails? {
    val basePlanId = this.sku.substringAfter(":", "")

    return this.details.subscriptionOfferDetails?.reversed()?.find {
      // If no base plan id, return the first occurence
      if (basePlanId == "") {
        return@find true
      }
      return@find it.basePlanId == basePlanId
    }
  }

}
