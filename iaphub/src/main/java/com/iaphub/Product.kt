package com.iaphub

import java.util.LinkedHashMap

open class Product: ProductDetails {

  // Product id
  var id: String
  // Product type
  var type: String
  // Group id
  var group: String?
  // Group name
  var groupName: String?

  // Details source
  internal var details: ProductDetails? = null

  constructor(data: Map<String, Any?>): super(data, false) {
    this.id = data["id"] as String
    this.type = data["type"] as String
    this.group = data["group"] as? String
    this.groupName = data["groupName"] as? String
  }

  constructor(data: Map<String, Any?>, allowEmptySku: Boolean = false): super(data, allowEmptySku) {
    this.id = data["id"] as String
    this.type = data["type"] as String
    this.group = data["group"] as? String
    this.groupName = data["groupName"] as? String
  }

  override fun getData(): Map<String, Any?> {
    var data1 = super.getData()
    val data2 = mapOf(
      "id" to this.id as? Any?,
      "type" to this.type as? Any?,
      "group" to this.group as? Any?,
      "groupName" to this.groupName as? Any?
    )

    return LinkedHashMap(data1).apply { putAll(data2) }
  }

  override fun setDetails(details: ProductDetails) {
    super.setDetails(details)
    this.details = details
  }

}