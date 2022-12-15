package com.iaphub.example

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import com.iaphub.ActiveProduct
import com.iaphub.IaphubError
import com.iaphub.Iaphub
import com.iaphub.Product

class AppModel {

    val productsLoading = MutableLiveData<Boolean>(false)
    val restoreLoading = MutableLiveData<Boolean>(false)
    val productsForSale = MutableLiveData<List<Product>>(listOf())
    val activeProducts = MutableLiveData<List<ActiveProduct>>(listOf())

    constructor() {
        // Refresh products when the user is updated
        Iaphub.setOnUserUpdateListener { ->
            Log.d("IAPHUB", "-> setOnUserUpdateListener called")
            this.refreshProducts()
        }
        // Listen for errors
        Iaphub.setOnErrorListener { err ->
            Log.d("IAPHUB", "-> setOnErrorListener called ${err?.message}")
        }
    }

    fun login(userId: String, completion: (IaphubError?) -> Unit) {
        this.logout()
        Iaphub.login(userId) { err ->
            if (err == null) {
                this.refreshProducts()
            }
            completion(err)
        }
    }

    fun loginAnonymously() {
        this.logout()
        this.refreshProducts()
    }

    fun logout() {
        this.productsForSale.value = listOf()
        this.activeProducts.value = listOf()
        Iaphub.logout()
    }

    fun refreshProducts(completion: ((IaphubError?) -> Unit)? = null) {
        if (this.productsLoading.value == true) return
        this.productsLoading.value = true
        Iaphub.getProducts() { err, productsForSale, activeProducts ->
            if (productsForSale != null) {
                this.productsForSale.value = productsForSale
            }
            if (activeProducts != null) {
                this.activeProducts.value = activeProducts
            }
            this.productsLoading.value = false
            if (completion != null) {
                completion(err)
            }
        }
    }

    fun restoreProducts(completion: ((IaphubError?) -> Unit)? = null) {
        if (this.restoreLoading.value == true) return
        this.restoreLoading.value = true
        Iaphub.restore { err, _ ->
            this.restoreLoading.value = false
            if (err != null) {
                if (completion != null) {
                    completion(err)
                }
                return@restore
            }
            this.refreshProducts(completion)
        }
    }

    fun showManageSubscriptions(completion: ((IaphubError?) -> Unit)? = null) {
        Iaphub.showManageSubscriptions() { err ->
            if (completion != null) {
                completion(err)
            }
        }
    }

    companion object {
        private lateinit var sInstance: AppModel

        @MainThread
        fun get(): AppModel {
            sInstance = if (::sInstance.isInitialized) sInstance else AppModel()
            return sInstance
        }
    }
}