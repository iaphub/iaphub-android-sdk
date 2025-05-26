package com.iaphub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import com.android.billingclient.api.*
import java.lang.Exception
import java.util.*
import kotlin.concurrent.timerTask

internal class ReceiptProcessing {
  val token: String
  val date: Date

  constructor(token: String) {
    this.token = token
    this.date = Date()
  }
}

internal class GooglePlay: Store, PurchasesUpdatedListener, BillingClientStateListener {

  private var sdk: SDK
  private var onReceipt: ((Receipt, (IaphubError?, Boolean, ReceiptTransaction?) -> Unit) -> Unit)? = null
  private var onBillingReady: MutableList<(IaphubError?, BillingClient?) -> Unit> = mutableListOf()
  private var onBillingReadyTimer: Timer? = null
  private var billing: BillingClient? = null
  private var buyRequest: BuyRequest? = null
  private var restoredPurchases: List<Purchase>? = null
  private var purchaseQueue: Queue<Purchase>? = null
  private var lastReceipt: Receipt? = null
  private var cachedProductQueries: MutableMap<String, ProductQuery> = mutableMapOf()
  private var isRestoring: Boolean = false
  private var isRefreshing: Boolean = false
  private var isStartingConnection: Boolean = false
  private var hasBillingUnavailable: Boolean = false
  private var refreshedReceipts: MutableList<ReceiptProcessing> = mutableListOf()
  private var failedReceipts: MutableList<ReceiptProcessing> = mutableListOf()

  /**
   * Constructor
   */
  constructor(sdk: SDK) {
    this.sdk = sdk
  }

  /**
   * Start
   */
  @Synchronized
  override fun start(
    context: Context,
    onReceipt: (Receipt, (IaphubError?, Boolean, ReceiptTransaction?) -> Unit) -> Unit
  ) {
    // Check it isn't already started
    if (this.billing != null) {
      return
    }
    // Save callbacks
    this.onReceipt = onReceipt
    // Create purchased transaction queue
    this.purchaseQueue = Queue() { item, completion ->
      this.processPurchase(item.data, item.date, completion)
    }
    // Create billing instance
    this.billing = BillingClient.newBuilder(context).enablePendingPurchases().setListener(this).build()
  }

