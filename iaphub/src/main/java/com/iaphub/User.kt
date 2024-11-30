package com.iaphub

import android.app.Activity
import java.lang.Exception
import java.util.*

internal class User {

  // User id
  internal var id: String
  // Iaphub user id
  internal var iaphubId: String? = null
  // Products for sale of the user
  internal var productsForSale: List<Product> = listOf()
  // Filtered products for sale of the user
  internal var filteredProductsForSale: List<Product> = listOf()
  // Active products of the user
  internal var activeProducts: List<ActiveProduct> = listOf()
  // Paywall id
  internal var paywallId: String? = null
  // Latest user fetch date
  internal var fetchDate: Date? = null


  // SDK
  internal val sdk: SDK
  // API
  internal val api: API
  // Event triggered when the user is updated
  internal val onUserUpdate: (() -> Unit)?
  // Event triggered on a deferred purchase
  internal val onDeferredPurchase: ((ReceiptTransaction) -> Unit)?
  // Restored deferred purchases (recorded during restore instead of calling onDeferredPurchase event)
  internal var restoredDeferredPurchases: MutableList<ReceiptTransaction> = mutableListOf()
  // Fetch requests
  internal var fetchRequests: MutableList<(IaphubError?, Boolean) -> Unit> = mutableListOf()
  // Indicates if the user is currently being fetched
  internal var isFetching: Boolean = false
  // Indicates the user is posting tags
  internal var isPostingTags: Boolean = false
  // Indicates the user is restoring purchases
  internal var isRestoring: Boolean = false
  // Indicates the user is initialized
  internal var isInitialized: Boolean = false
  // Indicates the login with the server is enabled
  internal var isServerLoginEnabled: Boolean = false
  // Indicates if the user needs to be fetched
  internal var needsFetch: Boolean = false
  // Latest receipt post date
  internal var receiptPostDate: Date? = null
  // Latest date an update has been made
  internal var updateDate: Date? = null
  // If the deferred purchase events should be consumed
  internal var enableDeferredPurchaseListener: Boolean = true
  // Last error returned when fetching the products details
  internal var productsDetailsError: IaphubError? = null
  // Purchase intent id
  internal var purchaseIntent: String? = null


  constructor(id: String?, sdk: SDK, enableDeferredPurchaseListener: Boolean = true, onUserUpdate: (() -> Unit)? = null, onDeferredPurchase: ((ReceiptTransaction) -> Unit)? = null) {
    this.sdk = sdk
    this.enableDeferredPurchaseListener = enableDeferredPurchaseListener
    this.onUserUpdate = onUserUpdate
    this.onDeferredPurchase = onDeferredPurchase
    // If id defined use it
    if (id != null) {
      this.id = id
    }
    // Otherwise generate anonymous id
    else {
      this.id = this.getAnonymousId()
    }
    this.api = API(user=this)
  }

  /**
  Enable server login
   */
  fun enableServerLogin() {
    this.isServerLoginEnabled = true
    this.saveCacheData()
  }

  /**
  Disable server login
   */
  fun disableServerLogin() {
    this.isServerLoginEnabled = false
    this.saveCacheData()
  }

  /*
   * Return if it is an anonymous user
   */
  fun isAnonymous(): Boolean {
    return this.id.startsWith(Config.anonymousUserPrefix)
  }

