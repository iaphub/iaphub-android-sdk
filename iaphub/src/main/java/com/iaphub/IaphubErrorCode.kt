package com.iaphub

enum class IaphubErrorCode(val message: String) {

  unexpected("An unexpected error has happened"),
  network_error("The remote server couldn't be reached properly"),
  billing_unavailable("The billing is unavailable"),
  anonymous_purchase_not_allowed("Anonymous purchase are not allowed, identify user using the login method or enable the anonymous purchase option"),
  user_cancelled("The purchase has been cancelled by the user"),
  //deferred_payment("The payment has been deferred (awaiting approval from parental control)"),
  product_not_available("The requested product isn't available for purchase"),
  product_already_owned("Product already owned, restore required"),
  receipt_failed("Receipt validation failed, receipt processing will be automatically retried if possible"),
  receipt_invalid("Receipt is invalid"),
  receipt_stale("Receipt is stale, no purchases still valid were found"),
  cross_platform_conflict("Cross platform conflict detected, an active subscription from another platform has been detected"),
  product_already_purchased("Product already purchased, it is already an active product of the user"),
  user_conflict("The transaction is successful but it belongs to a different user, a restore might be needed"),
  transaction_not_found("Transaction not found, the product sku wasn't in the receipt, the purchase failed"),
  //code_redemption_unavailable("Presenting the code redemption is not available (only available on iOS 14+)"),
  user_tags_processing("The user is currently posting tags, please wait concurrent requests not allowed"),
  restore_processing("A restore is currently processing"),
  buy_processing("A purchase is currently processing")
}