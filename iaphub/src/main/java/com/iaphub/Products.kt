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

}