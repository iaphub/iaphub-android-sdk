package com.iaphub

internal interface IaphubErrorProtocol {
  val name: String
  val message: String
}

internal enum class IaphubErrorCode(override val message: String): IaphubErrorProtocol {
  unexpected("An unexpected error has happened"),
  network_error("The remote server request failed"),
  server_error("The remote server returned an error"),
  billing_unavailable("The billing service is unavailable"),
  anonymous_purchase_not_allowed("Anonymous purchase are not allowed, identify user using the login method or enable the anonymous purchase option"),
  user_cancelled("The purchase has been cancelled by the user"),
  deferred_payment("The payment has been deferred (transaction pending, its final status is pending external action)"),
  product_not_available("The requested product isn't available for purchase"),
  product_already_owned("Product already owned"),
  receipt_failed("Receipt validation failed, receipt processing will be automatically retried if possible"),
  cross_platform_conflict("Cross platform conflict detected, an active subscription from another platform has been detected"),
  cross_account_conflict("Cross account conflict detected, the currently active subscription belongs to a different Google account and cannot be replaced"),
  product_already_purchased("Product already purchased, it is already an active product of the user"),
  product_change_next_renewal("The product will be changed on the next renewal date"),
  user_conflict("The transaction is successful but it belongs to a different user, a restore might be needed"),
  transaction_not_found("Transaction not found, the product sku wasn't in the receipt, the purchase failed"),
  manage_subscriptions_unavailable("Manage subscriptions unavailable"),
  //code_redemption_unavailable("Presenting the code redemption is not available (only available on iOS 14+)"),
  user_tags_processing("The user is currently posting tags, please wait concurrent requests not allowed"),
  restore_processing("A restore is currently processing"),
  buy_processing("A purchase is currently processing")
}

internal enum class IaphubBillingUnavailableErrorCode(override val message: String): IaphubErrorProtocol {
  play_store_outdated("The Play Store app on the user's device is out of date, it must be updated"),
  billing_ready_timeout("Google Play billing not ready, timeout triggered")
}

internal enum class IaphubUnexpectedErrorCode(override val message: String): IaphubErrorProtocol {
  start_missing("iaphub not started"),
  receipt_validation_response_invalid("receipt validation failed, response invalid"),
  get_cache_data_json_parsing_failed("get cache data json parsing failed"),
  get_cache_data_item_parsing_failed("error parsing item of cache data"),
  save_cache_data_json_invalid("cannot save cache data, not a valid json object"),
  save_cache_anonymous_id_failed("cannot save anonymous id in cache"),
  user_id_invalid("user id invalid"),
  update_item_parsing_failed("error parsing item of api in order to update user"),
  product_missing_from_store("google play did not return the product, the product has been filtered (https://www.iaphub.com/docs/troubleshooting/product-not-returned)"),
  post_receipt_data_missing("post receipt data missing"),
  receipt_transation_parsing_failed("receipt transaction parsing from data failed, transaction ignored"),
  end_connection_failed("google play billing end connection failed"),
  product_details_parsing_failed("product details parsing failed"),
  product_parsing_failed("product parsing failed"),
  consume_failed("consume transaction failed"),
  acknowledge_failed("acknowledge transaction failed"),
  billing_developer_error("the billing service isn't used properly"),
  billing_error("the billing service returned an error"),
  proration_mode_invalid("proration mode invalid"),
  subscription_replace_failed("subscription replace failed"),
  compare_products_failed("compare products failed"),
  date_parsing_failed("the parsing of a date failed"),
  intro_phase_parsing_failed("the parsing of a intro phase failed"),
  property_missing("a property is missing")
}

internal enum class IaphubNetworkErrorCode(override val message: String): IaphubErrorProtocol {
  url_invalid("url invalid"),
  request_failed("request failed"),
  billing_request_failed("a request by the google play billing failed"),
  response_empty("response empty"),
  response_parsing_failed("response parsing failed"),
  status_code_error("status code error"),
  unknown_exception("unknown exception")
}

internal enum class IaphubReceiptErrorCode(override val message: String): IaphubErrorProtocol {
  receipt_failed("receipt processing failed"),
  receipt_invalid("receipt invalid"),
  receipt_stale("receipt stale, no purchases still valid were found"),
  receipt_expired("receipt expired"),
  receipt_processing("receipt currently processing")
}

internal class IaphubCustomError: IaphubErrorProtocol {

  override val name: String
  override val message: String

  constructor(name: String, message: String) {
    this.name = name
    this.message = message
  }

}