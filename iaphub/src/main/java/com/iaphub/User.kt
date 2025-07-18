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
  // ETag for HTTP caching
  internal var etag: String? = null
  // Foreground refresh interval
  internal var foregroundRefreshInterval: Long? = null


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
  // Indicates if the user data has been fetched from the server
  internal var isServerDataFetched: Boolean = false
  // Indicates if the filtered products are currently being updated
  internal var isUpdatingFilteredProducts: Boolean = false
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
    // Create purchase intent
    this.createPurchaseIntent(sku) { err ->
      // Check if there is an error
      if (err != null) {
        return@createPurchaseIntent this.confirmPurchaseIntent(err, null, completion)
      }
      // Refresh user
      this.refresh(UserFetchContext(source = UserFetchContextSource.BUY)) { err, _, _ ->
        // Check if there is an error
        if (err != null) {
          return@refresh this.confirmPurchaseIntent(err, null, completion)
        }
        // Get product details
        this.getProductBySku(sku) { err, product ->
          // We must have the product
          if (product == null) {
            return@getProductBySku this.confirmPurchaseIntent(err ?: IaphubError(IaphubErrorCode.unexpected), null, completion)
          }
          // Check subscription safeties
          this.checkSubscriptionSafeties(product, crossPlatformConflict) { err ->
            // Check if there is an error
            if (err != null) {
              return@checkSubscriptionSafeties this.confirmPurchaseIntent(err, null, completion)
            }
            // Launch purchase
            this.sdk.store?.buy(activity, product, mapOf("prorationMode" to prorationMode)) { err, transaction ->
              this.confirmPurchaseIntent(err, transaction, completion)
            }
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
      this.refresh(UserFetchContext(source = UserFetchContextSource.RESTORE), interval = 0, force = true) { _, _, _ ->
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
  fun refresh(context: UserFetchContext, interval: Long, force: Boolean = false, completion: ((IaphubError?, Boolean, Boolean) -> Unit)? = null) {
    var shouldFetch = false

    if (
      // Refresh forced
      force ||
      // User hasn't been fetched yet
      this.fetchDate == null ||
      // User marked as outdated
      this.needsFetch == true ||
      // User hasn't been refreshed since the interval
      Date(this.fetchDate!!.getTime() + (interval * 1000)).before(Date()) ||
      // Receit post date more recent than the user fetch date
      this.receiptPostDate?.after(this.fetchDate!!) == true
    ) {
      shouldFetch = true
    }
    // Update products details if we had an error or filtered products
    if (!shouldFetch && this.filteredProductsForSale.isNotEmpty()) {
      this.updateFilteredProducts() { isUpdated ->
        // Trigger onUserUpdate on update
        if (isUpdated) {
          this.onUserUpdate?.let { it() }
        }
        // Call completion
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
    this.fetch(context.withRefreshInterval(interval)) {err, isUpdated ->
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
   * Refresh user with a dynamic interval
   */
  fun refresh(context: UserFetchContext, completion: ((IaphubError?, Boolean, Boolean) -> Unit)? = null) {
    var interval: Long = 86400

    // If this is an on foreground refresh, use the foreground refresh interval if defined
    if (context.properties.contains(UserFetchContextProperty.ON_FOREGROUND)) {
      foregroundRefreshInterval?.let { interval = it }
    }
    // Refresh user
    this.refresh(context = context, interval) { err, isFetched, isUpdated ->
      // Check if there is an error
      if (err != null) {
        completion?.let { it(err, isFetched, isUpdated) }
        return@refresh
      }
      // If the user has not been fetched and the interval is over 60s, check for active subscriptions
      if (isFetched == false && interval > 60) {
        val subscription = this.activeProducts.find { product -> arrayOf("renewable_subscription", "subscription").contains(product.type) }
        // If there are active subscriptions, refresh every minute
        if (subscription != null) {
          this.refresh(context = context, interval = 60, completion = completion)
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
    this.refresh(UserFetchContext(source = UserFetchContextSource.PRODUCTS)) { err, _, _ ->
      // Check if there is an error
      if (err != null) {
        return@refresh completion(err, null)
      }
      // Otherwise return the products
      val subscriptionStates = arrayOf("active", "grace_period") + includeSubscriptionStates
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
    this.refresh(UserFetchContext(source = UserFetchContextSource.PRODUCTS), interval = 60 * 60 * 24) { err, _, _ ->
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
      this.api.postReceipt(receipt.getData()) { err, response ->
        val data = response?.data
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
          val receiptResponse = ReceiptResponse(data)
          // If it is an anonymous user, enable the server login if a new transaction is detected
          if (this.isAnonymous() && receiptResponse.status == "success" && receiptResponse.newTransactions?.isEmpty() == false) {
            this.enableServerLogin()
          }
          // Parse and return receipt response
          completion(null, receiptResponse)
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
        "body" to mapOf("message" to mapOf("body" to options["message"])),
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
    this.api.postLog(params) { _, ->
      // No need to do anything if there is an error
    }
  }

  /*
   * Fetch user
   */
  @Synchronized
  fun fetch(context: UserFetchContext, completion: (IaphubError?, Boolean) -> Unit) {
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
      val isInitialized = this.isInitialized
      if (this.fetchDate != null && !isInitialized) {
        this.isInitialized = true
      }
      // Call requests with the error
      var requestIsUpdated = isUpdated
      fetchRequests.forEach { request ->
        request(err, requestIsUpdated)
        // Only mark as updated for the first request
        if (requestIsUpdated && isInitialized) {
          requestIsUpdated = false
          this.onUserUpdate?.let { it() }
        }
      }
    }
    // If fetching for the first time, try getting data from cache first
    if (this.fetchDate == null) {
      this.getCacheData {
        this.fetchAPI(context, ::completeFetchRequests)
      }
    } else {
      this.fetchAPI(context, ::completeFetchRequests)
    }
  }

  /******************************** PRIVATE ********************************/

  /**
   * Check subscription safeties
   */
  private fun checkSubscriptionSafeties(product: Product, crossPlatformConflict: Boolean, completion: (IaphubError?) -> Unit) {
    // No need to check if the product is not a subscription
    if (!product.type.contains("subscription")) {
      return completion(null)
    }
    // Check if the subscription is already active
    val activeSubscription = this.activeProducts.find { item -> item.sku == product.sku }
    if (activeSubscription != null) {
      return completion(IaphubError(IaphubErrorCode.product_already_purchased, params = mapOf("sku" to product.sku)))
    }
    // Check for cross platform conflicts
    val conflictedSubscription = this.activeProducts.find { item -> item.type.contains("subscription") && item.platform != "android" }
    if (crossPlatformConflict && conflictedSubscription != null) {
      return completion(IaphubError(IaphubErrorCode.cross_platform_conflict, null,"platform: ${conflictedSubscription.platform}"))
    }
    // Check if the product is already going to be replaced on next renewal date
    val replacedProduct = this.activeProducts.find { item -> item.subscriptionRenewalProductSku == product.sku && item.subscriptionState == "active" }
    if (replacedProduct != null) {
      return completion(IaphubError(IaphubErrorCode.product_change_next_renewal, params = mapOf("sku" to product.sku)))
    }

    return completion(null)
  }

  /*
   * Get product by sku
   */
  private fun getProductBySku(sku: String, completion: (IaphubError?, Product?) -> Unit) {
    val product = this.productsForSale.find { item -> item.sku == sku } ?:
                  this.filteredProductsForSale.find { item -> item.sku == sku } ?:
                  this.activeProducts.find { item -> item.sku == sku }
  
    if (product != null) {
      return completion(null, product)
    }
    this.api.getProduct(sku) { err, response ->
      if (err != null) {
        return@getProduct completion(err, null)
      }
      val parsedProduct = response?.data?.let { 
        try {
          Product(it)
        }
        catch (e: Exception) {
          return@getProduct completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.product_parsing_failed, e.message), null)
        }
      }
      completion(null, parsedProduct)
    }
  }

  /*
   * Get products by sku
   */
  internal fun getProductsBySku(skus: List<String>, completion: (IaphubError?, List<Product>?) -> Unit) {
    Util.eachSeriesWithResult(
      skus,
      { sku: String, callback: (IaphubError?, Product?) -> Unit ->
        this.getProductBySku(sku) { err, product ->
          callback(err, product)
        }
      },
      { err: IaphubError?, products: List<Product>? ->
        if (err != null) {
          completion(err, null)
        } else {
          completion(null, products)
        }
      }
    )
  }

  /**
   * Fetch user from API
   */
  private fun fetchAPI(context: UserFetchContext, completion: (IaphubError?, Boolean) -> Unit) {
    // Add last fetch context
    this.fetchDate?.let { fetchDate ->
      val timeSinceLastFetch = (Date().time - fetchDate.time) / 1000

      if (timeSinceLastFetch < 10) {
        context.properties.add(UserFetchContextProperty.LAST_FETCH_UNDER_TEN_SECONDS)
      }
      else if (timeSinceLastFetch < 60) {
        context.properties.add(UserFetchContextProperty.LAST_FETCH_UNDER_ONE_MINUTE)
      }
      else if (timeSinceLastFetch < 600) {
        context.properties.add(UserFetchContextProperty.LAST_FETCH_UNDER_TEN_MINUTES)
      }
      else if (timeSinceLastFetch < 3600) {
        context.properties.add(UserFetchContextProperty.LAST_FETCH_UNDER_ONE_HOUR)
      }
      else if (timeSinceLastFetch < 86400) {
        context.properties.add(UserFetchContextProperty.LAST_FETCH_UNDER_ONE_DAY)
      }
      else {}
    }
    // Add property to context if initialization detected
    if (!this.isServerDataFetched) {
      context.properties.add(UserFetchContextProperty.INITIALIZATION)
    }
    // Add property to context if active product detected
    if (this.activeProducts.isNotEmpty()) {
      // Check for active and expired subscriptions
      val subscriptions = this.activeProducts.filter { it.type.contains("subscription") && it.expirationDate != null }
      // Add active subscription property if any subscription is active
      if (subscriptions.any { it.expirationDate!! > Date() }) {
        context.properties.add(UserFetchContextProperty.WITH_ACTIVE_SUBSCRIPTION)
      }
      // Add expired subscription property if any subscription is expired
      if (subscriptions.any { it.expirationDate!! <= Date() }) {
        context.properties.add(UserFetchContextProperty.WITH_EXPIRED_SUBSCRIPTION)
      }
      // Add active non consumable property if any non consumable is active
      if (this.activeProducts.any { it.type == "non_consumable" }) {
        context.properties.add(UserFetchContextProperty.WITH_ACTIVE_NON_CONSUMABLE)
      }
    }
    // Save products dictionary
    val oldData = this.getData(productsOnly = true)
    // Get data from API
    this.api.getUser(context) { err, response ->
      // Update user using API data
      this.updateFromApiData(err, response) { updateErr ->
        // Check if the user has been updated
        val newData = this.getData(productsOnly = true)
        var isUpdated = false
        if (!this.sameProducts(newData, oldData)) {
          isUpdated = true
        }
        // Call completion
        completion(updateErr, isUpdated)
      }
    }
  }

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

    this.api.createPurchaseIntent(params) callback@{ err, response ->
      // Check if there is an error
      if (err != null) {
        return@callback completion(err)
      }
      // Update current purchase intent id
      this.purchaseIntent = response?.data?.get("id") as? String
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
        "etag" to this.etag as? Any,
        "isServerLoginEnabled" to this.isServerLoginEnabled,
        "filteredProductsForSale" to this.filteredProductsForSale.map { product -> product.getData() },
        "cacheVersion" to Config.cacheVersion,
        "foregroundRefreshInterval" to this.foregroundRefreshInterval
      ))
    }
    return data
  }

  /*
   * Update user using API data
   */
  private fun updateFromApiData(err: IaphubError?, response: NetworkResponse?, completion: (IaphubError?) -> Unit) {
    var data = response?.data

    // If there's no error
    if (err == null) {
      // Update isServerDataFetched
      this.isServerDataFetched = true
      // Update ETag
      response?.getHeader("ETag")?.let { this.etag = it }
      // Update foreground refresh interval
      response?.getHeader("X-Foreground-Refresh-Interval")?.let { this.foregroundRefreshInterval = it.toLongOrNull() }
    }
    // Handle error or 304 non modified
    if (err != null || response?.hasNotModifiedStatusCode() == true) {
      // Clear products if the platform is disabled
      if (err?.code == "server_error" && err.subcode == "platform_disabled") {
        this.isServerDataFetched = true
        data = mapOf("productsForSale" to emptyList<Any>(), "activeProducts" to emptyList<Any>())
      }
      // Otherwise return an error
      else {
        // Update all products details
        this.updateAllProductsDetails {
          // Call completion
          completion(err)
        }
        return
      }
    }
    // Check data
    if (data == null) {
      return completion(IaphubError(IaphubErrorCode.unexpected))
    }
    // Update data
    this.update(data) { updateErr ->
      // Check error
      if (updateErr != null) {
        return@update completion(updateErr)
      }
      // Call completion
      completion(null)
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
    // Call completion on empty array
    if (productSkus.isEmpty()) {
      return completion()
    }
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
   * Update all products details
   */
  private fun updateAllProductsDetails(completion: () -> Unit) {
    val products: List<Product> = this.productsForSale + this.activeProducts + this.filteredProductsForSale

    this.updateProductsDetails(products) {
      // Detect recovered products
      val recoveredProducts = this.filteredProductsForSale.filter { product -> product.details != null }

      if (recoveredProducts.isNotEmpty()) {
        // Add to list of products for sale
        this.productsForSale = this.productsForSale + recoveredProducts
        // Update filtered products for sale
        this.filteredProductsForSale = this.filteredProductsForSale.filter { product -> product.details == null }
      }
      // Call completion
      completion()
    }
  }

  /*
   * Update filtered products
   */
  @Synchronized
  private fun updateFilteredProducts(completion: (Boolean) -> Unit) {
    // Check if the filtered products are currently being updated and return completion if true
    if (this.isUpdatingFilteredProducts) {
      return completion(false)
    }
    // Update property
    this.isUpdatingFilteredProducts = true
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
        // Call completion
        completion(true)
      }
      // Otherwise just call the completion
      else {
        completion(false)
      }
      // Update property
      this.isUpdatingFilteredProducts = false
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
    this.filteredProductsForSale = listOf()
    this.activeProducts = listOf()
    this.fetchDate = null
    this.receiptPostDate = null
    this.updateDate = null
    this.needsFetch = false
    this.isInitialized = false
    this.isServerLoginEnabled = false
    this.isServerDataFetched = false
    this.etag = null
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
        this.filteredProductsForSale = Util.parseItems<Product>(jsonMap["filteredProductsForSale"], allowNull = true)
        { err, item ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on filtered product for sale\n\n${err.stackTraceToString()}",
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
        this.etag = jsonMap["etag"] as? String
        this.foregroundRefreshInterval = (jsonMap["foregroundRefreshInterval"] as? Number)?.toLong()
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