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
    var basePlanId = this.sku.substringAfter(":", "")

    // Get default base plan if not defined
    if (basePlanId == "") {
      val defaultBasePlanOffer = this.details.subscriptionOfferDetails?.get(0)

      if (defaultBasePlanOffer != null) {
        basePlanId = defaultBasePlanOffer.basePlanId
      }
    }
    // Get offers of base plan
    val offersOfBasePlanId = this.details.subscriptionOfferDetails?.filter { offer ->
      offer.basePlanId == basePlanId
    }
    // Look if any other offer than the default one
    var offer = offersOfBasePlanId?.find { offer ->
      offer.offerId != null
    }
    // If none found, use the default one
    if (offer == null) {
      offer = offersOfBasePlanId?.find { offer ->
        offer.offerId == null
      }
    }

    return offer
  }

}
