package com.iaphub

internal class ReceiptResponse {

  // Status
  internal val status: String?
  // New transactions
  internal val newTransactions: List<ReceiptTransaction>?
  // Old transactions
  internal val oldTransactions: List<ReceiptTransaction>?

  constructor(data: Map<String, Any>) {
    this.status = data["status"] as? String
    this.newTransactions = Util.parseItems(data["newTransactions"]) { err, _ ->
      IaphubError(IaphubErrorCode.unexpected, "error parsing new transaction of receipt response, transaction ignored, $err")
    }
    this.oldTransactions = Util.parseItems(data["oldTransactions"]) { err, _ ->
      IaphubError(IaphubErrorCode.unexpected, "error parsing old transaction of receipt response, transaction ignored, $err")
    }
  }

  fun findTransactionBySku(sku: String, filter: String? = null, useSubscriptionRenewalProductSku: Boolean = false): ReceiptTransaction? {
    var transactions = mutableListOf<ReceiptTransaction>()

    if (filter == "new" || filter == null) {
      if (this.newTransactions != null) {
        transactions.addAll(this.newTransactions)
      }
    }
    if (filter == "old" || filter == null) {
      if (this.oldTransactions != null) {
        transactions.addAll(this.oldTransactions)
      }
    }

    return transactions.find { transaction ->
      if (useSubscriptionRenewalProductSku) {
        return@find transaction.subscriptionRenewalProductSku == sku
      }
      return@find transaction.sku == sku
    }
  }
}