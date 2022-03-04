package com.iaphub

class IaphubError {

  val code: String
  val message: String
  val params: Map<String, Any>

  internal constructor(code: IaphubErrorCode, message: String = "", params: Map<String, Any> = emptyMap(), listener: Boolean = true) {
    this.code = code.name
    if (message != "") {
      this.message = code.message + ", " + message
    }
    else {
      this.message = code.message
    }
    this.params = params
    if (listener != false) {
      this.triggerListener()
      this.sendLog()
    }
  }

  constructor(code: String, message: String = "", params: Map<String, Any> = emptyMap(), listener: Boolean = true) {
    this.code = code
    this.message = message
    this.params = params
    if (listener != false) {
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
    // Send request
    Iaphub.user?.api?.postLog(mapOf(
      "data" to mapOf(
        "body" to mapOf(
          "message" to mapOf("body" to this.message)
        ),
        "environment" to Iaphub.environment,
        "platform" to Config.sdk,
        "framework" to Iaphub.sdk,
        "code_version" to Iaphub.sdkVersion,
        "custom" to this.params,
        "person" to mapOf("id" to Iaphub.appId)
      )
    )) { _, _ ->
      // No need to do anything if there is an error
    }
  }

}