  /**
   * Buy product
   */
  fun buy(activity: Activity, sku: String, prorationMode: String? = null, crossPlatformConflict: Boolean = true, completion: (IaphubError?, ReceiptTransaction?) -> Unit) {
    val options = mutableMapOf("sku" to sku, "prorationMode" to prorationMode)

    // Create purchase intent
    this.createPurchaseIntent(sku) { err ->
      // Check if there is an error
      if (err != null) {
        return@createPurchaseIntent this.confirmPurchaseIntent(err, null, completion)
      }
      // Refresh user
      this.refresh() { err, _, _ ->
        // Check if there is an error
        if (err != null) {
          return@refresh this.confirmPurchaseIntent(err, null, completion)
        }
        // Get product details
        this.getProductBySku(sku) { product ->
          // If we have the product (we could not have the products if the user purchases a product that isn't in the products for sale)
          if (product != null) {
            // Check for cross platform conflicts if it is a subscription
            if (product.type.contains("subscription")) {
              val conflictedSubscription = this.activeProducts.find { item -> item.type.contains("subscription") && item.platform != "android" }
              if (crossPlatformConflict && conflictedSubscription != null) {
                return@getProductBySku this.confirmPurchaseIntent(IaphubError(IaphubErrorCode.cross_platform_conflict, null,"platform: ${conflictedSubscription.platform}"), null, completion)
              }
            }
            // Check for renewable subscription replace
            if (product.type == "renewable_subscription" && product.group != null) {
              val subscriptionToReplace = this.activeProducts.find { item -> item.type == "renewable_subscription" && item.group == product.group && item.androidToken != null }

              if (subscriptionToReplace != null) {
                options["oldPurchaseToken"] = subscriptionToReplace.androidToken
              }
              // Check if the product is already going to be replaced on next renewal date
              val subscriptionRenewalProduct = this.activeProducts.find { item -> item.subscriptionRenewalProductSku == sku && item.subscriptionState == "active" }
              if (subscriptionRenewalProduct != null) {
                return@getProductBySku this.confirmPurchaseIntent(IaphubError(IaphubErrorCode.product_change_next_renewal, params = mapOf("sku" to sku)), null, completion)
              }
            }
          }
          // Launch purchase
          this.sdk.store?.buy(activity, options) { err, transaction ->
            this.confirmPurchaseIntent(err, transaction, completion)
          }
        }
      }
    }
  }

  /**
   * Restore products
   */
  fun restore(completion: (IaphubError?, RestoreResponse?) -> Unit) {
    // Reinitialize restoredDeferredPurchases list
    this.restoredDeferredPurchases = mutableListOf()
    // Mark as restoring
    this.isRestoring = true
    // Save old active products
    val oldActiveProducts = this.activeProducts
    // Launch restore
    this.sdk.store?.restore() { err ->
      // Update updateDate
      this.updateDate = Date()
      // Refresh user
      this.refresh(interval = 0, force = true) { _, _, _ ->
        val newPurchases = this.restoredDeferredPurchases
        val transferredActiveProducts = this.activeProducts.filter { newActiveProduct ->
          val isInOldActiveProducts = (oldActiveProducts.find { oldActiveProduct -> oldActiveProduct.sku == newActiveProduct.sku }) != null
          val isInNewPurchases = (newPurchases.find { newPurchase -> newPurchase.sku == newActiveProduct.sku }) != null

          return@filter !isInOldActiveProducts && !isInNewPurchases
        }
        // Call completion
        if (err == null || (newPurchases.size > 0 || transferredActiveProducts.size > 0)) {
          completion(null, RestoreResponse(newPurchases, transferredActiveProducts))
        }
        else {
          completion(err, null)
        }
        // Mark restore as done
        this.isRestoring = false
      }
    }
  }

  /**
   * Show subscriptions manage
   */
  fun showManageSubscriptions(sku: String? = null, completion: (IaphubError?) -> Unit) {
    this.sdk.store?.showManageSubscriptions(sku, completion)
  }

  /*
   * Refresh user
   */
  fun refresh(interval: Long, force: Boolean = false, completion: ((IaphubError?, Boolean, Boolean) -> Unit)? = null) {
    var shouldFetch = false

    if (
      // Refresh forced
      force ||
      // User hasn't been fetched yet
      this.fetchDate == null ||
      // User marked as outdated
      this.needsFetch == true ||
      // User hasn't been refreshed since the interval
      Date(this.fetchDate!!.getTime() + interval).before(Date()) ||
      // Receit post date more recent than the user fetch date
      this.receiptPostDate?.after(this.fetchDate!!) == true
    ) {
      shouldFetch = true
    }
    // Update products details if we had an error or filtered products
    if (!shouldFetch && (this.productsDetailsError != null || this.filteredProductsForSale.isNotEmpty())) {
      this.updateFilteredProducts() { isUpdated ->
        completion?.let { it(null, false, isUpdated) }
      }
      return
    }
    // Call completion if fetch not requested
    if (!shouldFetch) {
      completion?.let { it(null, false, false) }
      return
    }
    // Otherwise fetch user
    this.fetch() {err, isUpdated ->
      // Check if there is an error
      if (err != null) {
        // Return an error if the user has never been fetched
        if (this.fetchDate == null) {
          completion?.let { it(err, false, false) }
        }
        // Otherwise check if there is an expired subscription in the active products
        else {
          val expiredSubscription = this.activeProducts.find { product -> product.expirationDate != null && product.expirationDate.before(Date()) }
          // If we have an expired subscription, return an error
          if (expiredSubscription != null) {
            completion?.let { it(err, false, false) }
          }
          // Otherwise return no error
          else {
            completion?.let { it(null, false, false) }
          }
        }
      }
      // Otherwise it's a success
      else {
        completion?.let { it(null, true, isUpdated) }
      }
    }
  }

