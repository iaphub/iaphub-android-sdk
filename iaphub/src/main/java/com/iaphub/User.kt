package com.iaphub

import android.app.Activity
import android.util.Log
import java.lang.Exception
import java.util.*

internal class User {

  internal val sdk: SDK
  internal val api: API
  internal val onUserUpdate: () -> Unit

  internal var id: String
  internal var iaphubId: String? = null
  internal var productsForSale: List<Product> = listOf()
  internal var activeProducts: List<ActiveProduct> = listOf()
  internal var pricings: List<ProductPricing> = listOf()
  internal var fetchDate: Date? = null
  internal var receiptPostDate: Date? = null
  internal var updateDate: Date? = null
  internal var fetchRequests: MutableList<(IaphubError?, Boolean) -> Unit> = mutableListOf()
  internal var isFetching: Boolean = false
  internal var isInitialized: Boolean = false
  internal var needsFetch: Boolean = false
  internal var isPostingTags: Boolean = false

  constructor(id: String?, sdk: SDK, onUserUpdate: () -> Unit) {
    this.sdk = sdk
    this.onUserUpdate = onUserUpdate
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

    // Refresh user
    this.refresh() { err, _, _ ->
      // Check if there is an error
      if (err != null) {
        return@refresh completion(err, null)
      }
      // Get product details
      this.getProductBySku(sku) { err, product ->
        // Check error
        if (product == null) {
          return@getProductBySku completion(err, null)
        }
        // Check for cross platform conflicts if it is a subscription
        if (product.type.contains("subscription")) {
          val conflictedSubscription = this.activeProducts.find { item -> item.type.contains("subscription") && item.platform != "android" }
          if (crossPlatformConflict && conflictedSubscription != null) {
            return@getProductBySku completion(IaphubError(IaphubErrorCode.cross_platform_conflict, null,"platform: ${conflictedSubscription.platform}"), null)
          }
        }
        // Check for renewable subscription replace
        if (product.type == "renewable_subscription" && product.group != null) {
          val subscriptionToReplace = this.activeProducts.find { item -> item.type == "renewable_subscription" && item.group == product.group && item.androidToken != null }

          if (subscriptionToReplace != null) {
            options["oldPurchaseToken"] = subscriptionToReplace.androidToken
          }
        }
        // Launch purchase
        this.sdk.store?.buy(activity, options, completion)
      }
    }
  }

  /**
   * Restore products
   */
  fun restore(completion: (IaphubError?) -> Unit) {
    // Launch restore
    this.sdk.store?.restore() { err ->
      // Update updateDate
      this.updateDate = Date()
      // Refresh user
      this.refresh(interval = 0, force = true) { _, _, _ ->
        completion(err)
      }
    }
  }

