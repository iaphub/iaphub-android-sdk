package com.iaphub.example

import android.app.Application
import android.util.Log
import com.iaphub.Iaphub
import com.iaphub.ProductDetails

class MainApplication: Application()  {

    override fun onCreate() {
        super.onCreate()
        // Start IAPHUB
        Iaphub.start(
            context=this,
            appId="5e4890f6c61fc971cf46db4d",
            apiKey="SDp7aY220RtzZrsvRpp4BGFm6qZqNkNf",
            allowAnonymousPurchase=true
        )
        /*
         * Mock products (to make the example work without Google Play), do not use this in your own app
         * In order to make this example work using Google Play:
         * - Comment/remove the code bellow
         * - Create an app on IAPHUB and replace the `appId` and `apiKey` properties with the one of your app (available in the settings of your app)
         * - Configure the Google Play API of your app (https://www.iaphub.com/docs/set-up-android/configure-google-play-api)
         * - Create at least one product on the Google Play Console and IAPHUB (products page)
         * - Return the product in your listing (https://www.iaphub.com/docs/getting-started/create-listing)
         */
        /*
        Iaphub.testing.storeLibraryMock=true
        Iaphub.testing.storeReady=true
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
        // Mock request posting receipt
        Iaphub.testing.mockNetworkRequest() { type, route, _ ->
            if (type == "GET" && route.contains("/user")) {
                val emptyList: List<Any> = emptyList()

                return@mockNetworkRequest mapOf(
                    "id" to "61781dff9bf07f0c7d32c8b6",
                    "productsForSale" to listOf(
                        mapOf(
                            "id" to "61781dff9bf07f0c7d32c9a7",
                            "sku" to "consumable",
                            "type" to "consumable"
                        )
                    ),
                    "activeProducts" to emptyList
                )
            }
            else if (type == "POST" && route.contains("/receipt")) {
                val emptyList: List<Any> = emptyList()

                return@mockNetworkRequest mapOf(
                    "status" to "success",
                    "oldTransactions" to emptyList,
                    "newTransactions" to listOf(
                        mapOf(
                            "id" to "5e517bdd0613c16f11e7fae0",
                            "type" to "consumable",
                            "sku" to "consumable",
                            "user" to "61781dff9bf07f0c7d32c8b6",
                            "purchase" to "2e517bdd0613c16f11e7faz2",
                            "purchaseDate" to "2020-05-22T01:34:40.462Z",
                            "webhookStatus" to "success"
                        )
                    )
                )
            }
            else if (type == "POST" && route.contains("/pricing")) {
                return@mockNetworkRequest mapOf()
            }
            else if (type == "POST" && route.contains("/login")) {
                return@mockNetworkRequest mapOf()
            }
            return@mockNetworkRequest null
        }
        */
    }
}