  /*
   * Refresh user with a shorter interval if the user has an active subscription (otherwise every 24 hours by default)
   */
  fun refresh(completion: ((IaphubError?, Boolean, Boolean) -> Unit)? = null) {
    // Refresh user
    this.refresh(60 * 60 * 24) { err, isFetched, isUpdated ->
      // Check if there is an error
      if (err != null) {
        completion?.let { it(err, isFetched, isUpdated) }
        return@refresh
      }
      // If the user has not been fetched, look if there is active subscriptions
      if (isFetched == false) {
        val subscription = this.activeProducts.find { product -> arrayOf("renewable_subscription", "subscription").contains(product.type) }
        // If we have an active subscription refresh every minute
        if (subscription != null) {
          this.refresh(interval=60, completion=completion)
        }
        // Otherwise call the completion
        else {
          completion?.let { it(null, isFetched, isUpdated) }
        }
      }
      // Otherwise call the completion
      else {
        completion?.let { it(null, isFetched, isUpdated) }
      }
    }
  }

  /*
   * Get active products
   */
  fun getActiveProducts(includeSubscriptionStates: List<String> = listOf(), completion: (IaphubError?, List<ActiveProduct>?) -> Unit) {
    // Refresh user
    this.refresh() { err, _, _ ->
      // Check if there is an error
      if (err != null) {
        return@refresh completion(err, null)
      }
      // Otherwise return the products
      val subscriptionStates = arrayOf("active", "gracePeriod") + includeSubscriptionStates
      val activeProducts = this.activeProducts.filter { activeProduct ->
        // Return product if it has no subscription state
        if (activeProduct.subscriptionState == null) {
          return@filter true
        }
        // Otherwise return product only if the state is in the list
        return@filter subscriptionStates.contains(activeProduct.subscriptionState)
      }
      completion(null, activeProducts)
    }
  }

  /*
   * Get products for sale
   */
  fun getProductsForSale(completion: (IaphubError?, List<Product>?) -> Unit) {
    // Refresh user with an interval of 24 hours
    this.refresh(interval=60 * 60 * 24) { err, _, _ ->
      // Check if there is an error
      if (err != null) {
        return@refresh completion(err, null)
      }
      // Otherwise return the products
      completion(null, this.productsForSale)
    }
  }

  /*
   * Get billing status
   */
  fun getBillingStatus(): BillingStatus {
    val filteredProductIds = this.filteredProductsForSale.map { product -> product.sku }

    return BillingStatus(error=this.productsDetailsError, filteredProductIds=filteredProductIds)
  }

  /*
   * Get products (active and for sale)
   */
  fun getProducts(includeSubscriptionStates: List<String> = listOf(), completion: (IaphubError?, Products?) -> Unit) {
    this.getActiveProducts(includeSubscriptionStates) { err, activeProducts ->
      // Check if there is an error
      if (err != null || activeProducts == null) {
        return@getActiveProducts completion(err, null)
      }
      // Otherwise return the products
      completion(null, Products(activeProducts, this.productsForSale))
    }
  }

