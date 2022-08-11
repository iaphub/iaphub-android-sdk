package com.iaphub.example.store

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.iaphub.Iaphub
import com.iaphub.IaphubError
import com.iaphub.Product
import com.iaphub.ReceiptTransaction
import com.iaphub.example.AppModel
import com.iaphub.example.R
import com.iaphub.example.databinding.FragmentStoreBinding
import com.iaphub.example.login.LoginFragmentDirections

class StoreFragment: Fragment(), ProductsAdapter.ProductsAdapterListener {

    private val app: AppModel = AppModel.get()
    private lateinit var store: StoreViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentStoreBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_store, container, false
        )
        // Init model
        val application = requireNotNull(this.activity).application
        this.store = StoreViewModel(application)
        binding.app = this.app
        binding.store = this.store
        binding.lifecycleOwner = viewLifecycleOwner
        // Init recycler view for products for sale
        binding.productsForSaleRecyclerView.layoutManager = LinearLayoutManager(this.context)
        this.app.productsForSale.observe(viewLifecycleOwner, Observer {
            binding.productsForSaleRecyclerView.adapter = ProductsAdapter(it, this)
        })
        // Init recycler view for active products
        binding.activeProductsRecyclerView.layoutManager = LinearLayoutManager(this.context)
        this.app.activeProducts.observe(viewLifecycleOwner, Observer {
            binding.activeProductsRecyclerView.adapter = ProductsAdapter(it, this)
        })
        // Listen to navigateToLogin property
        this.store.navigateToLogin.observe(viewLifecycleOwner, Observer { value ->
            if (value == true) {
                this.navigateToLogin()
                this.store.logoutDone()
            }
        })

        return binding.root
    }

    private fun navigateToLogin() {
        NavHostFragment.findNavController(this).popBackStack()
    }

    override fun onProductClicked(view: View, product: Product) {
        val application = requireNotNull(this.activity).application

        Iaphub.buy(requireActivity(), product.sku) { err, transaction ->
            var message: String? = null

            if (err != null) {
                if (err.code == "user_cancelled" || err.code == "product_already_purchased") {
                    // No need to show a message here
                }
                // The billing is unavailable
                else if (err.code == "billing_unavailable") {
                    message = "In-app billing not available, make sure you're connected to your Google Play account and your Google Play Store is up to date"
                }
                // The payment has been deferred (transaction pending, its final status is pending external action)
                else if (err.code == "deferred_payment") {
                    message = "Purchase awaiting approval, your purchase has been processed but is awaiting approval"
                }
                /*
                 * The remote server couldn't be reached properly
                 * The user will have to restore its purchases in order to validate the transaction
                 * An automatic restore should be triggered on every relaunch of your app since the transaction hasn't been 'finished'
                 */
                else if (err.code == "network_error") {
                    message = "Please try to restore your purchases later (Button in the settings) or contact the support (support@myapp.com)"
                }
                /*
                 * The receipt has been processed on IAPHUB but something went wrong
                 * It is probably because of an issue with the configuration of your app or a call to the Itunes/GooglePlay API that failed
                 * IAPHUB will send you an email notification when a receipt fails, by checking the receipt on the dashboard you'll find a detailed report of the error
                 * After fixing the issue (if there's any), just click on the 'New report' button in order to process the receipt again
                 * If it is an error contacting the Itunes/GooglePlay API, IAPHUB will retry to process the receipt automatically as well
                 */
                else if (err.code == "receipt_failed") {
                    message = "We're having trouble validating your transaction, give us some time we'll retry to validate your transaction ASAP!"
                }
                /*
                 * The receipt has been processed on IAPHUB but is invalid
                 * It could be a fraud attempt, using apps such as Freedom or Lucky Patcher on an Android rooted device
                 */
                else if (err.code == "receipt_invalid") {
                    message = "We were not able to process your purchase, if you've been charged please contact the support (support@myapp.com)"
                }
                /*
                 * The user has already an active subscription on a different platform (android or ios)
                 * This security has been implemented to prevent a user from ending up with two subscriptions of different platforms
                 * You can disable the security by providing the 'crossPlatformConflict' parameter to the buy method (Iaphub.buy(sku: sku, crossPlatformConflict: false))
                */
                else if (err.code == "cross_platform_conflict") {
                    message = "Seems like you already have a subscription on a different platform\nYou have to use the same platform to change your subscription or wait for your current subscription to expire";
                }
                /*
                 * The transaction is successful but the product belongs to a different user
                 * You should ask the user to use the account with which he originally bought the product or ask him to restore its purchases in order to transfer the previous purchases to the new account
                 */
                else if (err.code == "user_conflict") {
                    message = "Product owned by a different user\nPlease use the account with which you originally bought the product or restore your purchases"
                }
            }
            // If there is no error, check the webhook status (if it is enabled)
            else {
                if (transaction?.webhookStatus != "failed") {
                    message = "Purchase successful!"
                }
                else {
                    message = "Your purchase was successful but we need some more time to validate it, should arrive soon! Otherwise contact the support (support@myapp.com)"
                }
            }
            // Show message
            if (message != null) {
                Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

}