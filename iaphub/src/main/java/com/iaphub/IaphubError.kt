package com.iaphub

class IaphubError {

  val code: String
  val subcode: String?
  val message: String
  val params: Map<String, Any?>
  val fingerprint: String
  var sent: Boolean = false

  internal constructor(error: IaphubErrorCode, suberror: IaphubErrorProtocol? = null, message: String? = null, params: Map<String, Any?> = emptyMap(), silent: Boolean = false, silentLog: Boolean = false, fingerprint: String = "") {
    var fullMessage = error.message

    this.code = error.name
    this.subcode = suberror?.name
    this.params = params
    this.fingerprint = fingerprint
    if (suberror != null) {
      fullMessage = "$fullMessage, ${suberror.message}"
    }
    if (message != null && message != "") {
      fullMessage = "$fullMessage, $message"
    }
    this.message = fullMessage
    if (!silent) {
      this.send(silentLog)
    }
  }

  /**
   * Send
   */
  fun send(silentLog: Boolean = false) {
    // Ignore some server errors (they are not real errors)
    if (this.code == "server_error" && listOf("user_not_found", "user_authenticated").contains(this.subcode)) {
      return
    }
    // Ignore if already sent
    if (this.sent) {
      return
    }
    // Trigger listener and send log
    this.sent = true
    this.triggerListener()
    if (!silentLog) {
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
    // Ignore some errors when sending a log isn't necessary
    if (listOf("user_cancelled", "deferred_payment").contains(this.code)) {
      return
    }
    // Check rate limit
    if (!IaphubLogLimit.isAllowed()) {
      return
    }
    // Build fingerprint
    var fullFingerprint = "${Config.sdk}_${this.code}_${this.subcode ?: ""}"
    if (this.fingerprint != "") {
      fullFingerprint = "${fullFingerprint}_${this.fingerprint}"
    }
    // Send log
    Iaphub.user?.sendLog(mapOf(
      "message" to this.message,
      "params" to this.params + mapOf(
        "osVersion" to Iaphub.osVersion,
        "sdkVersion" to Iaphub.sdkVersion,
        "code" to this.code,
        "subcode" to this.subcode
      ),
      "fingerprint" to fullFingerprint
    ))
  }

  fun getData(): Map<String, Any?> {
    return mapOf(
      "code" to this.code,
      "subcode" to this.subcode,
      "message" to this.message,
      "params" to this.params
    )
  }

}