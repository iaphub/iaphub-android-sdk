package com.iaphub

import android.app.Activity
import android.content.Context

internal interface Store {

  /**
   * Start
   */
  fun start(
    context: Context,
    onReceipt: (Receipt, (IaphubError?, Boolean, ReceiptTransaction?) -> Unit) -> Unit,
    onDeferredSubscriptionReplace: (purchaseToken: String, newSku: String, (IaphubError?, ReceiptTransaction?) -> Unit) -> Unit
  )

  /**
   * Stop
   */
  fun stop()

  /**
   * Buy
   */
  fun buy(activity: Activity, options: Map<String, String?>, completion: (IaphubError?, ReceiptTransaction?) -> Unit)

  /**
   * Restore
   */
  fun restore(completion: (IaphubError?) -> Unit)

  /**
   * Get products
   */
  fun getProductsDetails(skus: List<String>, completion: (IaphubError?, List<ProductDetails>?) -> Unit)

  /**
   * Get product
   */
  fun getProductDetails(sku: String, completion: (IaphubError?, ProductDetails?) -> Unit)

  /**
   * Notify billing is ready
   */
  fun notifyBillingReady(err: IaphubError? = null)

}