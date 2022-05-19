package com.iaphub

import java.util.*

open class ActiveProduct : Product {

  // Purchase id
  val purchase: String?
  // Purchase date
  val purchaseDate: Date?
  // Platform of the purchase
  val platform: String?
  // Android token
  val androidToken: String?

  // Subscription expiration date
  val expirationDate: Date?
  // If the subscription will auto renew
  val isSubscriptionRenewable: Boolean
  // Subscription product of the next renewal (only defined if different than the current product)
  val subscriptionRenewalProduct: String?
  // SubscriptionRenewalProduct sku
  val subscriptionRenewalProductSku: String?
  // Subscription state
  val subscriptionState: String?

  constructor(data: Map<String, Any?>): super(data) {
    this.purchase = data["purchase"] as? String
    this.purchaseDate = Util.dateFromIsoString(data["purchaseDate"]) { exception ->
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.date_parsing_failed,
        message="issue on active product purchase date, $exception",
        params=mapOf("purchaseDate" to data["purchaseDate"], "purchase" to this.purchase)
      )
    }
    this.platform = data["platform"] as? String
    this.androidToken = data["androidToken"] as? String
    // The following properties are for subscriptions only
    this.expirationDate = Util.dateFromIsoString(str=data["expirationDate"], allowNull=!this.type.contains("subscription")) { exception ->
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.date_parsing_failed,
        message = "issue on active product expiration date, $exception",
        params = mapOf("expirationDate" to data["expirationDate"], "purchase" to this.purchase)
      )
    }
    this.isSubscriptionRenewable = (data["isSubscriptionRenewable"] as? Boolean) ?: false
    this.subscriptionRenewalProduct = data["subscriptionRenewalProduct"] as? String
    this.subscriptionRenewalProductSku = data["subscriptionRenewalProductSku"] as? String
    this.subscriptionState = data["subscriptionState"] as? String
  }

  override fun getData(): Map<String, Any?> {
    var data1 = super.getData()
    var data2 = mapOf(
      "purchase" to this.purchase as? Any?,
      "purchaseDate" to Util.dateToIsoString(this.purchaseDate) as? Any?,
      "platform" to this.platform as? Any?,
      "expirationDate" to Util.dateToIsoString(this.expirationDate) as? Any?,
      "isSubscriptionRenewable" to this.isSubscriptionRenewable as? Any?,
      "subscriptionRenewalProduct" to this.subscriptionRenewalProduct as? Any?,
      "subscriptionRenewalProductSku" to this.subscriptionRenewalProductSku as? Any?,
      "subscriptionState" to this.subscriptionState as? Any?,
      "androidToken" to this.androidToken as? Any?
    )

    return LinkedHashMap(data1).apply { putAll(data2) }
  }

}