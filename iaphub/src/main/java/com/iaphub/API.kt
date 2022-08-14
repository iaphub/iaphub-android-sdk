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
  fun login(userId: String, completion: (IaphubError?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/user/${this.user.id}/login",
      params=mapOf("userId" to userId),
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
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/user/${this.user.id}/receipt",
      params=receipt,
      timeout=45,
      completion=completion
    )
  }

  /*
   * Post pricing
   */
  fun postPricing(pricing: Map<String, Any>, completion: (IaphubError?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/user/${this.user.id}/pricing",
      params=pricing,
      completion={ err, _ -> completion(err)}
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
   * Post log
   */
  fun postLog(params: Map<String, Any>, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    this.network.send(
      type="POST",
      route="/app/${this.user.sdk.appId}/log",
      timeout=2,
      retry=0,
      silentLog=true,
      params=params,
      completion=completion
    )
  }

}