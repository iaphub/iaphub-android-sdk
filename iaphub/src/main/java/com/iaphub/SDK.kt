package com.iaphub

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner

open class SDK: LifecycleObserver
{
  internal var store: Store? = null
  internal var user: User? = null

  internal var context: Context? = null
  internal var appId = ""
  internal var apiKey: String = ""
  internal var environment: String = "production"
  internal var allowAnonymousPurchase: Boolean = false

  internal var sdk: String = Config.sdk
  internal var sdkVersion: String = Config.sdkVersion
  internal var osVersion: String = "${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}"
  internal var isStarted: Boolean = false
  internal var deviceParams: Map<String, String> = mapOf()

  internal var onUserUpdateListener: (() -> Unit)? = null
  internal var onErrorListener: ((IaphubError) -> Unit)? = null
  internal var onReceiptListener: ((IaphubError?, Receipt?) -> Unit)? = null

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  var testing: SDKTesting = SDKTesting(this)

  /**
   * Start IAPHUB
   */
  @Synchronized
  fun start(context: Context, appId: String, apiKey: String, userId: String? = null, allowAnonymousPurchase: Boolean = false, environment: String = "production", sdk: String = "", sdkVersion: String = "") {
    this.context = context.applicationContext
    this.appId = appId
    this.apiKey = apiKey
    this.allowAnonymousPurchase = allowAnonymousPurchase
    this.environment = environment

    val oldAppId = this.appId
    // Setup configuration
    this.appId = appId
    this.apiKey = apiKey
    this.allowAnonymousPurchase = allowAnonymousPurchase
    this.environment = environment
    if (sdk != "") {
      this.sdk = Config.sdk + "/" + sdk;
    }
    if (sdkVersion != "") {
      this.sdkVersion = Config.sdkVersion + "/" + sdkVersion;
    }
    // Initialize user
    if (this.user == null || (oldAppId != appId) || (userId != null && this.user?.id != userId)) {
      this.user = User(id=userId, sdk=this, onUserUpdate={ this.onUserUpdate() })
    }
    // If it isn't been started yet
    if (this.isStarted == false) {
      // Add lifecycle observer
      Util.dispatchToMain {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
      }
      // Start store
      this.startStore()
    }
    // Mark as started
    this.isStarted = true
  }

  /**
   * Stop IAPHUB
   */
  fun stop() {
    // Only if not already stopped
    if (this.isStarted == true) {
      // Stop store
      this.store?.stop()
      // Mark as unstarted
      this.isStarted = false
    }
  }

  /**
   * Login
   */
  fun login(userId: String, completion: (IaphubError?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started")) }
    }
    // Log in user
    user.login(userId) { err ->
      Util.dispatchToMain { completion(err) }
    }
  }

  /**
   * Get user id
   */
  fun getUserId(): String? {
    return this.user?.id
  }

  /**
   * Logout
   */
  fun logout() {
    this.user?.logout()
  }

  /**
   * Set device params
   */
  fun setDeviceParams(params: Map<String, String>) {
    if (this.deviceParams.equals(params) == false) {
      this.deviceParams = params
      this.user?.resetCache()
    }
  }

  /**
   * Set user tags
   */
  fun setUserTags(tags: Map<String, String>, completion: (IaphubError?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started")) }
    }
    // Set tags
    user.setTags(tags) { err ->
      Util.dispatchToMain { completion(err) }
    }
  }