  /*
   * Refresh user
   */
  fun refresh(interval: Long, force: Boolean = false, completion: ((IaphubError?, Boolean, Boolean) -> Unit)? = null) {
    // Check if we need to fetch the user
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
      // Fetch user
      this.fetch {err, isUpdated ->
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
    // Otherwise no need to fetch the user
    else {
      completion?.let { it(null, false, false) }
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
   * Get products (active and for sale)
   */
  fun getProducts(includeSubscriptionStates: List<String> = listOf(), completion: (IaphubError?, List<Product>?, List<ActiveProduct>?) -> Unit) {
    this.getActiveProducts(includeSubscriptionStates) { err, activeProducts ->
      // Check if there is an error
      if (err != null) {
        return@getActiveProducts completion(err, null, null)
      }
      // Otherwise return the products
      completion(null, this.productsForSale, activeProducts)
    }
  }

  /*
   * Get product by its sku
   */
  fun getProductBySku(sku: String, completion: (IaphubError?, Product?) -> Unit) {
    // Search in products for sale
    var product = this.productsForSale.find { product -> product.sku == sku }
    // Search in active products
    if (product == null) {
      this.activeProducts.find { product -> product.sku == sku }
    }
    // Return product
    if (product != null) {
      completion(null, product)
    }
    // Otherwise return error
    else {
      completion(IaphubError(error=IaphubErrorCode.product_not_available, params=mapOf("sku" to sku)), null)
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
    val shouldCallApi = this.isAnonymous() && this.fetchDate != null
    // Update id
    this.id = userId
    // Reset user
    this.reset()
    // No need to call API if not necessary
    if (!shouldCallApi) return completion(null)
    // Call API
    this.api.login(userId) { err ->
      // Check for error
      if (err != null) {
        // Ignore error if user not found or already authenticated
        if (arrayOf("user_not_found", "user_authenticated").contains(err.code)) {
          return@login completion(null)
        }
        return@login completion(err)
      }
      // Call completion
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
   * Post receipt
   */
  fun postReceipt(receipt: Receipt, completion: (IaphubError?, ReceiptResponse?) -> Unit) {
    this.api.postReceipt(receipt.getData()) { err, data ->
      // Check for error
      if (err != null || data == null) {
        return@postReceipt completion(err ?: IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.post_receipt_data_missing), null)
      }
      // Update updateDate
      this.updateDate = Date()
      // Update receipt post date
      this.receiptPostDate = Date()
      // Parse and return receipt response
      completion(null, ReceiptResponse(data))
    }
  }

  /*
   * Set subscription renewal product
   */
  fun setSubscriptionRenewalProduct(purchaseId: String, sku: String, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    this.api.editPurchase(purchaseId, mapOf("subscriptionRenewalProductSku" to sku), completion)
  }

  /*
   * Reset cache
   */
  fun resetCache() {
    this.needsFetch = true
  }

  /******************************** PRIVATE ********************************/

  /*
   * Fetch user
   */
  @Synchronized
  fun fetch(completion: (IaphubError?, Boolean) -> Unit) {
    var isUpdated = false

    // Check if the user id is valid
    if (!this.isAnonymous() && !this.isValidId(this.id)) {
      return completion(IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.user_id_invalid, "fetch failed, id: ${this.id}", mapOf("userId" to this.id)), isUpdated)
    }
    // Add completion to the requests
    this.fetchRequests.add(completion)
    // Stop here if the user is currently being fetched
    if (this.isFetching) {
      return
    }
    this.isFetching = true
    // Method to complete fetch request
    fun completeFetchRequest(err: IaphubError?) {
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
      fetchRequests.forEach { request ->
        request(err, isUpdated)
        // Only mark as updated for the first request
        if (isUpdated) {
          isUpdated = false
          this.onUserUpdate()
        }
      }
    }
    // Get cache data (only if fetching for the first time)
    this.getCacheData {
      // Get data from API
      this.api.getUser { err, data ->
        // Check error
        if (err != null || data == null) {
          return@getUser completeFetchRequest(err)
        }
        // Save products dictionary
        val oldData = this.getData(productsOnly = true)
        // Update data
        this.update(data) { err ->
          // Check error
          if (err != null) {
            return@update completeFetchRequest(err)
          }
          // Check if the user has been updated
          val newData = this.getData(productsOnly = true)
          if (this.isInitialized && !this.sameProducts(newData, oldData)) {
            isUpdated = true
          }
          // Update pricing
          this.updatePricings { err ->
            // No need to throw an error if the pricing update fails, the system can work without it
            completeFetchRequest(null)
          }
        }
      }
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
        "fetchDate" to Util.dateToIsoString(this.fetchDate),
        "pricings" to this.pricings.map { pricing -> pricing.getData() }
      ))
    }
    return data
  }

  /*
   * Update user with data
   */
  private fun update(data: Map<String, Any>, completion: (IaphubError?) -> Unit) {
    val productsForSale = Util.parseItems<Product>(data["productsForSale"]) { err, item ->
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.update_item_parsing_failed,
        message="product for sale, ${err}",
        params=mapOf("item" to item)
      )
    }
    val activeProducts = Util.parseItems<ActiveProduct>(data["activeProducts"]) { err, item ->
      IaphubError(
        IaphubErrorCode.unexpected,
        IaphubUnexpectedErrorCode.update_item_parsing_failed,
        message="active product, ${err}",
        params=mapOf("item" to item)
      )
    }
    val products = productsForSale + activeProducts
    val productSkus = products.map { product -> product.sku }

    this.sdk.store?.getProductsDetails(productSkus) { err, productsDetails ->
      // Check err
      if (err != null) {
        return@getProductsDetails completion(err)
      }
      // Otherwise assign data to the product
      productsDetails?.forEach { productDetail ->
        val product = products.find { product -> product.sku == productDetail.sku }

        product?.setDetails(productDetail)
      }
      // Filter products for sale with no details
      this.productsForSale = productsForSale.filter { product ->
        if (product.details == null) {
          IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.product_missing_from_store, "sku: ${product.sku}", mapOf("sku" to product.sku))
        }
        return@filter product.details != null
      }
      // No need to filter active products
      this.activeProducts = activeProducts
      // Update iaphub id
      this.iaphubId = data["id"] as? String
      // Mark needsFetch as false
      this.needsFetch = false
      // Call completion
      completion(null)
    }
  }