  /*
   * Get product by its sku
   */
  fun getProductBySku(sku: String, completion: (Product?) -> Unit) {
    // Search in products for sale
    var product = this.productsForSale.find { product -> product.sku == sku }
    // Search in active products
    if (product == null) {
      this.activeProducts.find { product -> product.sku == sku }
    }
    // Return product
    completion(product)
  }

  /*
   * Set tags
   */
  @Synchronized
  fun setTags(tags: Map<String, String>, completion: (IaphubError?) -> Unit) {
    // Check if currently posting tags
    if (this.isPostingTags) {
      return completion(IaphubError(IaphubErrorCode.user_tags_processing))
    }
    this.isPostingTags = true
    // Call API
    this.api.postTags(tags) { err ->
      this.isPostingTags = false
      // Check for error
      if (err != null) {
        return@postTags completion(err)
      }
      // Update updateDate
      this.updateDate = Date()
      // Reset cache
      this.resetCache()
      // Call completion
      completion(null)
    }
  }

  /*
   * Login
   */
  @Synchronized
  fun login(userId: String, completion: (IaphubError?) -> Unit) {
    // Check that id is valid
    if (!this.isValidId(userId)) {
      return completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.user_id_invalid, "login failed, id: ${userId}", mapOf("userId" to userId)))
    }
    // Check that the id isn't the same
    if (this.id == userId) {
      return completion(null)
    }
    // Detect if we should call the API to update the id
    val shouldCallApi = this.isAnonymous() && this.isServerLoginEnabled
    val currentUserId = this.id
    // Disable server login
    if (this.isServerLoginEnabled) {
      this.disableServerLogin()
    }
    // Update id
    this.id = userId
    // Reset user
    this.reset()
    // No need to call API if not necessary
    if (!shouldCallApi) return completion(null)
    // Call API
    this.api.login(currentUserId, userId) { _ ->
      // Ignore error and call completion (if the login couldn't be called any reason and the purchases were not transferred the user can still do a restore)
      completion(null)
    }
  }

  /*
   * Logout
   */
  @Synchronized
  fun logout() {
    // Cannot logout an anonymous user
    if (this.isAnonymous()) {
      return
    }
    // Update user id
    this.id = this.getAnonymousId()
    // Reset user
    this.reset()
  }

  /*
   * Load receipt pricings
   */
  fun loadReceiptPricings(receipt: Receipt, completion: () -> Unit) {
    // Get receipt sku + skus of same group
    val allProducts: List<Product> = productsForSale + activeProducts
    var skus = emptyList<String>()
    // Look if we can find product
    val receiptProduct = allProducts.find { product -> receipt.sku.substringBefore(":") == product.sku.substringBefore(":") }
    // If we can, also add the products from the same group
    if (receiptProduct != null) {
      val productsSameGroup = allProducts.filter { item -> item.group != null && item.group == receiptProduct.group && item.sku != receiptProduct.sku }.take(20)
      skus = listOf(receiptProduct.sku) + productsSameGroup.map { item -> item.sku }
    }
    // Otherwise only use the sku from the receipt
    else {
      skus = listOf(receipt.sku)
    }
    // Get products details
    this.sdk.store?.getProductsDetails(skus) { _, productsDetails ->
      // Add pricings of the skus on the receipt
      receipt.pricings = productsDetails?.mapNotNull { productDetails ->
        val price = productDetails.price
        val currency = productDetails.currency
        val product = allProducts.find { product -> product.sku == productDetails.sku }

        if (currency != null && price != null) {
          ProductPricing(
            id = product?.id,
            sku = productDetails.sku,
            price = price,
            currency = currency,
            introPrice = productDetails.subscriptionIntroPhases?.firstOrNull()?.price
          )
        } else {
          null
        }
      } ?: listOf()
      // Call completion
      completion()
    }
  }

  /*
   * Post receipt
   */
  fun postReceipt(receipt: Receipt, completion: (IaphubError?, ReceiptResponse?) -> Unit) {
    // Add purchase intent
    if (receipt.context == "purchase") {
      receipt.purchaseIntent = this.purchaseIntent
    }
    // Load receipt pricings
    this.loadReceiptPricings(receipt) { ->
      // Post receipt
      this.api.postReceipt(receipt.getData()) { err, data ->
        // Check for error
        if (err != null || data == null) {
          completion(err ?: IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.post_receipt_data_missing), null)
        }
        else {
          // Update updateDate
          this.updateDate = Date()
          // Update receipt post date
          this.receiptPostDate = Date()
          // Create receipt response
          val response = ReceiptResponse(data)
          // If it is an anonymous user, enable the server login if a new transaction is detected
          if (this.isAnonymous() && response.status == "success" && response.newTransactions?.isEmpty() == false) {
            this.enableServerLogin()
          }
          // Parse and return receipt response
          completion(null, response)
        }
      }
    }
  }

  /*
   * Reset cache
   */
  fun resetCache() {
    this.needsFetch = true
  }

  /*
   * Send log
   */
  fun sendLog(options: Map<String, Any?>) {
    // Build params
    val params = mutableMapOf(
      "data" to mutableMapOf(
        "body" to mapOf(
          "message" to mapOf("body" to options["message"])
        ),
        "level" to (options["level"] ?: "error"),
        "environment" to Iaphub.environment,
        "platform" to Config.sdk,
        "framework" to Iaphub.sdk,
        "code_version" to Config.sdkVersion,
        "person" to mapOf("id" to Iaphub.appId),
        "context" to "${Iaphub.appId}/${Iaphub.user?.id ?: ""}",
        "custom" to mapOf(
          "osVersion" to Iaphub.osVersion,
          "sdkVersion" to Iaphub.sdkVersion,
          "userIsInitialized" to this.isInitialized,
          "userHasProducts" to (this.productsForSale.isNotEmpty() || this.activeProducts.isNotEmpty())
        )
      )
    )
    // Add params
    val custom = options["params"] as? Map<String, Any?>
    val originalCustom = params["data"]?.get("custom") as? Map<String, Any?>
    if (custom != null && originalCustom != null) {
      params["data"]?.set("custom", originalCustom + custom);
    }
    // Add fingerprint
    val fingerprint = options["fingerprint"]
    if (fingerprint != null) {
      params["data"]?.set("fingerprint", fingerprint)
    }
    // Post log
    this.api.postLog(params) { _, _ ->
      // No need to do anything if there is an error
    }
  }

  /*
   * Fetch user
   */
  @Synchronized
  fun fetch(completion: (IaphubError?, Boolean) -> Unit) {
    // Check if the user id is valid
    if (!this.isAnonymous() && !this.isValidId(this.id)) {
      return completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.user_id_invalid, "fetch failed, id: ${this.id}", mapOf("userId" to this.id)), false)
    }
    // Add completion to the requests
    this.fetchRequests.add(completion)
    // Stop here if the user is currently being fetched
    if (this.isFetching) {
      return
    }
    this.isFetching = true
    // Method to complete fetch requests
    fun completeFetchRequests(err: IaphubError?, isUpdated: Boolean) {
      val fetchRequests = this.fetchRequests

      // Clean requests
      this.fetchRequests = mutableListOf()
      // Update properties
      this.isFetching = false
      // If there is no error
      if (err == null) {
        // Update fetch date
        this.fetchDate = Date()
        // Save data
        this.saveCacheData()
      }
      // If we have a fetchDate mark the user as initialized
      if (this.fetchDate != null && !this.isInitialized) {
        this.isInitialized = true
      }
      // Call requests with the error
      var requestIsUpdated = isUpdated
      fetchRequests.forEach { request ->
        request(err, requestIsUpdated)
        // Only mark as updated for the first request
        if (requestIsUpdated) {
          requestIsUpdated = false
          this.onUserUpdate?.let { it() }
        }
      }
    }
    // Get cache data (only if fetching for the first time)
    this.getCacheData {
      // Get data from API
      this.api.getUser { err, data ->
        // Update user using API data
        this.updateFromApiData(err, data) { updateErr, isUpdated ->
          completeFetchRequests(updateErr, isUpdated)
        }
      }
    }
  }

  /******************************** PRIVATE ********************************/

  /**
   * Create purchase intent
   */
  private fun createPurchaseIntent(sku: String, completion: (IaphubError?) -> Unit) {
    // Check if not already processing
    if (this.purchaseIntent != null) {
      return completion(IaphubError(IaphubErrorCode.buy_processing))
    }
    // Create purchase intent
    val params = mutableMapOf<String, Any>(
      "sku" to sku
    )
    this.paywallId?.let { params["paywallId"] = it }

    this.api.createPurchaseIntent(params) callback@{ err, result ->
      // Check if there is an error
      if (err != null) {
        return@callback completion(err)
      }
      // Update current purchase intent id
      this.purchaseIntent = result?.get("id") as? String
      // Call completion
      completion(null)
    }
  }

  /**
   * Confirm purchase intent
   */
  private fun confirmPurchaseIntent(err: IaphubError?, transaction: ReceiptTransaction?, completion: (IaphubError?, ReceiptTransaction?) -> Unit) {
    val params = mutableMapOf<String, Any>()

    if (err != null) {
      params["errorCode"] = err.code
      err.subcode?.let { params["errorSubCode"] = it }
    }
    // This error shouldn't happen
    else if (transaction == null) {
      params["errorCode"] = "transaction_missing"
    }

    val purchaseIntent = this.purchaseIntent
    if (purchaseIntent == null) {
      return completion(err, transaction)
    }
    this.purchaseIntent = null
    this.api.confirmPurchaseIntent(purchaseIntent, params) { _, _ ->
      completion(err, transaction)
    }
  }

  /*
   * Get data
   */
  private fun getData(productsOnly: Boolean = false): Map<String, Any> {
    var data: Map<String, Any> = mapOf(
      "productsForSale" to this.productsForSale.map { product -> product.getData() },
      "activeProducts" to this.activeProducts.map { product -> product.getData() }
    )

    if (productsOnly == false) {
      data = LinkedHashMap(data)
      data.putAll(mapOf(
        "id" to this.id as Any,
        "paywallId" to this.paywallId as? Any,
        "fetchDate" to Util.dateToIsoString(this.fetchDate),
        "isServerLoginEnabled" to this.isServerLoginEnabled,
        "cacheVersion" to Config.cacheVersion
      ))
    }
    return data
  }

  /*
   * Update user using API data
   */
  private fun updateFromApiData(err: IaphubError?, data: Map<String, Any>?, completion: (IaphubError?, Boolean) -> Unit) {
    var userData = data
    var isUpdated = false

    // Check error
    if (err != null) {
      // Clear products if the platform is disabled
      if (err.code == "server_error" && err.subcode == "platform_disabled") {
        userData = mapOf(
          "productsForSale" to emptyList<Any>(),
          "activeProducts" to emptyList<Any>()
        )
      }
      // Otherwise return an error
      else {
        return completion(err, isUpdated)
      }
    }
    // Check data
    if (userData == null) {
      return completion(IaphubError(IaphubErrorCode.unexpected), isUpdated)
    }
    // Save products dictionary
    val oldData = this.getData(productsOnly = true)
    // Update data
    this.update(userData) { updateErr ->
      // Check error
      if (updateErr != null) {
        return@update completion(updateErr, isUpdated)
      }
      // Check if the user has been updated
      val newData = this.getData(productsOnly = true)
      if (this.isInitialized && !this.sameProducts(newData, oldData)) {
        isUpdated = true
      }
      // Call completion
      completion(null, isUpdated)
    }
  }

  /*
   * Update user with data
   */
  private fun update(data: Map<String, Any>, completion: (IaphubError?) -> Unit) {
    val productsForSale = Util.parseItems<Product>(data["productsForSale"]) { err, item ->
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.update_item_parsing_failed,
        message="product basic details\n\n${err.stackTraceToString()}",
        params=mapOf("item" to item)
      )
    }
    val activeProducts = Util.parseItems<ActiveProduct>(data["activeProducts"]) { err, item ->
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.update_item_parsing_failed,
        message="active basic details\n\n${err.stackTraceToString()}",
        params=mapOf("item" to item)
      )
    }
    val events = Util.parseItems<Event>(data["events"], allowNull=true) { err, item ->
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.update_item_parsing_failed,
        message="event\n\n${err.stackTraceToString()}",
        params=mapOf("item" to item)
      )
    }
    val eventTransactions = events.map { event -> event.transaction }
    val products: List<Product> = productsForSale + activeProducts + eventTransactions
    // Get products details
    this.updateProductsDetails(products) {
      val oldFilteredProducts = this.filteredProductsForSale

      // Filter products for sale
      this.productsForSale = productsForSale.filter { product -> product.details != null }
      this.filteredProductsForSale = productsForSale.filter { product -> product.details == null }
      // Check filtered products
      this.filteredProductsForSale.forEach { product ->
        var oldFilteredProduct = oldFilteredProducts.find { it.sku == product.sku }
        // Trigger log only if it is a new filtered product
        if (oldFilteredProduct == null) {
          IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.product_missing_from_store, "sku: ${product.sku}", mapOf("sku" to product.sku))
        }
      }
      // No need to filter active products
      this.activeProducts = activeProducts
      // Update iaphub id
      this.iaphubId = data["id"] as? String
      // Update paywall id
      this.paywallId = data["paywallId"] as? String
      // Mark needsFetch as false
      this.needsFetch = false
      // Process events
      this.processEvents(events)
      // Call completion
      completion(null)
    }
  }

  /*
   * Update products details
   */
  private fun updateProductsDetails(products: List<Product>, completion: () -> Unit) {
    // Extract sku and filter empty sku (could happen with an active product from another platform)
    var productSkus = products.map { product -> product.sku }.filter { sku -> sku != "" }.distinct()

    // Get products details
    this.sdk.store?.getProductsDetails(productSkus) { err, productsDetails ->
      // Note: We're not calling with completion handler with the error of getProductsDetails
      // We need to complete the update even though an error such as 'billing_unavailable' is returned
      // When there is an error getProductsDetails can still return products details (they might be in cache)
      // So instead we're saving the error
      this.productsDetailsError = err
      // Assign details to the product
      productsDetails?.forEach { productDetail ->
        products
        .filter { product -> product.sku == productDetail.sku }
        .forEach { product ->
          product.setDetails(productDetail)
        }
      }
      // Call completion
      completion()
    }
  }

  /*
   * Update filtered products
   */
  private fun updateFilteredProducts(completion: (Boolean) -> Unit) {
    // Get products details
    this.updateProductsDetails(this.filteredProductsForSale) {
      // Detect recovered products
      val recoveredProducts = this.filteredProductsForSale.filter { product -> product.details != null }
      // Add to list of products for sale
      this.productsForSale = this.productsForSale + recoveredProducts
      // Update filtered products for sale
      this.filteredProductsForSale = this.filteredProductsForSale.filter { product -> product.details == null }
      // If we recovered products
      if (recoveredProducts.isNotEmpty()) {
        // Trigger onUserUpdate
        this.onUserUpdate?.let { it() }
        // Call completion
        completion(true)
      }
      // Otherwise just call the completion
      else {
        completion(false)
      }
    }
  }

  /*
   * Process events
   */
  private fun processEvents(events: List<Event>) {
    events.forEach { event ->
      if (event.type == "purchase" && event.tags.contains("deferred")) {
        if (this.isRestoring) {
          this.restoredDeferredPurchases.add(event.transaction)
        }
        else {
          this.onDeferredPurchase?.let { it(event.transaction) }
        }
      }
    }
  }

  /*
   * Reset user
   */
  private fun reset() {
    this.productsForSale = listOf()
    this.activeProducts = listOf()
    this.fetchDate = null
    this.receiptPostDate = null
    this.updateDate = null
    this.needsFetch = false
    this.isInitialized = false
    this.isServerLoginEnabled = false
  }

  /*
   * Return if the user id is valid
   */
  private fun isValidId(userId: String): Boolean {
    // Check id length
    if (userId.length == 0 || userId.length > 100) {
      return false
    }
    // Check if id has valid format
    val regex = """^[a-zA-Z0-9-_]*$""".toRegex()
    if (regex.matches(userId) == false) {
      return false
    }
    // Check for forbidden user ids
    val forbiddenIds = arrayOf("null", "none", "nil", "(null)")
    if (forbiddenIds.contains(userId)) {
      return false
    }

    return true
  }

  /*
   * Get anonymous id
   */
  private fun getAnonymousId(): String {
    val key = "anonymous_user_id"
    val cacheId = Util.getFromCache(context=this.sdk.context, key=key)

    // Return id from cache if found
    if (cacheId != null) {
      return cacheId
    }
    // Generate new id
    val newId = Config.anonymousUserPrefix + UUID.randomUUID().toString()
    // Save new id to cache
    Util.setToCache(context=this.sdk.context, key=key, value=newId)
    // Check the id has been saved correctly
    val id = Util.getFromCache(context=this.sdk.context, key=key)
    if (id != newId) {
      IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.save_cache_anonymous_id_failed)
    }

    return newId
  }

  /*
   * Get cache data
   */
  private fun getCacheData(completion: () -> Unit) {
    // Fetch only on initialization
    if (this.fetchDate != null) {
      return completion()
    }
    // Dispatch on a thread to prevent work on the main thread
    Util.dispatch {
      val prefix = if (this.isAnonymous()) "iaphub_user_a" else "iaphub_user"
      val jsonString = Util.getFromCache(context = this.sdk.context, key = "${prefix}_${this.sdk.appId}")
      var jsonMap: Map<String, Any>? = null

      // Parse JSON string
      if (jsonString != null) {
        jsonMap = Util.jsonStringToMap(jsonString)
        if (jsonMap == null) {
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_json_parsing_failed,
            params=mapOf("jsonString" to jsonString)
          )
        }
      }
      // Use cache data if it comes from the same user id
      if (jsonMap != null && (jsonMap["id"] as? String == this.id) && (jsonMap["cacheVersion"] == Config.cacheVersion)) {
        this.fetchDate = Util.dateFromIsoString(jsonMap["fetchDate"]) { err ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on fetch date, $err",
            params=mapOf("fetchDate" to jsonMap["fetchDate"])
          )
        }
        this.productsForSale = Util.parseItems<Product>(jsonMap["productsForSale"]) { err, item ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on product for sale\n\n${err.stackTraceToString()}",
            params=mapOf("item" to item)
          )
        }
        this.activeProducts = Util.parseItems<ActiveProduct>(jsonMap["activeProducts"]) { err, item ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on active product\n\n${err.stackTraceToString()}",
            params=mapOf("item" to item)
          )
        }
        this.isServerLoginEnabled = jsonMap["isServerLoginEnabled"] as? Boolean ?: false
        this.paywallId = jsonMap["paywallId"] as? String
      }
      completion()
    }
  }

  /*
   * Get cache data
   */
  private fun saveCacheData() {
    val jsonData = this.getData()
    val jsonString = Util.mapToJsonString(jsonData)

    if (jsonString == null) {
      IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.save_cache_data_json_invalid)
    }
    else {
      val prefix = if (this.isAnonymous()) "iaphub_user_a" else "iaphub_user"
      Util.setToCache(
        context = this.sdk.context,
        key = "${prefix}_${this.sdk.appId}",
        value = jsonString
      )
    }
  }

  /*
   * Compare products
   */
  private fun sameProducts(data1: Map<String, Any>, data2: Map<String, Any>): Boolean {
    var isEqual = true

    try {
      var productsForSale1 = (data1["productsForSale"] as List<Map<String, Any?>>).toTypedArray()
      var productsForSale2 = (data2["productsForSale"] as List<Map<String, Any?>>).toTypedArray()
      var activeProducts1 = (data1["activeProducts"] as List<Map<String, Any?>>).toTypedArray()
      var activeProducts2 = (data2["activeProducts"] as List<Map<String, Any?>>).toTypedArray()

      if (!productsForSale1.contentDeepEquals(productsForSale2)) {
        isEqual = false
      }
      else if (!activeProducts1.contentDeepEquals(activeProducts2)) {
        isEqual = false
      }
    }
    catch(err: Exception) {
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.compare_products_failed,
        message="$err",
        params=mapOf("data1" to data1, "data2" to data2)
      )
    }

    return isEqual
  }

}