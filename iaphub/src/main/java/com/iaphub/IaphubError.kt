package com.iaphub

class IaphubError {

  val code: String
  val subcode: String?
  val message: String
  val params: Map<String, Any?>
  var sent: Boolean = false

  internal constructor(error: IaphubErrorCode, suberror: IaphubErrorProtocol? = null, message: String? = null, params: Map<String, Any?> = emptyMap(), silent: Boolean = false) {
    var fullMessage = error.message

    this.code = error.name
    this.subcode = suberror?.name
    this.params = params
    if (suberror != null) {
      fullMessage = "$fullMessage, ${suberror.message}"
    }
    if (message != null && message != "") {
      fullMessage = "$fullMessage, $message"
    }
    this.message = fullMessage
    if (!silent) {
      this.send()
    }
  }

  /**
   * Send
   */
  fun send() {
    if (this.sent == false) {
      this.triggerListener()
      this.sendLog()
    }
  }

  /**
   * Trigger error listener
   */
  fun triggerListener() {
    Util.dispatchToMain {
      Iaphub.onErrorListener?.invoke(this)
    }
  }

  /**
   * Send log
   */
  fun sendLog() {
    // Do not send log if disabled for testing
    if (Iaphub.testing.logs == false) {
      return
    }
    // Check rate limit
    if (!IaphubLogLimit.isAllowed()) {
      return
    }
    // Send request
    Iaphub.user?.api?.postLog(mapOf(
      "data" to mapOf(
        "body" to mapOf(
          "message" to mapOf("body" to this.message)
        ),
        "environment" to Iaphub.environment,
        "platform" to Config.sdk,
        "framework" to Iaphub.sdk,
        "code_version" to Config.sdkVersion,
        "custom" to this.params + mapOf(
          "osVersion" to Iaphub.osVersion,
          "sdkVersion" to Iaphub.sdkVersion,
          "code" to this.code,
          "subcode" to this.subcode
        ),
        "person" to mapOf("id" to Iaphub.appId),
        "context" to "${Iaphub.appId}/${Iaphub.user?.id ?: ""}",
        "fingerprint" to "${Config.sdk}_${this.code}_${this.subcode ?: ""}"
      )
    )) { _, _ ->
      // No need to do anything if there is an error
    }
  }

}