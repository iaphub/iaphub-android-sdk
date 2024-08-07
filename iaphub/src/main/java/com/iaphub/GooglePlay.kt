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
  private var onDeferredSubscriptionReplace: ((purchaseToken: String, newSku: String, (IaphubError?, ReceiptTransaction?) -> Unit) -> Unit)? = null
  private var billing: BillingClient? = null
  private var buyRequest: BuyRequest? = null
  private var restoredPurchases: List<Purchase>? = null
  private var purchaseQueue: Queue<Purchase>? = null
  private var lastReceipt: Receipt? = null
  private var cachedSkusDetails: MutableMap<String, com.android.billingclient.api.ProductDetails> = mutableMapOf()
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
    onReceipt: (Receipt, (IaphubError?, Boolean, ReceiptTransaction?) -> Unit) -> Unit,
    onDeferredSubscriptionReplace: (purchaseToken: String, newSku: String, (IaphubError?, ReceiptTransaction?) -> Unit) -> Unit
  ) {
    // Check it isn't already started
    if (this.billing != null) {
      return
    }
    // Save callbacks
    this.onReceipt = onReceipt
    this.onDeferredSubscriptionReplace = onDeferredSubscriptionReplace
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
  override fun buy(activity: Activity, options: Map<String, String?>, completion: (IaphubError?, ReceiptTransaction?) -> Unit) {
    // Return an error if a buy request is currently processing
    if (this.buyRequest != null) {
      return completion(IaphubError(IaphubErrorCode.buy_processing), null)
    }
    // Return an error if a restore is currently processing
    if (this.isRestoring) {
      return completion(IaphubError(IaphubErrorCode.restore_processing), null)
    }
    // Get and check sku
    val sku = options["sku"]
    if (sku == null) {
      return completion(IaphubError(IaphubErrorCode.unexpected, null, "product sku not specified"), null)
    }
    // Save buy request
    this.buyRequest = BuyRequest(sku, options, completion)
    // Handle mock
    if (this.sdk.testing.storeLibraryMock == true) {
      val self = this
      Timer().schedule(timerTask {
        self.purchaseQueue?.add(self.createFakePurchase(sku))
      }, 200)
      return
    }
    // Get product from sku
    this.getSkuDetails(sku) { err, skuDetails ->
      // Check error
      if (skuDetails == null) {
        this.buyRequest = null
        return@getSkuDetails completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Create billing flow params
      val billingFlowParams = BillingFlowParams.newBuilder()
      // Create product details prams
      val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(skuDetails)
      // We must set the offer token
      val subscriptionOfferDetails = this.getSubscriptionOffer(skuDetails)
      if (subscriptionOfferDetails != null) {
        productDetailsParams.setOfferToken(subscriptionOfferDetails.offerToken)
      }
      billingFlowParams.setProductDetailsParamsList(listOf(productDetailsParams.build()))
      // Handle subscription replace options for subscriptions
      if (skuDetails.productType == BillingClient.ProductType.SUBS && options["oldPurchaseToken"] != null) {
        val subscriptionUpdateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
        // Look for purchaseToken option
        val oldPurchaseToken = options["oldPurchaseToken"]
        if (oldPurchaseToken != null) {
          subscriptionUpdateParams.setOldPurchaseToken(oldPurchaseToken)
        }
        // Look for prorationMode option
        val prorationMode = options["prorationMode"]
        if (prorationMode == "immediate_with_time_proration") {
          subscriptionUpdateParams.setReplaceProrationMode(BillingFlowParams.ProrationMode.IMMEDIATE_WITH_TIME_PRORATION)
        } else if (prorationMode == "immediate_and_charge_prorated_price") {
          subscriptionUpdateParams.setReplaceProrationMode(BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE)
        } else if (prorationMode == "immediate_without_proration") {
          subscriptionUpdateParams.setReplaceProrationMode(BillingFlowParams.ProrationMode.IMMEDIATE_WITHOUT_PRORATION)
        } else if (prorationMode == "deferred") {
          subscriptionUpdateParams.setReplaceProrationMode(BillingFlowParams.ProrationMode.DEFERRED)
        } else if (prorationMode != null) {
          this.buyRequest = null
          return@getSkuDetails completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.proration_mode_invalid, "value: ${prorationMode}", mapOf("prorationMode" to prorationMode)), null)
        }
        // Add subscription params to builder
        billingFlowParams.setSubscriptionUpdateParams(subscriptionUpdateParams.build())
      }
      // Launch billing flow (must be executed on the main thread according to the doc)
      val builtBillingFlowParams = billingFlowParams.build()
      this.launchBillingFlow(activity, builtBillingFlowParams) { err ->
        if (err != null) {
          this.buyRequest = null
          completion(err, null)
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
        val failedReceiptRetryDuration = 60 * 60 * 24 * 3

        // Process purchase if not processed before or processed with an error less than 72 hours ago
        if (refreshedReceipt == null || (failedReceipt != null && Date(failedReceipt.date.getTime() + failedReceiptRetryDuration).after(Date()))) {
          this.purchaseQueue?.add(purchase)
        }
      }
      // Update refreshed order ids
      this.refreshedReceipts = mutableListOf()
      purchases.map { purchase -> ReceiptProcessing(purchase.purchaseToken) }
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
    this.getSkusDetails(skus) { err, skusDetails ->
      // Check error
      if (err != null || skusDetails == null) {
        return@getSkusDetails completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Convert list to product details
      val productsDetails: List<ProductDetails?> = skusDetails.map { details ->
        try {
          var data: MutableMap<String, Any?> = mutableMapOf()

          // Get general info
          data["sku"] = details.productId
          data["localizedTitle"] = details.title
          data["localizedDescription"] = details.description
          // Get consumable info
          val oneTimePurchaseOfferDetails = details.oneTimePurchaseOfferDetails
          if (oneTimePurchaseOfferDetails != null) {
            data["price"] = oneTimePurchaseOfferDetails.priceAmountMicros.toDouble() / 1000000
            data["currency"] = oneTimePurchaseOfferDetails.priceCurrencyCode
            data["localizedPrice"] = oneTimePurchaseOfferDetails.formattedPrice
          }
          // Get subscription info
          else {
            // Get offer
            val subscriptionOfferDetails = this.getSubscriptionOffer(details)
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
      this.processDeferredSubscriptionReplace()
      return
    }
    // Process purchases
    for (purchase in purchases) {
      this.purchaseQueue?.add(purchase)
    }
  }

  /************************************ Private ************************************/

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
    val sku = if (purchase.skus.isNotEmpty()) purchase.skus[0] else null
    if (sku == null) {
      return completion()
    }
    // Detect receipt context
    var context = "refresh"
    if (this.buyRequest?.sku == sku) {
      context = "purchase"
    }
    else if (this.restoredPurchases?.contains(purchase) == true) {
      context = "restore"
    }
    // Create receipt
    val receipt = Receipt(token=purchaseToken, sku=sku, context=context)
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
      this.processBuyRequest(sku, err, receiptTransaction)
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
   * Process deferred subscription replace
   */
  @Synchronized
  private fun processDeferredSubscriptionReplace() {
    val buyRequest = this.buyRequest
    var oldPurchaseToken = buyRequest?.options?.get("oldPurchaseToken")

    if (buyRequest != null && buyRequest.options["prorationMode"] == "deferred" && oldPurchaseToken != null) {
      this.onDeferredSubscriptionReplace?.let { it(oldPurchaseToken, buyRequest.sku) { err, transaction ->
        this.processBuyRequest(buyRequest.sku, err, transaction)
      }}
    }
    else {
      this.processBuyRequest(null, IaphubError(IaphubErrorCode.unexpected), null)
    }
  }

  /**
   * Process buy request
   */
  @Synchronized
  private fun processBuyRequest(sku: String?, err: IaphubError?, transaction: ReceiptTransaction?) {
    val buyRequest = this.buyRequest
    // If an sku if specified, process the buy request only if the sku match
    if (buyRequest != null && sku != null && buyRequest?.sku == sku) {
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
    else if (buyRequest != null && sku == null) {
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
   * Get list of sku details
   */
  private fun getSkusDetails(skus: List<String>, completion: (IaphubError?, List<com.android.billingclient.api.ProductDetails>?) -> Unit) {
    // Get subscriptions
    this.getSkusDetails(skus, BillingClient.ProductType.SUBS) one@ { err, subs ->
      // Check error
      if (err != null) {
        return@one completion(err, null)
      }
      // Return if we have everything we need
      if (subs?.size == skus.size) {
        return@one completion(null, subs)
      }
      // Get in-app products
      this.getSkusDetails(skus, BillingClient.ProductType.INAPP) two@ { err, products ->
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
  private fun getSkusDetailsFromCache(skus: List<String>): List<com.android.billingclient.api.ProductDetails> {
    return skus.map { sku -> this.cachedSkusDetails.get(sku) }.filterNotNull()
  }

  /**
   * Get list of sku details
   */
  private fun getSkusDetails(skus: List<String>, type: String, completion: (IaphubError?, List<com.android.billingclient.api.ProductDetails>?) -> Unit) {
    // Return empty list if skus list empty
    if (skus.isEmpty()) {
      return completion(null, emptyList())
    }
    // Wait for billing to be ready
    this.whenBillingReady { err, billing ->
      // Return an error if the billing isn't ready
      if (err != null || billing == null) {
        return@whenBillingReady completion(err, getSkusDetailsFromCache(skus))
      }
      // Build request
      val productsList = skus.map { sku ->
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
          return@queryProductDetailsAsync completion(this.getErrorFromBillingResult(billingResult, "queryProductDetailsAsync"), getSkusDetailsFromCache(skus))
        }
        // Cache skus details
        skus.forEach { sku ->
          val productDetails = productsDetailsList?.find { item -> item.productId == sku }

          if (productDetails != null) {
            this.cachedSkusDetails.put(sku, productDetails)
          } else {
            this.cachedSkusDetails.remove(sku)
          }
        }
        // Return list
        completion(null, productsDetailsList)
      }
    }
  }

  /**
   * Get sku details
   */
  private fun getSkuDetails(sku: String, completion: (IaphubError?, com.android.billingclient.api.ProductDetails?) -> Unit) {
    this.getSkusDetails(listOf(sku)) { err, skusDetails ->
      // Check error
      if (skusDetails == null) {
        return@getSkusDetails completion(err ?: IaphubError(IaphubErrorCode.unexpected), null)
      }
      // Search for product
      val skuDetails = skusDetails.find { product -> product.productId == sku }
      if (skuDetails == null) {
        return@getSkusDetails completion(IaphubError(error=IaphubErrorCode.product_not_available, params=mapOf("sku" to sku)), null)
      }
      // Return product
      completion(null, skuDetails)
    }
  }

  /**
   * Get the subscription offer for the user
   */
  private fun getSubscriptionOffer(details: com.android.billingclient.api.ProductDetails):  com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails? {
    // The first index (0) should contain the latest offer, then the previous offers and the last one is a base offer with no phases
    // When an offer has been used, all the other offers should disappear, leaving the base offer only
    val lastIndex = details.subscriptionOfferDetails?.lastIndex
    val index = if (lastIndex != null && lastIndex > 0) lastIndex - 1 else 0

    return details.subscriptionOfferDetails?.elementAtOrNull(index)
  }

}