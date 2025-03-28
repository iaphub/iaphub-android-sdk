package com.iaphub.testintegration

import android.content.Context
import org.junit.runner.RunWith
import org.junit.*
import android.util.Log
import androidx.test.core.app.launchActivity
import androidx.test.runner.AndroidJUnit4
import com.iaphub.*
import net.jodah.concurrentunit.Waiter
import org.junit.runners.MethodSorters

import java.util.*
import kotlin.concurrent.timerTask

var iaphubStarted = false
var context: Context? = null

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestIntegration {

    val scenario = launchActivity<MainActivity>()
    var errorCount = 0
    var userUpdateCount = 0
    var processReceiptCount = 0
    var deferredPurchases: MutableList<ReceiptTransaction> = mutableListOf()

    @Before
    fun setup() {
        this.scenario.onActivity {
            // Start IAPHUB
            if (iaphubStarted == false) {
                // Set testing options
                Iaphub.testing.storeLibraryMock=true
                Iaphub.testing.storeReady=true
                Iaphub.testing.storeReadyTimeout = 2000
                Iaphub.testing.lifecycleEvent=false
                Iaphub.testing.logs=false
                Iaphub.testing.mockedProductDetails = listOf(
                    ProductDetails(mapOf(
                        "sku" to "consumable",
                        "price" to 1.99,
                        "currency" to "USD",
                        "localizedPrice" to "$1.99",
                        "localizedTitle" to "Consumable",
                        "localizedDescription" to "This is a consumable"
                    )),
                    ProductDetails(mapOf(
                        "sku" to "renewable_subscription",
                        "price" to 9.99,
                        "currency" to "USD",
                        "localizedPrice" to "$9.99",
                        "localizedTitle" to "Renewable subscription",
                        "localizedDescription" to "This is a renewable subscription"
                    ))
                )
                iaphubStarted = true
                context = it
                Iaphub.start(context=it, appId="61718bfd9bf07f0c7d2357d1",  apiKey="Usaw9viZNrnYdNSwPIFFo7iUxyjK23K3", allowAnonymousPurchase=true)
                // Delete cache
                Iaphub.testing.clearCachedUser()
            }
            // Add listeners
            Iaphub.setOnErrorListener { err ->
                Log.d("IAPHUB", "-> Error: ${err.message}")
                this.errorCount++
            }
            Iaphub.setOnUserUpdateListener { ->
                Log.d("IAPHUB", "-> User update")
                this.userUpdateCount++
            }
            Iaphub.setOnReceiptListener() { err, receipt->
                Log.d("IAPHUB", "-> Process receipt")
                this.processReceiptCount++
            }
            Iaphub.setOnDeferredPurchaseListener { transaction ->
                Log.d("IAPHUB", "-> Process deferred purchase")
                this.deferredPurchases.add(transaction)
            }
            // Reset mock of requests
            Iaphub.testing.mockNetworkRequest(null)
        }
    }

    @Test
    fun test01_billingReadyTimeout() {
        val waiter = Waiter()
        var callbackCount = 0
        var requestCount = 0

        // Mock billing ready
        Iaphub.testing.storeReady = false
        // Mock network
        Iaphub.testing.mockNetworkRequest() { _, route, _ ->
            if (route.contains("/user") && !route.contains("pricing") && !route.contains("receipt")) {
                requestCount++
            }
            return@mockNetworkRequest null
        }
        // Callback function
        val callback = fun (err: IaphubError?, products: List<Product>?) {
            waiter.assertEquals(null, err)
            waiter.assertEquals(0, products?.size)
            callbackCount++
            if (callbackCount == 3) {
                waiter.assertEquals(1, requestCount)
                waiter.assertEquals(0, this.userUpdateCount)
                // Check products report
                val status = Iaphub.getBillingStatus()
                waiter.assertEquals("billing_unavailable", status.error?.code)
                waiter.assertEquals("billing_ready_timeout", status.error?.subcode)
                waiter.assertEquals(listOf("consumable"), status.filteredProductIds)
                // Check that the products details will be updated when the store is ready
                Iaphub.testing.storeReady = true
                Iaphub.getProductsForSale { err, products ->
                    waiter.assertEquals(1, requestCount)
                    waiter.assertEquals(1, this.userUpdateCount)
                    waiter.assertEquals(1, products?.size)
                    waiter.assertEquals("consumable", products?.get(0)?.sku)
                    waiter.assertEquals(null, err)
                    // Resume waiter
                    waiter.resume()
                }
            }
        }
        // Execute concurrent whenBillingReady
        Iaphub.getProductsForSale(callback)
        Iaphub.getProductsForSale(callback)
        Iaphub.getProductsForSale(callback)
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test02_billingReadyDelayed() {
        val waiter = Waiter()
        var callbackCount = 0

        // Mock billing ready
        Iaphub.testing.storeReady = false
        Iaphub.testing.storeReadyTimeout = 10000
        // Callback function
        val callback = fun (err: IaphubError?, products: List<Product>?) {
            waiter.assertNull(err)
            waiter.assertEquals(1, products?.size)
            callbackCount++
            if (callbackCount == 3) {
                waiter.resume()
            }
        }
        // Execute concurrent whenBillingReady
        Iaphub.getProductsForSale(callback)
        Iaphub.getProductsForSale(callback)
        Iaphub.getProductsForSale(callback)
        // Switch to ready after 2 seconds
        Timer().schedule(timerTask {
            Iaphub.testing.storeReady = true
            Iaphub.testing.notifyStoreReady()
        }, 2000)
        // Wait waiter
        waiter.await(4000)
    }

    @Test
    fun test03_getProductsForSale() {
        val waiter = Waiter()
        var userFetched = false

        Iaphub.testing.mockNetworkRequest() { _, route, _ ->
            if (route.contains("/user")) {
                userFetched = true
            }
            return@mockNetworkRequest null
        }
        Iaphub.getProductsForSale { err, products ->
            // Should return the products with no error
            waiter.assertNull(err)
            waiter.assertEquals(1, products?.size)
            waiter.assertEquals("consumable", products?.get(0)?.sku)
            waiter.assertEquals(1.99, products?.get(0)?.price?.toDouble())
            waiter.assertEquals("USD", products?.get(0)?.currency)
            waiter.assertEquals("$1.99", products?.get(0)?.localizedPrice)
            waiter.assertEquals("Consumable", products?.get(0)?.localizedTitle)
            waiter.assertEquals("This is a consumable", products?.get(0)?.localizedDescription)
            waiter.assertEquals(false, userFetched)
            // The user should have been cached
            waiter.assertNotNull(Iaphub.testing.getFromCache("iaphub_user_a_61718bfd9bf07f0c7d2357d1"))

            waiter.resume()
        }

        waiter.await(11000)
    }

    @Test
    fun test04_getUserId() {
        val waiter = Waiter()
        val userId = Iaphub.getUserId()

        waiter.assertEquals("a", userId?.split(":")?.get(0))
    }

    @Test
    fun test05_login() {
        val waiter = Waiter()
        var loginTriggered = false

        // Mock request posting receipt
        Iaphub.testing.mockNetworkRequest() { type, route, params ->
            if (route.contains("/login")) {
                loginTriggered = true
            }
            return@mockNetworkRequest null
        }
        // Login
        Iaphub.login("42") { err ->
            waiter.assertNull(err)
            waiter.assertEquals("42", Iaphub.getUserId())
            waiter.assertEquals(false, loginTriggered)
            waiter.resume()
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test06_logout() {
        Iaphub.logout()
    }

    @Test
    fun test07_buy() {
        val waiter = Waiter()
        // Mock request posting receipt
        Iaphub.testing.mockNetworkRequest() { type, route, _ ->
            if (route.contains("/receipt")) {
                val emptyList: List<Any> = emptyList()

                return@mockNetworkRequest mapOf(
                    "status" to "success",
                    "oldTransactions" to emptyList,
                    "newTransactions" to listOf(
                        mapOf(
                            "id" to "5e517bdd0613c16f11e7fae0",
                            "type" to "consumable",
                            "sku" to "consumable",
                            "purchase" to "2e517bdd0613c16f11e7faz2",
                            "purchaseDate" to "2020-05-22T01:34:40.462Z",
                            "webhookStatus" to "success"
                        )
                    )
                )
            }
            return@mockNetworkRequest null
        }
        // Buy product
        this.scenario.onActivity {
            Iaphub.buy(activity=it, sku="consumable") { err, transaction ->
                waiter.assertNull(err)
                waiter.assertEquals("consumable", transaction?.sku)
                waiter.assertEquals("$1.99", transaction?.localizedPrice)
                waiter.resume()
            }
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test08_loginWithServer() {
        val waiter = Waiter()
        var loginTriggered = false

        // Mock request posting receipt
        Iaphub.testing.mockNetworkRequest() { type, route, params ->
            if (route.contains("/login")) {
                loginTriggered = true
                waiter.assertEquals("42", params["userId"])
            }
            return@mockNetworkRequest null
        }
        // Login
        Iaphub.login("42") { err ->
            waiter.assertNull(err)
            waiter.assertEquals("42", Iaphub.getUserId())
            waiter.assertEquals(true, loginTriggered)

            Iaphub.logout()
            loginTriggered = false
            Iaphub.login("43") { err ->
                waiter.assertEquals(false, loginTriggered)
                waiter.resume()
            }
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test07_buy_user_conflict() {
        val waiter = Waiter()
        Iaphub.testing.forceUserRefresh()
        // Mock request posting receipt
        Iaphub.testing.mockNetworkRequest() { type, route, _ ->
            if (type == "GET" && route.contains("/user")) {
                val emptyList: List<Any> = emptyList()

                return@mockNetworkRequest mapOf(
                    "id" to "61781dff9bf07f0c7d32c8b6",
                    "productsForSale" to listOf(
                        mapOf(
                            "id" to "61781dff9bf07f0c7d32c9a7",
                            "sku" to "renewable_subscription",
                            "type" to "renewable_subscription",
                            "subscriptionPeriodType" to "normal"
                        )
                    ),
                    "activeProducts" to emptyList
                )
            }
            else if (route.contains("/receipt")) {
                val emptyList: List<Any> = emptyList()

                return@mockNetworkRequest mapOf(
                    "status" to "success",
                    "oldTransactions" to emptyList,
                    "newTransactions" to listOf(
                        mapOf(
                            "id" to "5e517bdd0613c16f11e7fae0",
                            "type" to "renewable_subscription",
                            "sku" to "renewable_subscription",
                            "user" to "61781dff9bf07f0c7d32c8b5",
                            "purchase" to "2e517bdd0613c16f11e7fbt1",
                            "purchaseDate" to "2021-05-22T01:34:40.462Z",
                            "expirationDate" to "2031-05-22T01:34:40.462Z",
                            "subscriptionState" to "active",
                            "webhookStatus" to "success"
                        )
                    )
                )
            }
            return@mockNetworkRequest null
        }
        // Buy product
        this.scenario.onActivity {
            Iaphub.buy(activity=it, sku="renewable_subscription") { err, transaction ->
                waiter.assertEquals("user_conflict", err?.code)
                waiter.assertEquals("renewable_subscription", transaction?.sku)
                waiter.resume()
            }
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test08_detectUserUpdate() {
        val waiter = Waiter()
        val self = this
        // Mock request fetching user
        Iaphub.testing.mockNetworkRequest() { type, route, _ ->
            if (type == "GET" && route.contains("/user")) {
                val emptyList: List<Any> = emptyList()

                return@mockNetworkRequest mapOf(
                    "productsForSale" to emptyList,
                    "activeProducts" to listOf(
                        mapOf(
                            "id" to "61781dff9bf07f0c7d32c9a7",
                            "sku" to "renewable_subscription",
                            "type" to "renewable_subscription",
                            "purchase" to "2e517bdd0613c16f11e7faz3",
                            "purchaseDate" to "2021-05-22T01:34:40.462Z",
                            "platform" to "android",
                            "isFamilyShare" to false,
                            "expirationDate" to "2023-05-22T01:34:40.462Z",
                            "subscriptionState" to "active",
                            "subscriptionPeriodType" to "normal",
                            "isSubscriptionRenewable" to true,
                            "isSubscriptionRetryPeriod" to false,
                            "isSubscriptionGracePeriod" to false,
                            "isSubscriptionPaused" to false
                        )
                    )
                )
            }
            return@mockNetworkRequest null
        }
        // Force refresh
        Iaphub.testing.forceUserRefresh()
        // Get active products
        Iaphub.getActiveProducts { err, products ->
            waiter.assertNull(err)
            waiter.assertEquals(1, products?.size)
            waiter.assertEquals("renewable_subscription", products?.get(0)?.sku)
            waiter.assertEquals(9.99, products?.get(0)?.price?.toDouble())
            waiter.assertEquals("USD", products?.get(0)?.currency)
            waiter.assertEquals("$9.99", products?.get(0)?.localizedPrice)
            waiter.assertEquals("Renewable subscription", products?.get(0)?.localizedTitle)
            waiter.assertEquals("This is a renewable subscription", products?.get(0)?.localizedDescription)
            // Wait for the user update to be triggered (because of dispatchToMain)
            Timer().schedule(timerTask {
                waiter.assertEquals(1, self.userUpdateCount)
                waiter.resume()
            }, 10)
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test09_restore() {
        val waiter = Waiter()
        // Mock request posting receipt
        Iaphub.testing.mockNetworkRequest() { _, route, params ->
            if (route.contains("/receipt")) {
                waiter.assertEquals("restore", params["context"])

                val emptyList: List<Any> = emptyList()
                return@mockNetworkRequest mapOf(
                    "status" to "success",
                    "oldTransactions" to emptyList,
                    "newTransactions" to listOf(
                        mapOf(
                            "id" to "5e517bdd0613c16f11e7fae0",
                            "type" to "renewable_subscription",
                            "sku" to "test",
                            "purchase" to "2e517bdd0613c16f11e7faz3",
                            "purchaseDate" to "2020-05-22T01:34:40.462Z",
                            "expirationDate" to "2023-05-22T01:34:40.462Z",
                            "subscriptionState" to "active",
                            "subscriptionPeriodType" to "normal",
                            "isSubscriptionRenewable" to true,
                            "isSubscriptionRetryPeriod" to false,
                            "isSubscriptionGracePeriod" to false,
                            "isSubscriptionPaused" to false,
                            "webhookStatus" to "success"
                        )
                    )
                )
            }
            return@mockNetworkRequest null
        }
        // Run restore
        Iaphub.restore { err, _ ->
            waiter.assertNull(err)
            waiter.assertEquals(1, this.processReceiptCount)
            waiter.assertEquals(1, this.errorCount)
            waiter.resume()
        }
        // A concurrent restore request should return an error
        Iaphub.restore { err, _ ->
            waiter.assertEquals("restore_processing", err?.code)
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test10_setTags() {
        val waiter = Waiter()
        // Set tags
        Iaphub.setUserTags(mapOf("group" to "1")) { err ->
            waiter.assertNull(err)
            waiter.assertEquals(0, this.errorCount)
            waiter.resume()
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test11_deleteTags() {
        val waiter = Waiter()
        // Set tags
        Iaphub.setUserTags(mapOf("group" to "")) { err ->
            waiter.assertNull(err)
            waiter.assertEquals(0, this.errorCount)
            waiter.resume()
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test12_setDeviceParams() {
        val waiter = Waiter()
        // Set tags
        Iaphub.setDeviceParams(mapOf("appVersion" to "2.0.0"))
        // The products for sale should return the product only available in 2.0.0
        Iaphub.getProductsForSale { err, products ->
            waiter.assertNull(err)
            waiter.assertEquals(0, this.errorCount)
            waiter.assertEquals(1, products?.size)
            waiter.assertEquals("renewable_subscription", products?.get(0)?.sku)
            waiter.assertEquals(0, this.errorCount)
            waiter.resume()
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test13_getActiveProducts() {
        val waiter = Waiter()
        // Mock request fetching user
        Iaphub.testing.mockNetworkRequest() { type, route, _ ->
            if (type == "GET" && route.contains("/user")) {
                val emptyList: List<Any> = emptyList()

                return@mockNetworkRequest mapOf(
                    "productsForSale" to emptyList,
                    "activeProducts" to listOf(
                        mapOf(
                            "id" to "61781dff9bf07f0c7d32c9a7",
                            "sku" to "renewable_subscription",
                            "type" to "renewable_subscription",
                            "purchase" to "2e517bdd0613c16f11e7faz3",
                            "purchaseDate" to "2021-05-22T01:34:40.462Z",
                            "platform" to "android",
                            "isFamilyShare" to false,
                            "isPromo" to false,
                            "originalPurchase" to "2e517bdd0613c16f11e7fab2",
                            "expirationDate" to "2023-05-22T01:34:40.462Z",
                            "subscriptionState" to "retry_period",
                            "subscriptionPeriodType" to "normal",
                            "isSubscriptionRenewable" to true,
                            "isSubscriptionRetryPeriod" to true,
                            "isSubscriptionGracePeriod" to false,
                            "isSubscriptionPaused" to false
                        ),
                        mapOf(
                            "id" to "61781dff9bf07f0c7d32c9a7",
                            "sku" to "unknown_subscription",
                            "type" to "renewable_subscription",
                            "purchase" to "2e517bdd0613c16f11e7faz4",
                            "purchaseDate" to "2021-06-22T01:34:40.462Z",
                            "platform" to "android",
                            "isFamilyShare" to false,
                            "isPromo" to false,
                            "originalPurchase" to "2e517bdd0613c16f21e5fab1",
                            "expirationDate" to "2023-06-22T01:34:40.462Z",
                            "subscriptionState" to "active",
                            "subscriptionPeriodType" to "grace_period",
                            "isSubscriptionRenewable" to true,
                            "isSubscriptionRetryPeriod" to false,
                            "isSubscriptionGracePeriod" to true,
                            "isSubscriptionPaused" to false
                        ),
                        mapOf(
                            "id" to "21781dff9bf02f0c6d32c5a8",
                            "type" to "renewable_subscription",
                            "purchase" to "6e517bdd0313c56f11e7faz9",
                            "purchaseDate" to "2021-04-22T01:34:40.462Z",
                            "platform" to "ios",
                            "isFamilyShare" to false,
                            "isPromo" to true,
                            "promoCode" to "SPRING",
                            "originalPurchase" to "6e517bdd0313c56f11e7faz9",
                            "expirationDate" to "2023-05-22T01:34:40.462Z",
                            "subscriptionState" to "active",
                            "subscriptionPeriodType" to "normal",
                            "isSubscriptionRenewable" to true,
                            "isSubscriptionPaused" to false
                        )
                    )
                )
            }
            return@mockNetworkRequest null
        }
        // Force refresh
        Iaphub.testing.forceUserRefresh()
        // Get active products
        Iaphub.getActiveProducts { err, products ->
            waiter.assertNull(err)
            waiter.assertEquals(2, products?.size)
            waiter.assertEquals("unknown_subscription", products?.get(0)?.sku)
            waiter.assertEquals("2e517bdd0613c16f21e5fab1", products?.get(0)?.originalPurchase)
            waiter.assertEquals("", products?.get(1)?.sku)
            waiter.assertEquals(true, products?.get(1)?.isPromo)
            waiter.assertEquals("SPRING", products?.get(1)?.promoCode)
            waiter.assertEquals(0, this.errorCount)
            // Get active products including all states
            Iaphub.getActiveProducts(includeSubscriptionStates=listOf("retry_period", "paused")) { err, products ->
                waiter.assertNull(err)
                waiter.assertEquals(3, products?.size)
                waiter.assertEquals("renewable_subscription", products?.get(0)?.sku)
                waiter.assertEquals("unknown_subscription", products?.get(1)?.sku)
                waiter.assertEquals("", products?.get(2)?.sku)
                waiter.assertEquals(0, this.errorCount)
                waiter.resume()
            }
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test14_concurrentFetch() {
        val waiter = Waiter()
        var callbackCount = 0
        var requestCount = 0
        // Force refresh
        Iaphub.testing.forceUserRefresh()
        // Mock request fetching user
        Iaphub.testing.mockNetworkRequest() { type, route, _ ->
            if (type == "GET" && route.contains("/user")) {
                requestCount++
            }
            return@mockNetworkRequest null
        }
        // Callback function
        val callback = fun (err: IaphubError?, products: List<Product>?) {
            if (err == null) {
                callbackCount++
            }
            if (callbackCount == 6) {
                waiter.assertEquals(1, requestCount)
                waiter.assertEquals(0, this.errorCount)
                waiter.resume()
            }
        }
        // Execute concurrent fetch
        Iaphub.getProductsForSale(completion=callback)
        Iaphub.getProductsForSale(completion=callback)
        Iaphub.getActiveProducts(completion=callback)
        Iaphub.getActiveProducts(completion=callback)
        Iaphub.getProductsForSale(completion=callback)
        Iaphub.getProductsForSale(completion=callback)
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test15_consumeDeferredPurchase() {
        val waiter = Waiter()
        var requestCount = 0

        // Mock request fetching user
        Iaphub.testing.mockNetworkRequest() { type, route, params ->
            if (type == "GET" && route.contains("/user")) {
                val emptyList: List<Any> = emptyList()
                requestCount++
                waiter.assertEquals(null, params["deferredPurchase"] as? String)
                return@mockNetworkRequest mapOf(
                    "productsForSale" to emptyList,
                    "activeProducts" to emptyList
                )
            }
            return@mockNetworkRequest null
        }
        // Force refresh
        Iaphub.testing.forceUserRefresh()
        // Get active products
        Iaphub.getActiveProducts { _, _ ->
            waiter.assertEquals(1, requestCount)
            // Disable enableDeferredPurchaseListener option
            Iaphub.start(context=context as Context, appId="61718bfd9bf07f0c7d2357d1",  apiKey="Usaw9viZNrnYdNSwPIFFo7iUxyjK23K3", enableDeferredPurchaseListener=false)
            // Mock request fetching user
            Iaphub.testing.mockNetworkRequest() { type, route, params ->
                if (type == "GET" && route.contains("/user")) {
                    val emptyList: List<Any> = emptyList()
                    requestCount++
                    waiter.assertEquals("false", params["deferredPurchase"] as? String)
                    return@mockNetworkRequest mapOf(
                        "productsForSale" to emptyList,
                        "activeProducts" to emptyList
                    )
                }
                return@mockNetworkRequest null
            }
            // Refresh user
            Iaphub.testing.forceUserRefresh()
            Iaphub.getActiveProducts { _, _ ->
                waiter.assertEquals(2, requestCount)
                waiter.resume()
            }
        }
        // Wait waiter
        waiter.await(5000)
    }

    @Test
    fun test16_getDeferredPurchaseEvent() {
        var self = this
        val waiter = Waiter()
        var requestCount = 0

        // Mock request fetching user
        Iaphub.testing.mockNetworkRequest() { type, route, params ->
            if (type == "GET" && route.contains("/user")) {
                val emptyList: List<Any> = emptyList()
                requestCount++
                return@mockNetworkRequest mapOf(
                    "productsForSale" to emptyList,
                    "activeProducts" to emptyList,
                    "events" to listOf(
                        mapOf(
                            "type" to "purchase",
                            "tags" to listOf("deferred"),
                            "transaction" to mapOf(
                                "id" to "21781dff9bf02f0c6d32c5a7",
                                "type" to "consumable",
                                "purchase" to "6e517bdd0313c56f11e7faz8",
                                "purchaseDate" to "2022-06-22T01:34:40.462Z",
                                "platform" to "android",
                                "isFamilyShare" to false,
                                "user" to "21781dff9bf02f0c6d32c4b2",
                                "webhookStatus" to "success"
                            )
                        )
                    )
                )
            }
            return@mockNetworkRequest null
        }
        // Force refresh
        Iaphub.testing.forceUserRefresh()
        // Get active products
        Iaphub.getActiveProducts { _, _ ->
            waiter.assertEquals(1, requestCount)
            waiter.assertEquals(1, self.deferredPurchases.size)
            waiter.assertEquals("21781dff9bf02f0c6d32c5a7", self.deferredPurchases[0].id)
            waiter.resume()
        }
        // Wait waiter
        waiter.await(5000)
    }

}