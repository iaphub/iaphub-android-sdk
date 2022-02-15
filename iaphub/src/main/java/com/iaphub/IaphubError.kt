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
    }
  }

  constructor(code: String, message: String = "", params: Map<String, Any> = emptyMap(), listener: Boolean = true) {
    this.code = code
    this.message = message
    this.params = params
    if (listener != false) {
      this.triggerListener()
    }
  }

  fun triggerListener() {
    Util.dispatchToMain {
      Iaphub.onErrorListener?.invoke(this)
    }
  }

}