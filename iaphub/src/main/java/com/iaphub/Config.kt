package com.iaphub

internal object Config {
  // API endpint
  val api: String = "https://api.iaphub.com/v1"
  // Anonymous user prexix
  val anonymousUserPrefix: String = "a:"
  // SDK platform
  val sdk = "android"
  // SDK version
  val sdkVersion = "4.3.1"
  // Cache version (Increment it when cache needs to be reset because of a format change)
  val cacheVersion = "1"
}