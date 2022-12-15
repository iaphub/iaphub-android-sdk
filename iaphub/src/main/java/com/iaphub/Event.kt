package com.iaphub

class Event: Parsable {

  // Event type
  var type: String
  // Event tags
  var tags: List<String>
  // Event transaction
  var transaction: ReceiptTransaction

  constructor(data: Map<String, Any?>): super(data) {
    this.type = data["type"] as String
    this.tags = data["tags"] as List<String>
    this.transaction = ReceiptTransaction(data["transaction"] as Map<String, Any?>)
  }

  open fun getData(): Map<String, Any?> {
    return mapOf(
      "type" to this.type as? Any?,
      "tags" to this.tags as? Any?,
      "transaction" to this.transaction.getData()
    )
  }

}