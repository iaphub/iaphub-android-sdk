package com.iaphub

internal interface IaphubErrorProtocol {
  val name: String
  val message: String
}

internal enum class IaphubErrorCode(override val message: String): IaphubErrorProtocol {
  unexpected("An unexpected error has happened"),
  network_error("The remote server request failed"),
  server_error("The remote server returned an error"),
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

internal enum class IaphubUnexpectedErrorCode(override val message: String): IaphubErrorProtocol {
  start_missing("iaphub not started"),
  receipt_validation_response_invalid("receipt validation failed, response invalid"),
  anonymous_id_keychain_save_failed("saving anonymous id to keychain failed"),
  get_cache_data_item_parsing_failed("error parsing item of cache data"),
  save_cache_data_json_invalid("cannot save cache date, not a valid json object"),
  save_cache_keychain_failed("cannot save cache date, save to keychain failed"),
  user_id_invalid("user id invalid"),
  update_item_parsing_failed("error parsing item of api in order to update user"),
  product_missing_from_store("google play did not return the product, the product has been filtered, if the sku is valid your GooglePlay account or sandbox environment is probably not configured properly (https://iaphub.com/docs/set-up-android/configure-sandbox-testing)"),
  post_receipt_data_missing("post receipt data missing"),
  receipt_transation_parsing_failed("receipt transaction parsing from data failed, transaction ignored"),
  end_connection_failed("google play pilling end connection failed"),
  product_details_parsing_failed("product details parsing failed"),
  billing_ready_timeout("Google Play billing not ready, timeout triggered"),
  consume_failed("consume transaction failed"),
  acknowledge_failed("acknowledge transaction failed"),
  get_sku_details_failed("get skus details failed"),
  billing_developer_error("the google play billing library isn't used properly"),
  billing_error("fatal error during google play billing API action"),
  proration_mode_invalid("proration mode invalid"),
  subscription_replace_failed("subscription replace failed"),
  compare_products_failed("compare products failed")
}

internal enum class IaphubNetworkErrorCode(override val message: String): IaphubErrorProtocol {
  url_invalid("url invalid"),
  request_failed("request failed"),
  billing_request_failed("a request by the google play billing failed"),
  billing_request_timeout("a request by the google play billing failed with a timeout"),
  response_empty("response empty"),
  response_parsing_failed("response parsing failed"),
  unknown_exception("unknown exception")
}

internal class IaphubCustomError: IaphubErrorProtocol {

  override val name: String
  override val message: String

  constructor(name: String, message: String) {
    this.name = name
    this.message = message
  }

}