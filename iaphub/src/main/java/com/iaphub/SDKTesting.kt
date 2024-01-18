package com.iaphub

import androidx.annotation.VisibleForTesting

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class SDKTesting(private val sdk: SDK) {
  // Disable/enable the lifecycle event
  var lifecycleEvent: Boolean? = null
  // Disable/enable the mock of the store library
  var storeLibraryMock: Boolean? = null
  // Disable/enable the store ready state
  var storeReady: Boolean? = null
  // Disable/enable the store ready timeout (default 10s)
  var storeReadyTimeout: Long? = null
  // Mocked product details
  var mockedProductDetails: List<ProductDetails>? = null
  // Disable/enable logs
  var logs: Boolean? = null
  // Disable/enable pricing cache
  var pricingCache: Boolean? = null

  /**
   * Notify store ready
   */
  fun notifyStoreReady() {
    this.sdk.store?.notifyBillingReady()
  }

  /**
   * Mock network request
   */
  fun mockNetworkRequest(mock: ((String, String, Map<String, Any>) -> Map<String, Any>?)?) {
    this.sdk.user?.api?.network?.mockRequest(mock)
  }

  /**
   * Clear cached user
   */
  fun clearCachedUser() {
    Util.setToCache(context=this.sdk.context, key="iaphub_user_a_${this.sdk.appId}", value=null)
    Util.setToCache(context=this.sdk.context, key="iaphub_user_${this.sdk.appId}", value=null)
  }

  /**
   * Force user refresh
   */
  fun forceUserRefresh() {
    this.sdk.user?.resetCache()
  }

  /**
   * Get from cache
   */
  fun getFromCache(key: String): String? {
    return Util.getFromCache(context=this.sdk.context, key=key)
  }

  /**
   * Get app id
   */
  fun getAppId(): String? {
    return this.sdk.appId
  }

}