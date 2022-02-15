package com.iaphub.example.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.iaphub.Product
import com.iaphub.example.R
import com.iaphub.example.databinding.ViewProductBinding

class ProductsAdapter(private val products: List<Product>, private val listener: ProductsAdapterListener): RecyclerView.Adapter<ProductsAdapter.ProductViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding: ViewProductBinding = DataBindingUtil.inflate(layoutInflater, R.layout.view_product, parent,false)
        return ProductViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return this.products.size
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(this.products[position])
    }

    inner class ProductViewHolder(private val binding: ViewProductBinding): RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.productTitle.text = product.localizedTitle
            binding.productDescription.text = product.localizedDescription
            binding.cardView.setOnClickListener {
                listener.onProductClicked(binding.root, product)
            }
        }

    }

    interface ProductsAdapterListener {
        fun onProductClicked(view: View, product: Product)
    }

}