  /*
   * Reset user
   */
  private fun reset() {
    this.productsForSale = listOf()
    this.activeProducts = listOf()
    this.pricings = listOf()
    this.fetchDate = null
    this.receiptPostDate = null
    this.updateDate = null
    this.needsFetch = false
    this.isInitialized = false
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

    if (cacheId != null) {
      return cacheId
    }

    val newId = Config.anonymousUserPrefix + UUID.randomUUID().toString()
    val result = Util.setToCache(context=this.sdk.context, key=key, value=newId)

    if (result == false) {
      IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.anonymous_id_keychain_save_failed)
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
      if (jsonMap != null && (jsonMap["id"] as? String == this.id)) {
        this.fetchDate = Util.dateFromIsoString(jsonMap["fetchDate"]) { exception ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on fetch date, $exception",
            params=mapOf("fetchDate" to jsonMap["fetchDate"])
          )
        }
        this.productsForSale = Util.parseItems<Product>(jsonMap["productsForSale"]) { err, item ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on product for sale, $err",
            params=mapOf("item" to item)
          )
        }
        this.activeProducts = Util.parseItems<ActiveProduct>(jsonMap["activeProducts"]) { err, item ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on active product, $err",
            params=mapOf("item" to item)
          )
        }
        this.pricings = Util.parseItems<ProductPricing>(jsonMap["pricings"]) { err, item ->
          IaphubError(
            IaphubErrorCode.unexpected,
            IaphubUnexpectedErrorCode.get_cache_data_item_parsing_failed,
            message="issue on pricing, $err",
            params=mapOf("item" to item)
          )
        }
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
      val result = Util.setToCache(
        context = this.sdk.context,
        key = "${prefix}_${this.sdk.appId}",
        value = jsonString
      )

      if (result == false) {
        IaphubError(IaphubErrorCode.unexpected, IaphubUnexpectedErrorCode.save_cache_keychain_failed)
      }
    }
  }

  /*
   * Update pricings
   */
  private fun updatePricings(completion: (IaphubError?) -> Unit) {
    val products = this.productsForSale + this.activeProducts
    // Convert the products to an array of product pricings
    val pricings = products.map { product ->
      val price = product.price
      val currency = product.currency

      if (price != null && currency != null) {
        return@map ProductPricing(id=product.id, price=price, currency=currency)
      }
      return@map null
    }.filterNotNull()
    // Compare latest pricing with the previous one
    val samePricings = pricings.filter { newPricing ->
      // Look if we already have the pricing in memory
      val pricingFound = this.pricings.find { oldPricing ->
        if (oldPricing.id == newPricing.id && oldPricing.price == newPricing.price && oldPricing.currency == newPricing.currency) {
          return@find true
        }
        return@find false
      }
      return@filter pricingFound != null
    }
    // No need to send a request if the array of pricings is empty
    if (pricings.isEmpty()) {
      return completion(null)
    }
    // No need to send a request if the pricing is the same
    if (samePricings.size == pricings.size) {
      return completion(null)
    }
    // Post pricing
    val pricingsData: List<Map<String, Any>> = pricings.map { pricing -> pricing.getData()}
    this.api.postPricing(mapOf("products" to pricingsData)) { err ->
      // Check error
      if (err != null) {
        return@postPricing completion(err)
      }
      // Update pricings
      this.pricings = pricings
      // Call completion
      completion(null)
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