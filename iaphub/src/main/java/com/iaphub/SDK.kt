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
  internal var lang: String = ""
  internal var allowAnonymousPurchase: Boolean = false

  internal var sdk: String = Config.sdk
  internal var sdkVersion: String = Config.sdkVersion
  internal var osVersion: String = "${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}"
  internal var isStarted: Boolean = false
  internal var deviceParams: Map<String, String> = mapOf()

  internal var onUserUpdateListener: (() -> Unit)? = null
  internal var onDeferredPurchaseListener: ((ReceiptTransaction) -> Unit)? = null
  internal var onErrorListener: ((IaphubError) -> Unit)? = null
  internal var onReceiptListener: ((IaphubError?, Receipt?) -> Unit)? = null

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  var testing: SDKTesting = SDKTesting(this)

  /**
   * Start IAPHUB
   */
  @Synchronized
  fun start(context: Context, appId: String, apiKey: String, userId: String? = null, allowAnonymousPurchase: Boolean = false, enableDeferredPurchaseListener: Boolean = true, environment: String = "production", lang: String = "", sdk: String = "", sdkVersion: String = "") {
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
    if (lang != "" && this.isValidLang(lang)) {
      this.lang = lang
    }
    if (sdk != "") {
      this.sdk = Config.sdk + "/" + sdk;
    }
    if (sdkVersion != "") {
      this.sdkVersion = Config.sdkVersion + "/" + sdkVersion;
    }
    // Initialize user
    if (this.user == null || (oldAppId != appId) || (this.user?.id != userId) || (this.user?.enableDeferredPurchaseListener != enableDeferredPurchaseListener)) {
      this.user = User(
        id=userId,
        sdk=this,
        enableDeferredPurchaseListener=enableDeferredPurchaseListener,
        onUserUpdate={ this.onUserUpdate() },
        onDeferredPurchase={ transaction -> this.onDeferredPurchase(transaction) }
      )
    }
    // Otherwise reset user cache
    else {
      this.user?.resetCache()
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
   * Set language
   */
  fun setLang(lang: String): Boolean {
    if (!this.isValidLang(lang)) {
      return false
    }
    if (lang != this.lang) {
      this.lang = lang
      val user = this.user
      if (user != null) {
        this.user?.resetCache()
      }
    }
    return true
  }

  /**
   * Login
   */
  fun login(userId: String, completion: (IaphubError?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "login failed")) }
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
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "setUserTags failed")) }
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
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "buy failed"), null) }
    }
    // Check if anonymous purchases are allowed
    if (user.isAnonymous() && this.allowAnonymousPurchase == false) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.anonymous_purchase_not_allowed), null) }
    }
    // Buy product
    user.buy(activity, sku, prorationMode, crossPlatformConflict) { err, transaction ->
      // Refresh store if we receive a product_already_owned error (it could be because the product hasn't been consumed)
      if (err?.code == "product_already_owned") {
        this.store?.refresh()
      }
      // Call completion
      Util.dispatchToMain { completion(err, transaction) }
    }
  }

  /**
   * Restore
   */
  fun restore(completion: (IaphubError?, RestoreResponse?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "restore failed"), null) }
    }
    // Launch restore
    user.restore() { err, response ->
      Util.dispatchToMain { completion(err, response) }
    }
  }

  /**
   * Show manage subscriptions
   */
  fun showManageSubscriptions(sku: String? = null, completion: (IaphubError?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "showManageSubscriptions failed")) }
    }
    // Launch showManageSubscriptions
    user.showManageSubscriptions(sku) { err ->
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
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "getProductsForSale failed"), null) }
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
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "getActiveProducts failed"), null) }
    }
    // Return active products
    user.getActiveProducts(includeSubscriptionStates) { err, products ->
      Util.dispatchToMain { completion(err, products) }
    }
  }

  /**
   * Get products (active and for sale)
   */
  fun getProducts(includeSubscriptionStates: List<String> = listOf(), completion: (IaphubError?, Products?) -> Unit) {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return Util.dispatchToMain { completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "getProducts failed"), null) }
    }
    // Return products
    user.getProducts(includeSubscriptionStates) { err, products ->
      Util.dispatchToMain { completion(err, products) }
    }
  }

  /**
   * Get billing status
   */
  fun getBillingStatus(): BillingStatus {
    // Check the sdk is started
    val user = this.user
    if (user == null) {
      return BillingStatus(error=IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing, "getBillingStatus failed"))
    }
    // Return report
    return user.getBillingStatus()
  }

  /**
   * Get SDK version
   */
  fun getSDKVersion(): String {
    return Config.sdkVersion
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

  /**
   * Set onDeferredPurchase listener
   */
  fun setOnDeferredPurchaseListener(listener: (ReceiptTransaction) -> Unit) {
    this.onDeferredPurchaseListener = listener
  }

  /************************************* PRIVATE ***********************************/

  /**
   * Check if the lang code is valid
   */
  private fun isValidLang(lang: String): Boolean {
    // Regular expression to match "xx" or "xx-XX" formats
    val regex = Regex("^[a-z]{2}(-[A-Z]{2})?$")
    return regex.matches(lang)
  }

  /**
   * Triggered when there is an user update
   */
  private fun onUserUpdate() {
    Util.dispatchToMain { this.onUserUpdateListener?.let { it() } }
  }

  /**
   * Triggered when there is an user update
   */
  private fun onDeferredPurchase(transaction: ReceiptTransaction) {
    Util.dispatchToMain { this.onDeferredPurchaseListener?.let { it(transaction) } }
  }

  /**
   * Triggered when the app is going to the foreground
   */
  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  private fun onAppForeground() {
    var user = this.user
    // Refresh user (only if it has already been fetched)
    if (user != null && user.isInitialized && this.testing.lifecycleEvent != false) {
      user.refresh(UserFetchContext(
        source = UserFetchContextSource.PRODUCTS,
        properties = mutableListOf(UserFetchContextProperty.ON_FOREGROUND)
      ), interval = 2) { _, _, _ ->
        // Refresh store
        this.store?.refresh()
      }
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
          return@start finish(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing), false, null)
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
            user.refresh(UserFetchContext(source = UserFetchContextSource.RECEIPT)) { _, _, _ ->
              // Finish receipt if it is a success
              if (receiptResponse.status == "success") {
                shouldFinishReceipt = true
              }
              // Check if the receipt is invalid
              else if (receiptResponse.status == "invalid") {
                error = IaphubError(IaphubErrorCode.receipt_failed, IaphubReceiptErrorCode.receipt_invalid, params=mapOf("context" to receipt.context), silent=receipt.context != "purchase")
                shouldFinishReceipt = true
              }
              // Check if the receipt is expired (the receipt is expired and it cannot be used anymore)
              else if (receiptResponse.status == "expired") {
                error = IaphubError(IaphubErrorCode.receipt_failed, IaphubReceiptErrorCode.receipt_expired, params=mapOf("context" to receipt.context), silent=receipt.context != "purchase")
                shouldFinishReceipt = true
              }
              // Check if the receipt is stale (the receipt is valid but no purchases still valid were found)
              else if (receiptResponse.status == "stale") {
                error = IaphubError(IaphubErrorCode.receipt_failed, IaphubReceiptErrorCode.receipt_stale, params=mapOf("context" to receipt.context), silent=receipt.context != "purchase")
                shouldFinishReceipt = true
              }
              // Check if the receipt is failed
              else if (receiptResponse.status == "failed") {
                error = IaphubError(IaphubErrorCode.receipt_failed, IaphubReceiptErrorCode.receipt_failed, params = mapOf("context" to receipt.context))
              }
              // Check if the receipt is processing
              else if (receiptResponse.status == "processing") {
                error = IaphubError(IaphubErrorCode.receipt_failed, IaphubReceiptErrorCode.receipt_processing, params=mapOf("context" to receipt.context), silent=receipt.context != "purchase")
              }
              // Check if the receipt is deferred (its final status is pending external action)
              else if (receiptResponse.status == "deferred") {
                error = IaphubError(IaphubErrorCode.deferred_payment, params=mapOf("context" to receipt.context), silent=true)
              }
              // Check any other status different than success
              else {
                error = IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.receipt_validation_response_invalid, "status: ${receiptResponse.status}", params=mapOf("context" to receipt.context))
              }
              // If there is no error, try to find the transaction
              if (error == null) {
                // Look first in the new transactions
                transaction = receiptResponse.findTransactionBySku(sku = receipt.sku, filter = "new", ignoreAndroidBasePlanId = true)
                // If the transaction hasn't been found
                if (transaction == null) {
                  // If it is purchase, look for a product change
                  if (receipt.context == "purchase") {
                    transaction = receiptResponse.findTransactionBySku(
                      sku = receipt.sku,
                      filter = "new",
                      useSubscriptionRenewalProductSku = true,
                      ignoreAndroidBasePlanId = true
                    )
                  }
                  // Otherwise look in the old transactions
                  else {
                    transaction = receiptResponse.findTransactionBySku(sku = receipt.sku, filter = "old", ignoreAndroidBasePlanId = true)
                  }
                }
                // If it is a purchase, check for errors
                if (receipt.context == "purchase") {
                  // If we didn't find any transaction, we have an error
                  if (transaction == null) {
                    // Check if it is because of a subscription already active
                    val oldTransaction = receiptResponse.findTransactionBySku(sku = receipt.sku, filter = "old", ignoreAndroidBasePlanId = true)
                    if ((oldTransaction?.type == "non_consumable") || (oldTransaction?.subscriptionState != null && oldTransaction?.subscriptionState != "expired")) {
                      // Check if the transaction belongs to a different user
                      if (oldTransaction.user != null && user.iaphubId != null && oldTransaction.user != user.iaphubId) {
                        error = IaphubError(IaphubErrorCode.user_conflict, params=mapOf("loggedUser" to user.iaphubId, "transactionUser" to oldTransaction.user))
                      } else {
                        error = IaphubError(IaphubErrorCode.product_already_purchased, params=mapOf("sku" to receipt.sku))
                      }
                    }
                    // Otherwise it means the product sku wasn't in the receipt
                    else {
                      error = IaphubError(IaphubErrorCode.transaction_not_found, params=mapOf("sku" to receipt.sku))
                    }
                  }
                  // If we have a transaction check that it belongs to the same user
                  else if (transaction?.user != null && user.iaphubId != null && transaction?.user != user.iaphubId) {
                    error = IaphubError(IaphubErrorCode.user_conflict, params=mapOf("loggedUser" to user.iaphubId, "transactionUser" to transaction?.user))
                  }
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
      }
    )
    // Refresh store
    store.refresh()
  }
}