  /**
   * Stop
   */
  @Synchronized
  override fun stop() {
    if (this.billing == null) {
      return
    }
    try {
      this.billing?.endConnection()
      this.billing = null
    }
    catch(err: Exception) {
      IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.end_connection_failed, err.message ?: "")
    }
  }

  /**
   * Buy
   */
  @Synchronized
  override fun buy(activity: Activity, product: Product, options: Map<String, String?>, completion: (IaphubError?, ReceiptTransaction?) -> Unit) {
    // Return an error if a buy request is currently processing
    if (this.buyRequest != null) {
      return completion(IaphubError(IaphubErrorCode.buy_processing), null)
    }
    // Return an error if a restore is currently processing
    if (this.isRestoring) {
      return completion(IaphubError(IaphubErrorCode.restore_processing), null)
    }
    // Save buy request
    this.buyRequest = BuyRequest(product.sku, options, completion)
    // Handle mock
    if (this.sdk.testing.storeLibraryMock == true) {
      val self = this
      Timer().schedule(timerTask {
        self.purchaseQueue?.add(self.createFakePurchase(product.sku))
      }, 200)
      return
    }
    // Check subscription replacement
    this.checkSubscriptionReplacement(product) { err, oldPurchaseToken ->
      // Check error
      if (err != null) {
        this.buyRequest = null
        return@checkSubscriptionReplacement completion(err, null)
      }
      // Build billing flow params
      this.buildBillingFlowParams(sku=product.sku, oldPurchaseToken=oldPurchaseToken, prorationMode=options["prorationMode"]) { err, billingFlowParams ->
        // Check error
        if (err != null || billingFlowParams == null) {
          this.buyRequest = null
          return@buildBillingFlowParams completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
        }
        // Launch billing flow
        this.launchBillingFlow(activity, billingFlowParams) { err ->
          if (err != null) {
            this.buyRequest = null
            completion(err, null)
          }
        }
      }
    }
  }

  /**
   * Restore
   */
  @Synchronized
  override fun restore(completion: (IaphubError?) -> Unit) {
    // Return an error if a restore is currently processing
    if (this.isRestoring) {
      return completion(IaphubError(IaphubErrorCode.restore_processing))
    }
    // Return an error if a buy request is currently processing
    if (this.buyRequest != null) {
      return completion(IaphubError(IaphubErrorCode.buy_processing))
    }
    // Mark as restoring
    this.isRestoring = true
    // Get purchases
    this.getPurchases() { err, purchases ->
      if (err != null) {
        this.isRestoring = false
        return@getPurchases completion(err)
      }
      this.purchaseQueue?.pause()
      this.restoredPurchases = purchases
      purchases.forEach { purchase -> this.purchaseQueue?.add(purchase)}
      this.purchaseQueue?.resume() { ->
        this.restoredPurchases = null
        this.isRestoring = false
        completion(null)
      }
    }
  }

  /**
   * Refresh
   */
  @Synchronized
  override fun refresh() {
    // Do not refresh in some cases
    if (this.isRefreshing || this.isRestoring || this.buyRequest != null) {
      return
    }
    // Mark as refreshing
    this.isRefreshing = true
    // Get purchases
    this.getPurchases() { err, purchases ->
      if (err != null) {
        this.isRefreshing = false
        return@getPurchases
      }
      purchases.forEach { purchase ->
        val refreshedReceipt = this.refreshedReceipts.find { receipt -> receipt.token == purchase.purchaseToken }
        val failedReceipt = this.failedReceipts.find { receipt -> receipt.token == purchase.purchaseToken }
        val failedReceiptRetryDuration = 1000 * 60 * 60 * 24 * 3

        // Process purchase if not processed before or processed with an error less than 72 hours ago
        if (refreshedReceipt == null || (failedReceipt != null && Date(failedReceipt.date.getTime() + failedReceiptRetryDuration).after(Date()))) {
          this.purchaseQueue?.add(purchase)
        }
      }
      // Update refreshed order ids
      this.refreshedReceipts = purchases.map { purchase -> ReceiptProcessing(purchase.purchaseToken) }.toMutableList()
      // Mark refreshing as done
      this.isRefreshing = false
    }
  }

  /**
   * Show subscriptions manage
   */
  @Synchronized
  override fun showManageSubscriptions(sku: String?, completion: (IaphubError?) -> Unit) {
    val context = this.sdk.context
    var error: IaphubError? = null

    try {
      var url = "https://play.google.com/store/account/subscriptions"
      val bundleId = context?.packageName
      if (sku != null && bundleId != null) {
        url = "${url}?sku=${sku}&package=${bundleId}"
      }
      val intent = Intent(Intent.ACTION_VIEW,  Uri.parse(url))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (context != null) {
        startActivity(context, intent, null)
      }
      else {
        error = IaphubError(IaphubErrorCode.manage_subscriptions_unavailable, null, message="context not found")
      }
    }
    catch (err: Exception) {
      error = IaphubError(IaphubErrorCode.manage_subscriptions_unavailable, null, message="$err")
    }
    completion(error)
  }

  /**
   * Get products details
   */
  override fun getProductsDetails(skus: List<String>, completion: (IaphubError?, List<ProductDetails>?) -> Unit) {
    // Return mocked products when enabled
    if (this.sdk.testing.storeLibraryMock == true) {
      return this.whenBillingReady { err, _ ->
        // Return an error if the billing isn't ready
        if (err != null) {
          return@whenBillingReady completion(err, null)
        }
        completion(null, this.sdk.testing.mockedProductDetails)
      }
    }
    // Get skus details
    this.getProductQueries(skus) { err, productQueries ->
      // Check error
      if (err != null || productQueries == null) {
        return@getProductQueries completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Convert list to product details
      val productsDetails: List<ProductDetails?> = productQueries.map { productQuery ->
        try {
          var data: MutableMap<String, Any?> = mutableMapOf()
          // Get general info
          data["sku"] = productQuery.sku
          data["localizedTitle"] = productQuery.details.title
          data["localizedDescription"] = productQuery.details.description
          // Get consumable info
          val oneTimePurchaseOfferDetails = productQuery.details.oneTimePurchaseOfferDetails
          if (oneTimePurchaseOfferDetails != null) {
            data["price"] = oneTimePurchaseOfferDetails.priceAmountMicros.toDouble() / 1000000
            data["currency"] = oneTimePurchaseOfferDetails.priceCurrencyCode
            data["localizedPrice"] = oneTimePurchaseOfferDetails.formattedPrice
          }
          // Get subscription info
          else {
            // Get offer
            val subscriptionOfferDetails = productQuery.getSubscriptionOffer()
            // Use first pricing phase as default
            var phaseList = subscriptionOfferDetails?.pricingPhases?.pricingPhaseList
            if (phaseList != null) {
              val lastPricingPhase = phaseList.elementAtOrNull(phaseList.lastIndex)
              if (lastPricingPhase != null) {
                data["price"] = lastPricingPhase.priceAmountMicros.toDouble() / 1000000
                data["currency"] = lastPricingPhase.priceCurrencyCode
                data["localizedPrice"] = lastPricingPhase.formattedPrice
                data["subscriptionDuration"] = lastPricingPhase.billingPeriod
                data["subscriptionIntroPhases"] = listOf<Any>()
                // Add other phases
                if (phaseList.size > 1) {
                  var otherPhases = phaseList.subList(0, phaseList.lastIndex)
                  data["subscriptionIntroPhases"] = otherPhases.map { phase ->
                    return@map mapOf(
                      "type" to if (phase.priceAmountMicros == 0L) "trial" else "intro",
                      "price" to phase.priceAmountMicros.toDouble() / 1000000,
                      "currency" to phase.priceCurrencyCode,
                      "localizedPrice" to phase.formattedPrice,
                      "cycleDuration" to phase.billingPeriod,
                      "cycleCount" to phase.billingCycleCount,
                      // Only way to check payment type, checking getRecurrenceMode() doesn't work
                      "payment" to if (phase.billingCycleCount == 1) "upfront" else "as_you_go"
                    )
                  }
                }
              }
            }
            // Ignore product if there is no phase list
            else {
              return@map null
            }
          }
          // Return product details
          return@map ProductDetails(data)
        }
        catch (err: Exception) {
          IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.product_details_parsing_failed, "\n\n${err.stackTraceToString()}")
        }
        return@map null
      }
      completion(null, productsDetails.filterNotNull())
    }
  }

  /**
   * Get product details
   */
  override fun getProductDetails(sku: String, completion: (IaphubError?, ProductDetails?) -> Unit) {
    // Return mocked product when enabled
    if (this.sdk.testing.storeLibraryMock == true) {
      val product = this.sdk.testing.mockedProductDetails?.find { product -> product.sku == sku }

      if (product == null) {
        return completion(IaphubError(error=IaphubErrorCode.product_not_available, params=mapOf("sku" to sku)), null)
      }
      return completion(null, product)
    }
    // Get product
    this.getProductsDetails(listOf(sku)) { err, productsDetails ->
      // Check error
      if (productsDetails == null) {
        return@getProductsDetails completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Search for product
      val productDetails = productsDetails.find { product -> product.sku == sku }
      if (productDetails == null) {
        return@getProductsDetails completion(IaphubError(error=IaphubErrorCode.product_not_available, params=mapOf("sku" to sku)), null)
      }
      // Return product
      completion(null, productDetails)
    }
  }

  /**
   * Get product details
   */
  @Synchronized
  override fun notifyBillingReady(error: IaphubError?) {
    var billing = this.billing
    var err = error

    // Add flag that the user has a billing unavailable issue
    if (error?.code == "billing_unavailable") {
      this.hasBillingUnavailable = true
    }
    // Otherwise if the billing is now ready after a previous billing_unavailable error
    else if (error == null && this.hasBillingUnavailable) {
      this.hasBillingUnavailable = false
    }
    // Check the billing has been started
    if (billing == null) {
      err = IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing)
    }
    // Cancel timer
    this.onBillingReadyTimer?.cancel()
    // Execute completions
    this.onBillingReady.forEach { completion -> completion(err, billing) }
    this.onBillingReady = mutableListOf()
    // Unmark connection as starting
    this.isStartingConnection = false
  }

  /************************************ BillingClientStateListener **********************************/

  /**
   * Called to notify that setup is complete.
   */
  override fun onBillingSetupFinished(billingResult: BillingResult) {
    var err: IaphubError? = null

    // Ignore event if store ready marked as false for testing
    if (this.sdk.testing.storeReady == false) {
      return
    }
    // Check error
    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      err = this.getErrorFromBillingResult(billingResult, "startConnection")
    }
    // Notify billing ready
    this.notifyBillingReady(err)
  }

  /**
   * Called to notify that the connection to the billing service was lost.
   * This does not remove the billing service connection itself - this binding to the service will remain active, and will trigger onBillingSetupFinished when the billing service is next running
   */
  override fun onBillingServiceDisconnected() {
    // No need to do anything here
  }

  /************************************ PurchasesUpdatedListener ************************************/

  /**
   * Returns notifications about purchases updates.
   */
  override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
    // Handle error
    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      var err: IaphubError? = null
      // Handle when the user tries to buy a product that has a pending purchase
      if (this.buyRequest != null && billingResult.responseCode == BillingClient.BillingResponseCode.DEVELOPER_ERROR && billingResult.debugMessage.contains("pending purchase")) {
        err = IaphubError(IaphubErrorCode.deferred_payment)
      }
      // Otherwise get error from billing result
      else {
        err = this.getErrorFromBillingResult(billingResult, "onPurchasesUpdated")
      }
      this.processBuyRequest(null, err, null)
      return
    }
    // Handle deferred subscription replace
    if (purchases == null) {
      return
    }
    // Process purchases
    for (purchase in purchases) {
      this.purchaseQueue?.add(purchase)
    }
  }

  /************************************ Private ************************************/

  /**
   * Get active subscriptions
   */
  private fun getActiveSubscriptions(completion: (IaphubError?, List<Purchase>) -> Unit) {
    // Wait for billing to be ready
    this.whenBillingReady { err, billing ->
      // Return an error if the billing isn't ready
      if (err != null || billing == null) {
        return@whenBillingReady completion(err, listOf())
      }
      // Get subscriptions
      billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { billingResult, subscriptionPurchases ->
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
          return@queryPurchasesAsync completion(this.getErrorFromBillingResult(billingResult, "queryPurchasesAsync"), listOf())
        }
        completion(null, subscriptionPurchases)
      }
    }
  }

  /**
   * Build billing flow params
   */
  private fun buildBillingFlowParams(sku: String, oldPurchaseToken: String?, prorationMode: String?, completion: (IaphubError?, BillingFlowParams?) -> Unit) {
    // Get product query
    this.getProductQuery(sku) { err, productQuery ->
      // Check error
      if (productQuery == null) {
        return@getProductQuery completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Create billing flow params
      try {
        val billingFlowParams = BillingFlowParams.newBuilder()
        // Create product details prams
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
          .setProductDetails(productQuery.details)
        // We must set the offer token
        val subscriptionOfferDetails = productQuery.getSubscriptionOffer()
        if (subscriptionOfferDetails != null) {
          productDetailsParams.setOfferToken(subscriptionOfferDetails.offerToken)
        }
        billingFlowParams.setProductDetailsParamsList(listOf(productDetailsParams.build()))
        // Handle subscription replace options for subscriptions
        if (productQuery.details.productType == BillingClient.ProductType.SUBS && oldPurchaseToken != null) {
          // Add old purchase token
          val subscriptionUpdateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
          subscriptionUpdateParams.setOldPurchaseToken(oldPurchaseToken)
          // Add proration mode
          if (prorationMode == "immediate_with_time_proration") {
            subscriptionUpdateParams.setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION)
          } else if (prorationMode == "immediate_and_charge_full_price") {
            subscriptionUpdateParams.setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE)
          } else if (prorationMode == "immediate_and_charge_prorated_price") {
            subscriptionUpdateParams.setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE)
          } else if (prorationMode == "immediate_without_proration") {
            subscriptionUpdateParams.setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITHOUT_PRORATION)
          } else if (prorationMode == "deferred") {
            subscriptionUpdateParams.setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED)
          } else if (prorationMode != null) {
            return@getProductQuery completion(
              IaphubError(
                IaphubErrorCode.unexpected,
                IaphubUnexpectedErrorCode.proration_mode_invalid,
                "value: ${prorationMode}",
                mapOf("prorationMode" to prorationMode)
              ),
              null
            )
          }
          // Add subscription params to builder
          billingFlowParams.setSubscriptionUpdateParams(subscriptionUpdateParams.build())
        }
        // Return billing flow params
        completion(null, billingFlowParams.build())
      }
      catch (err: Exception) {
        completion(IaphubError(IaphubErrorCode.unexpected, null, err.message), null)
      }
    }
  }

  /**
   * Get subscription replacement token
   */
  private fun getSubscriptionReplacementToken(product: Product, completion: (IaphubError?, String?) -> Unit) {
    this.getActiveSubscriptions() { err, activeSubscriptions ->
      // Check error
      if (err != null) {
        return@getActiveSubscriptions completion(err, null)
      }
      // No need to do anything if we do not detect any active subscription
      if (activeSubscriptions.isEmpty()) {
        return@getActiveSubscriptions completion(null, null)
      }
      // Get the skus of the active subscriptions
      val activeSubscriptionsSkus = activeSubscriptions.mapNotNull { item -> item.products.firstOrNull() }
      // Get user
      val user = this.sdk.user
      if (user == null) {
        return@getActiveSubscriptions completion(IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Get products
      user.getProductsBySku(activeSubscriptionsSkus) { err, products ->
        // Check error
        if (err != null || products == null) {
          return@getProductsBySku completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
        }
        // Look for an active subscription of the same group
        val productToReplace = products.find { item -> item.group == product.group }
        val subscriptionToReplace = productToReplace?.let { activeSubscriptions.find { item -> item.products.contains(productToReplace.sku) } }
        if (subscriptionToReplace != null) {
          return@getProductsBySku completion(null, subscriptionToReplace.purchaseToken)
        }
        // Call completion if no result
        completion(null, null)
      }
    }
  }

  /**
   * Check subscription replacement
   */
  private fun checkSubscriptionReplacement(product: Product, completion: (IaphubError?, String?) -> Unit) {
    // Skipping unnecessary check for non-renewable subscriptions or products without a group
    if (product.type != "renewable_subscription" || product.group == null) {
      return completion(null, null)
    }
    this.getSubscriptionReplacementToken(product) { err, token ->
      // Check error
      if (err != null) {
        return@getSubscriptionReplacementToken completion(err, null)
      }
      // If no token is found
      if (token == null) {
        // Get user
        val user = this.sdk.user
        if (user == null) {
          return@getSubscriptionReplacementToken completion(IaphubError(IaphubErrorCode.unexpected), null)
        }
        // Verify that there are no active Android subscriptions for the user. This would indicate that the subscription is associated with a different Google account.
        val activeSubscription = user.activeProducts.find { item -> item.platform == "android" && item.type == "renewable_subscription" && item.group == product.group }
        if (activeSubscription != null) {
          return@getSubscriptionReplacementToken completion(IaphubError(IaphubErrorCode.cross_account_conflict), null)
        }
      }
      completion(null, token)
    }
  }

  /**
   * Check if the billing is ready
   */
  @Synchronized
  private fun isBillingReady(): Boolean {
    val testingValue = this.sdk.testing.storeReady

    if (testingValue != null) {
      return testingValue
    }
    return this.billing?.isReady == true
  }

  /**
   * Start billing connection
   */
  @Synchronized
  private fun whenBillingReady(completion: (IaphubError?, BillingClient?) -> Unit) {
    val billing = this.billing

    // Check the billing has been started
    if (billing == null) {
      return completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.start_missing), null)
    }
    // Call completion is the billing is ready
    if (this.isBillingReady()) {
      return completion(null, billing)
    }
    // Add callback
    this.onBillingReady.add(completion)
    // Stop here if we're already starting the connection
    if (this.isStartingConnection) {
      return
    }
    // Mark the connection as starting
    this.isStartingConnection = true
    // Trigger billing startConnection
    // Only if not already connecting, it could be if the timeout was previously triggered
    if (billing.connectionState != BillingClient.ConnectionState.CONNECTING) {
      billing.startConnection(this)
    }
    // Create timer to check if the billing isn't ready in the next 20 seconds
    val self = this
    val timeout = this.sdk.testing.storeReadyTimeout ?: 20000
    this.onBillingReadyTimer = Timer()
    this.onBillingReadyTimer?.schedule(timerTask {
      synchronized(this) {
        var err: IaphubError? = null
        // Clear timer
        self.onBillingReadyTimer = null
        // Create error if the billing is still not ready
        if (!self.isBillingReady()) {
          err = IaphubError(
            IaphubErrorCode.billing_unavailable,
            IaphubBillingUnavailableErrorCode.billing_ready_timeout,
            silentLog=self.hasBillingUnavailable
          )
        }
        // Notify billing ready
        self.notifyBillingReady(err)
      }
    }, timeout)
  }

  /**
   * Create fake purchase for testing
   */
  private fun createFakePurchase(sku: String): Purchase {
    var purchaseToken = UUID.randomUUID().toString()

    return Purchase(
      "{\"purchaseState\": 1, \"acknowledged\": false, \"purchaseToken\": \"${purchaseToken}\", \"productIds\": [\"${sku}\"]}",
      "fakesignature"
    )
  }

  /**
   * Process purchase
   */
  private fun processPurchase(purchase: Purchase, date: Date, completion: () -> Unit) {
    // Get purchase token and sku
    val purchaseToken = purchase.purchaseToken
    var productId = if (purchase.products.isNotEmpty()) purchase.products[0] else null

    if (productId == null) {
      return completion()
    }
    // Detect receipt context
    val buyRequest = this.buyRequest
    var context = "refresh"
    var prorationMode: String? = null

    if (buyRequest != null && ((buyRequest.productId == productId) || buyRequest.options["prorationMode"] == "deferred")) {
      context = "purchase"
      prorationMode = buyRequest.options["prorationMode"]
      // Use the actual product ID for a deferred purchase
      if (buyRequest.options["prorationMode"] == "deferred") {
        productId = buyRequest.productId
      }
    }
    else if (this.restoredPurchases?.contains(purchase) == true) {
      context = "restore"
    }
    // Create receipt
    val receipt = Receipt(token=purchaseToken, sku=productId, context=context, prorationMode=prorationMode)
    // Security to prevent the same token to be processed multiple times in a short interval
    if (
      context == "refresh" &&
      this.lastReceipt != null &&
      this.lastReceipt?.token == receipt.token &&
      this.lastReceipt?.processDate != null &&
      Date(this.lastReceipt?.processDate!!.time + 500) > date
    ) {
      // Call completion
      return completion()
    }
    // Update last receipt
    this.lastReceipt = receipt
    // Function to complete process
    fun completeProcess(err: IaphubError?, receiptTransaction: ReceiptTransaction?) {
      // Update receipt properties
      receipt.processDate = Date()
      // Process buy request
      this.processBuyRequest(productId, err, receiptTransaction)
      // Call completion
      completion()
    }
    // Call receipt listener
    this.onReceipt?.let {
      it(receipt) { err, shouldFinish, receiptTransaction ->
        // Finish transaction
        if (shouldFinish) {
          if (receiptTransaction == null) {
            IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.property_missing, "couldn't find transaction in receipt in order to determine product type", params=mapOf("context" to receipt.context, "sku" to receipt.sku, "orderId" to purchase.orderId))
          }
          // Remove from failed receipts if present
          this.failedReceipts = this.failedReceipts.filter { receipt -> receipt.token != purchaseToken }.toMutableList()
          // Finish purchase
          val isConsumable = listOf("consumable", "subscription").contains(receiptTransaction?.type)
          this.finishPurchase(purchase, isConsumable) { _ ->
            completeProcess(err, receiptTransaction)
          }
        }
        // Otherwise complete process directly
        else {
          // If we have an error and the receipt isn't finished, add it to the failed receipts list
          if (err != null) {
            var failedReceipt = this.failedReceipts.find { receipt -> receipt.token == purchaseToken }
            if (failedReceipt == null) {
              this.failedReceipts.add(ReceiptProcessing(purchase.purchaseToken))
            }
          }
          // Call completion
          completeProcess(err, receiptTransaction)
        }
      }
    }
  }

  /**
   * Process buy request
   */
  @Synchronized
  private fun processBuyRequest(productId: String?, err: IaphubError?, transaction: ReceiptTransaction?) {
    val buyRequest = this.buyRequest
    // If an sku if specified, process the buy request only if the sku match
    if (buyRequest != null && productId != null && buyRequest?.productId == productId) {
      val sku = buyRequest.sku
      this.buyRequest = null
      // Get product details
      this.getProductDetails(sku) { _, details ->
        if (transaction != null && details != null) {
          transaction.setDetails(details)
        }
        buyRequest.completion.invoke(err, transaction)
      }
    }
    // Otherwise process the buy request with no sku (it's an error)
    else if (buyRequest != null && productId == null) {
      this.buyRequest = null
      buyRequest.completion.invoke(err, transaction)
    }
  }

  /**
   * Launch billing flow
   */
  private fun launchBillingFlow(activity: Activity, params: BillingFlowParams, completion: (IaphubError?) -> Unit) {
    this.whenBillingReady { err, billing ->
      // Return an error if the billing isn't ready
      if (err != null || billing == null) {
        return@whenBillingReady completion(err)
      }
      // Launch billing flow (must be executed on the main thread according to the doc)
      Util.dispatchToMain {
        val billingResult = billing.launchBillingFlow(activity, params)
        // Check for errors
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
          completion(this.getErrorFromBillingResult(billingResult, "launchBillingFlow"))
        }
      }
    }
  }

  /**
   * Finish transaction
   */
  private fun finishPurchase(purchase: Purchase, isConsumable: Boolean, completion: (IaphubError?) -> Unit) {
    if (isConsumable) {
      this.consumePurchase(purchase, completion)
    }
    else {
      this.acknowledgePurchase(purchase, completion)
    }
  }

  /**
   * Consume purchase
   */
  private fun consumePurchase(purchase: Purchase, completion: (IaphubError?) -> Unit) {
    // Handle testing
    if (this.sdk.testing.storeLibraryMock == true) {
      return completion(null)
    }
    // Wait for billing to be ready
    this.whenBillingReady { err, billing ->
      // Return an error if the billing isn't ready
      if (err != null || billing == null) {
        return@whenBillingReady completion(err)
      }
      // Consume purchase
      val params: ConsumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
      billing.consumeAsync(params) { billingResult, _ ->
        // Check error
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
          return@consumeAsync completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.consume_failed, billingResult.debugMessage, mapOf("code" to billingResult.responseCode)))
        }
        // Otherwise it is a success
        completion(null)
      }
    }
  }

  /**
   * Acknowledge purchase
   */
  private fun acknowledgePurchase(purchase: Purchase, completion: (IaphubError?) -> Unit) {
    // Check if the purchase is already acknowledged
    if (purchase.isAcknowledged) {
      return completion(null)
    }
    // Check that it has a PURCHASED state
    if (purchase.purchaseState != 1) {
      return completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.acknowledge_failed, "purchase state invalid (value: ${purchase.purchaseState})", silent=true))
    }
    // Handle testing
    if (this.sdk.testing.storeLibraryMock == true) {
      return completion(null)
    }
    // Wait for billing to be ready
    this.whenBillingReady { err, billing ->
      // Return an error if the billing isn't ready
      if (err != null || billing == null) {
        return@whenBillingReady completion(err)
      }
      // Acknowledge purchase
      val params: AcknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
      billing.acknowledgePurchase(params) acknowledge@ { billingResult ->
        // Check error
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
          return@acknowledge completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.acknowledge_failed, billingResult.debugMessage, mapOf("code" to billingResult.responseCode)))
        }
        // Otherwise it is a success
        completion(null)
      }
    }
  }

  /**
   * Get purchases
   */
  private fun getPurchases(completion: (IaphubError?, List<Purchase>) -> Unit) {
    // Handle testing
    if (this.sdk.testing.storeLibraryMock == true) {
      val self = this
      Timer().schedule(timerTask {
        completion(null, listOf(self.createFakePurchase("test")))
      }, 200)
      return
    }
    // Wait for billing to be ready
    this.whenBillingReady { err, billing ->
      // Return an error if the billing isn't ready
      if (err != null || billing == null) {
        return@whenBillingReady completion(err, listOf())
      }
      // Get subscription purchases
      billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { _, subscriptionPurchases ->
        // Get product purchases
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { _, productPurchases ->
          // Return purchases
          completion(null, subscriptionPurchases + productPurchases)
        }
      }
    }
  }

  /**
   * Get error from billing result
   */
  private fun getErrorFromBillingResult(billingResult: BillingResult, method: String): IaphubError {
    val responseCode = billingResult.responseCode
    var errorType = IaphubErrorCode.unexpected
    var subErrorType: IaphubErrorProtocol? = null
    var message: String? = null
    var params = mapOf("method" to method, "responseCode" to responseCode, "message" to billingResult.debugMessage)
    var silentLog = false

    if (responseCode == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED) {
      errorType = IaphubErrorCode.billing_unavailable
      subErrorType = IaphubBillingUnavailableErrorCode.play_store_outdated
      message = "FEATURE_NOT_SUPPORTED error" + billingResult.debugMessage
      // Silence log if we already had a billing unavailable error
      if (this.hasBillingUnavailable) {
        silentLog = true
      }
    }
    else if (responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
      errorType = IaphubErrorCode.billing_unavailable
      message = billingResult.debugMessage
      // Detect if it is an issue with the Play Store being outdated
      if (message.contains("API version is less than")) {
        subErrorType = IaphubBillingUnavailableErrorCode.play_store_outdated
      }
      // Silence log if we already had a billing unavailable error
      if (this.hasBillingUnavailable) {
        silentLog = true
      }
    }
    else if (responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
      errorType = IaphubErrorCode.network_error
      subErrorType = IaphubNetworkErrorCode.billing_request_failed
    }
    else if (responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR) {
      errorType = IaphubErrorCode.network_error
      subErrorType = IaphubNetworkErrorCode.billing_request_failed
    }
    else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
      errorType = IaphubErrorCode.user_cancelled
    }
    else if (responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
      errorType = IaphubErrorCode.network_error
      subErrorType = IaphubNetworkErrorCode.billing_request_failed
    }
    else if (responseCode == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) {
      errorType = IaphubErrorCode.product_not_available
    }
    else if (responseCode == BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
      errorType = IaphubErrorCode.unexpected
      subErrorType = IaphubUnexpectedErrorCode.billing_developer_error
      message = billingResult.debugMessage
    }
    else if (responseCode == BillingClient.BillingResponseCode.ERROR) {
      errorType = IaphubErrorCode.unexpected
      subErrorType = IaphubUnexpectedErrorCode.billing_error
      message = "ERROR error, " + billingResult.debugMessage
    }
    else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
      errorType = IaphubErrorCode.product_already_owned
    }
    else if (responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
      errorType = IaphubErrorCode.unexpected
      subErrorType = IaphubUnexpectedErrorCode.billing_error
      message = "ITEM_NOT_OWNED error"
    }
    else {
      errorType = IaphubErrorCode.unexpected
      subErrorType = IaphubUnexpectedErrorCode.billing_error
      message = "unsupported error with responseCode ${responseCode}"
    }

    return IaphubError(error=errorType, suberror=subErrorType, message=message, params=params, fingerprint=method, silentLog=silentLog)
  }

  /**
   * Get list of product queries
   */
  private fun getProductQueries(skus: List<String>, completion: (IaphubError?, List<ProductQuery>?) -> Unit) {
    // Get subscriptions
    this.getProductQueries(skus, BillingClient.ProductType.SUBS) one@ { err, subs ->
      // Check error
      if (err != null) {
        return@one completion(err, null)
      }
      // Return if we have everything we need
      if (subs?.size == skus.size) {
        return@one completion(null, subs)
      }
      // Get in-app products
      this.getProductQueries(skus, BillingClient.ProductType.INAPP) two@ { err, products ->
        // Check error
        if (err != null) {
          return@two completion(err, null)
        }
        // Concatenate and return subs and products
        completion(null, (subs ?: listOf()) + (products ?: listOf()))
      }
    }
  }

  /**
   * Get list of sku details from cache
   */
  private fun getProductQueriesFromCache(skus: List<String>): List<ProductQuery> {
    return skus.map { sku -> this.cachedProductQueries.get(sku) }.filterNotNull()
  }

  /**
   * Get list of product queries
   */
  private fun getProductQueries(skus: List<String>, type: String, completion: (IaphubError?, List<ProductQuery>?) -> Unit) {
    // Return empty list if skus list empty
    if (skus.isEmpty()) {
      return completion(null, emptyList())
    }
    // Wait for billing to be ready
    this.whenBillingReady { err, billing ->
      // Return an error if the billing isn't ready
      if (err != null || billing == null) {
        return@whenBillingReady completion(err, getProductQueriesFromCache(skus))
      }
      // Build request
      val productsList = skus
        .map { sku -> sku.substringBefore(":") }
        .distinct()
        .map { sku ->
          QueryProductDetailsParams.Product.newBuilder()
            .setProductId(sku)
            .setProductType(type)
            .build()
        }
      val params = QueryProductDetailsParams.newBuilder().setProductList(productsList)
      billing.queryProductDetailsAsync(params.build()) { billingResult, productsDetailsList ->
        // Check response code
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
          // Otherwise return an error
          return@queryProductDetailsAsync completion(this.getErrorFromBillingResult(billingResult, "queryProductDetailsAsync"), getProductQueriesFromCache(skus))
        }
        // Create product queries list
        val productQueries = skus.map { sku ->
          val productDetails = productsDetailsList?.find { item -> item.productId == sku.substringBefore(":") }

          if (productDetails != null) {
            val productQuery = ProductQuery(sku, productDetails)

            this.cachedProductQueries.put(sku, productQuery)
            return@map productQuery
          }
          this.cachedProductQueries.remove(sku)
          return@map null
        }.filterNotNull()
        // Return list
        completion(null, productQueries)
      }
    }
  }

  /**
  * Get product query
  */
  private fun getProductQuery(sku: String, completion: (IaphubError?, ProductQuery?) -> Unit) {
    this.getProductQueries(listOf(sku)) { err, productQueries ->
      // Check error
      if (productQueries == null) {
        return@getProductQueries completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Search for product query
      val productQuery = productQueries.find { productQuery -> productQuery.sku == sku }
      if (productQuery == null) {
        return@getProductQueries completion(IaphubError(error=IaphubErrorCode.product_not_available, params=mapOf("sku" to sku)), null)
      }
      // Return product query
      completion(null, productQuery)
    }
  }

}