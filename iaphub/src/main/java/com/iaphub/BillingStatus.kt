package com.iaphub

class BillingStatus {

  // Error
  val error: IaphubError?
  // Filtered product ids
  val filteredProductIds: List<String>

  constructor(error: IaphubError? = null, filteredProductIds: List<String> = emptyList()) {
    this.error = error
    this.filteredProductIds = filteredProductIds
  }

  fun getData(): Map<String, Any?> {
    return mapOf(
      "error" to this.error?.getData(),
      "filteredProductIds" to this.filteredProductIds
    )
  }

}