package com.iaphub

class Products {

  // Active products
  val activeProducts: List<ActiveProduct>
  // Products for sale
  val productsForSale: List<Product>

  constructor(activeProducts: List<ActiveProduct>, productsForSale: List<Product>) {
    this.activeProducts = activeProducts
    this.productsForSale = productsForSale
  }

  fun getData(): Map<String, Any?> {
    return mapOf(
      "activeProducts" to this.activeProducts.map { product -> product.getData() },
      "productsForSale" to this.productsForSale.map { product -> product.getData() }
    )
  }

}