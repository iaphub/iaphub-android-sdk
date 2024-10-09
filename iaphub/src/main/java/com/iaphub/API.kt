package com.iaphub

internal class API {

  val network: Network
  val user: User

  constructor(user: User) {
    this.user = user
    this.network = Network(endpoint = Config.api)
    this.network.setHeaders(mapOf(
      "Authorization" to "ApiKey ${this.user.sdk.apiKey}"
    ))
    this.network.setParams(mapOf(
      "environment" to this.user.sdk.environment,
      "platform" to "android",
      "sdk" to this.user.sdk.sdk,
      "sdkVersion" to this.user.sdk.sdkVersion,
      "osVersion" to this.user.sdk.osVersion
    ))
  }

  /*
   * Get user
   */
  fun getUser(completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    var params: MutableMap<String, Any> = mutableMapOf()

    // Add updateDate
    val updateDate = this.user.updateDate
    if (updateDate != null) {
      params.put("updateDate", "${updateDate.getTime()}")
    }
    // Add device params
    for ((key, value) in this.user.sdk.deviceParams) {
      params.put("params.${key}", value)
    }
    // Add deferredPurchase parameter
    if (!this.user.enableDeferredPurchaseListener) {
      params.put("deferredPurchase", "false")
    }
    // Add lang parameter
    if (this.user.sdk.lang != "") {
      params.put("lang", this.user.sdk.lang)
    }
    this.network.send(
      type="GET",
      route="/app/${this.user.sdk.appId}/user/${this.user.id}",
      params=params,
      completion=completion
    )
  }

  /*
   * Login
   */
  fun login(currentUserId: String, newUserId: String, completion: (IaphubError?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/user/${currentUserId}/login",
      retry=0,
      connectTimeout=2,
      params=mapOf("userId" to newUserId),
      completion={ err, _ -> completion(err)}
    )
  }

  /*
   * Post tags
   */
  fun postTags(tags: Map<String, Any>, completion: (IaphubError?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/user/${this.user.id}/tags",
      params=mapOf("tags" to tags),
      completion={ err, _ -> completion(err)}
    )
  }

  /*
   * Post receipt
   */
  fun postReceipt(receipt: Map<String, Any>, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    var timeout: Long = 31
    var connectTimeout: Long = 4
    val params = receipt.toMutableMap()

    // Add lang parameter
    if (this.user.sdk.lang != "") {
      params.put("lang", this.user.sdk.lang)
    }
    // Update timeout to 65 seconds for purchase context
    if (receipt["context"] as? String == "purchase") {
      timeout = 61
      connectTimeout = 8
    }
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/user/${this.user.id}/receipt",
      params=params,
      timeout=timeout,
      connectTimeout=connectTimeout,
      completion=completion
    )
  }

  /*
   * Edit purchase
   */
  fun editPurchase(purchaseId: String, params: Map<String, Any>, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/purchase/${purchaseId}",
      params=params,
      completion=completion
    )
  }

  /*
   * Create purchase intent
   */
  fun createPurchaseIntent(params: Map<String, Any>, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/user/${this.user.id}/purchase/intent",
      params=params,
      completion=completion
    )
  }

  /*
   * Confirm purchase intent
   */
  fun confirmPurchaseIntent(id: String, params: Map<String, Any>, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/purchase/intent/${id}/confirm",
      params=params,
      completion=completion
    )
  }

  /*
   * Post log
   */
  fun postLog(params: Map<String, Any>, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/log",
      connectTimeout=2,
      timeout=2,
      retry=0,
      silentLog=true,
      params=params,
      completion=completion
    )
  }

}