  /**
   * Buy product
   */
  fun buy(activity: Activity, sku: String, prorationMode: String? = null, crossPlatformConflict: Boolean = true, completion: (IaphubError?, ReceiptTransaction?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started"), null) }
    }
    // Check if anonymous purchases are allowed
    if (user.isAnonymous() && this.allowAnonymousPurchase == false) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.anonymous_purchase_not_allowed), null) }
    }
    // Buy product
    user.buy(activity, sku, prorationMode, crossPlatformConflict) { err, transaction ->
      Util.dispatchToMain { completion(err, transaction) }
    }
  }

  /**
   * Restore
   */
  fun restore(completion: (IaphubError?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started")) }
    }
    // Launch restore
    user.restore() { err ->
      Util.dispatchToMain { completion(err) }
    }
  }

  /**
   * Get products for sale
   */
  fun getProductsForSale(completion: (IaphubError?, List<Product>?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started"), null) }
    }
    // Return products for sale
    user.getProductsForSale() { err, products ->
      Util.dispatchToMain { completion(err, products) }
    }
  }

  /**
   * Get active products
   */
  fun getActiveProducts(includeSubscriptionStates: List<String> = listOf(), completion: (IaphubError?, List<ActiveProduct>?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started"), null) }
    }
    // Return active products
    user.getActiveProducts(includeSubscriptionStates) { err, products ->
      Util.dispatchToMain { completion(err, products) }
    }
  }

  /**
   * Get products (active and for sale)
   */
  fun getProducts(includeSubscriptionStates: List<String> = listOf(), completion: (IaphubError?, List<Product>?, List<ActiveProduct>?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started"), null, null) }
    }
    // Return products
    user.getProducts(includeSubscriptionStates) { err, productsForSale, activeProducts ->
      Util.dispatchToMain { completion(err, productsForSale, activeProducts) }
    }
  }

  /**
   * Set OnUserUpdate listener
   */
  fun setOnUserUpdateListener(listener: () -> Unit) {
    this.onUserUpdateListener = listener
  }

  /**
   * Set onErrorListener listener
   */
  fun setOnErrorListener(listener: (IaphubError) -> Unit) {
    this.onErrorListener = listener
  }

  /**
   * Set onReceiptListener listener
   */
  fun setOnReceiptListener(listener: (IaphubError?, Receipt?) -> Unit) {
    this.onReceiptListener = listener
  }

  /************************************* PRIVATE ***********************************/

  /**
   * Triggered when there is an user update
   */
  private fun onUserUpdate() {
    Util.dispatchToMain { this.onUserUpdateListener?.let { it() } }
  }

  /**
   * Triggered when the app is going to the foreground
   */
  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  private fun onAppForeground() {
    var user = this.user
    // Refresh user (only if it has already been fetched)
    if (user != null && user.fetchDate != null && this.testing.lifecycleEvent != false) {
      user.refresh()
    }
  }

  /**
   * Start store
   */
  private fun startStore() {
    var context = this.context

    if (context == null) {
      return
    }
    val store = GooglePlay(this)
    this.store = store
    store.start(
      context=context,
      // Event triggered when a new receipt is available
      onReceipt={ receipt, finish ->
        // Check the sdk is started
        val user = this.user
        if (user == null) {
          return@start finish(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started"), false, null)
        }
        // When receiving a receipt, post it
        user.postReceipt(receipt) { err, receiptResponse ->
          var error = err
          var shouldFinishReceipt = false
          var transaction: ReceiptTransaction? = null

          fun callFinish() {
            // Trigger onReceiptListener
            Util.dispatchToMain { this.onReceiptListener?.let { it(error, receipt) } }
            // Finish receipt
            finish(error, shouldFinishReceipt, transaction)
          }
          if (error == null && receiptResponse != null) {
            // Refresh user in case the user id has been updated
            user.refresh { _, _, _ ->
              // Finish receipt
              shouldFinishReceipt = true
              // Check if the receipt is invalid
              if (receiptResponse.status == "invalid") {
                error = IaphubError(IaphubErrorCode.receipt_invalid)
              }
              // Check if the receipt is failed
              else if (receiptResponse.status == "failed") {
                error = IaphubError(IaphubErrorCode.receipt_failed)
              }
              // Check if the receipt is stale
              else if (receiptResponse.status == "stale") {
                error = IaphubError(IaphubErrorCode.receipt_stale)
              }
              // Check any other status different than success
              else if (receiptResponse.status != "success") {
                error = IaphubError(IaphubErrorCode.unexpected, "Receipt validation failed")
                shouldFinishReceipt = false
              }
              // Get transaction if we're in a purchase context
              if (error == null && receipt.context == "purchase") {
                // Get the new transaction from the response
                transaction = receiptResponse.findTransactionBySku(sku = receipt.sku, filter = "new")
                // If transaction not found, look if it is a product change
                if (transaction == null) {
                  transaction = receiptResponse.findTransactionBySku(
                    sku = receipt.sku,
                    filter = "new",
                    useSubscriptionRenewalProductSku = true
                  )
                }
                // Otherwise we have an error
                if (transaction == null) {
                  // Check if it is because of a subscription already active
                  val oldTransaction = receiptResponse.findTransactionBySku(sku = receipt.sku, filter = "old")
                  if ((oldTransaction?.type == "non_consumable") || (oldTransaction?.subscriptionState != null && oldTransaction?.subscriptionState != "expired")) {
                    // Check if the transaction belongs to a different user
                    if (oldTransaction.user != null && user.iaphubId != null && oldTransaction.user != user.iaphubId) {
                      error = IaphubError(IaphubErrorCode.user_conflict)
                    } else {
                      error = IaphubError(IaphubErrorCode.product_already_purchased)
                    }
                  }
                  // Otherwise it means the product sku wasn't in the receipt
                  else {
                    error = IaphubError(IaphubErrorCode.transaction_not_found)
                  }
                }
                // If we have a transaction check that it belongs to the same user
                else if (transaction?.user != null && user.iaphubId != null && transaction?.user != user.iaphubId) {
                  error = IaphubError(IaphubErrorCode.user_conflict)
                }
              }
              // Call finish
              callFinish()
            }
          }
          else {
            callFinish()
          }
        }
      },
      onDeferredSubscriptionReplace={ purchaseToken, newSku, completion ->
        // Check the sdk is started
        val user = this.user
        if (user == null) {
          return@start completion(IaphubError(IaphubErrorCode.unexpected, "IAPHUB not started"), null)
        }
        // Get subscription
        val subscription = user.activeProducts.find { item -> item.androidToken == purchaseToken }
        if (subscription == null) {
          return@start completion(IaphubError(IaphubErrorCode.unexpected, "subscription to replace not found"), null)
        }
        // Create receipt transaction
        val data = subscription.getData() as? MutableMap<String, Any?>
        if (data == null) {
          return@start completion(IaphubError(IaphubErrorCode.unexpected, "subscription to replace data cast failed"), null)
        }
        data["subscriptionRenewalProduct"] = subscription.id
        data["subscriptionRenewalProductSku"] = newSku
        // Check purchase
        if (subscription.purchase == null) {
          // Trigger an error but do not return it
          IaphubError(IaphubErrorCode.unexpected, "purchase of subscription to replace not found")
          // Complete transaction anyway
          return@start completion(null, ReceiptTransaction(data))
        }
        // Call API to update subscriptionRenewalProductSku
        user.setSubscriptionRenewalProduct(subscription.purchase, newSku) { _, response ->
          // Update webhook status
          data["webhookStatus"] = response?.get("webhookStatus")
          // Reset user cache (because the post receipt date hasn't been updated)
          user.resetCache()
          // Refresh user
          user.refresh() { _, _, _ ->
            completion(null, ReceiptTransaction(data))
          }
        }
      }
    )
  }
}