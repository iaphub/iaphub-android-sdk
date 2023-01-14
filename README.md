<a href="https://www.iaphub.com" title="IAPHUB">
  <img width=882px src="https://www.iaphub.com/img/github/github-android-ad.png" alt="IAPHUB">
</a>
<br/>
<br/>
Implementing and developping all the tools to manage your In-App purchases properly can be very complex and time consuming.
You should spend this precious time building your app!
<br/>
<br/>

[IAPHUB](https://www.iaphub.com) has all the features you need to increase your sales 🚀

|   | Features |
| --- | --- |
📜 | Receipt validation - Send the receipt, we'll take care of the rest.
📨 | Webhooks - Receive webhooks directly to your server to be notified of any event such as a purchase or a subscription cancellation.    
📊 | Realtime Analytics - Out of the box insights of on all your sales, subscriptions, customers and everything you need to improve your revenues.
🧪 | A/B Testing - Test different pricings and get analytics of which one performs the best.
🌎 | Product Segmentation - Offer different product or pricings to your customers depending on defined criterias such as the country.
👤 | Customer Management - Access easily the details of a customer, everything you need to know such as the past transactions and the active subscriptions on one page.

## Installation

Implementing In-app purchases in your app should be a piece of cake!<br/>

1. Create an account on [IAPHUB](https://www.iaphub.com)

2. Add the library to your `build.gradle` file
```
implementation 'com.iaphub:iaphub-android-sdk:4.0.+'
```

3. Read our complete guide to [set up your app](https://www.iaphub.com/docs/getting-started/set-up-app/).

<br>

⚠ If you're migrating from v2.X.X to v3.X.X, read [this](https://github.com/iaphub/iaphub-android-sdk/tree/master/guides/migrate-v2-to-v3.md).<br/>
⚠ If you're migrating from v3.X.X to v4.X.X, read [this](https://github.com/iaphub/iaphub-android-sdk/tree/master/guides/migrate-v3-to-v4.md).

## Start
Import `Iaphub` and start the library in the `onCreate` method of your application<br/>

```kotlin
package com.iaphub.example

import android.app.Application
import com.iaphub.Iaphub

class MainApplication: Application()  {

    override fun onCreate() {
        super.onCreate()
        // Start IAPHUB
        Iaphub.start(
          // You must provide the app context
          context=this,

          // Required, the app id is available on the settings page of your app
          appId="5e4890f6c61fc971cf46db4d",
          
          // Required, the (client) api key is available on the settings page of your app
          apiKey="SDp7aY220RtzZrsvRpp4BGFm6qZqNkNf",
          
          // Optional, the user id, if you do not specify one an anonymous id will be generated (id prefixed with 'a:')
          // You can provide it if the user is already logged in on app start
          userId="42",

          // Optional, ff you want to allow purchases when the user has an anonymous user id
          // If you're listenning to IAPHUB webhooks your implementation must support users with anonymous user ids
          // This option is disabled by default, when disabled the buy method will return an error when the user isn't logged in
          allowAnonymousPurchase=true
        )
    }
}
```

## Events
IAPHUB is exposing different events<br/>
They are all optional but `setOnUserUpdateListener` is highly recommended in order to know when to refresh the state of your products.

#### User update
```kotlin
  Iaphub.setOnUserUpdateListener { ->
    // Called when the user has already been fetch and is updated
    // It means the products for sale or active products are different from the one you previously loaded using getProductsForSale/getActiveProducts
    // You should refresh your view with the new state of your products

    // When using the login/logout method, the user is reset, meaning this event won't be called until the user has been loaded first using the getProductsForSale/getActiveProducts methods
  }
```

#### Deferred purchase
```kotlin
  Iaphub.setOnDeferredPurchaseListener { transaction ->
    // Called when a deferred purchase has been processed
    // A deferred purchase is a purchase processed 'outside' of the buy method 
  }
```

#### Error
```kotlin
  Iaphub.setOnErrorListener { err ->
    // Called when IAPHUB has detected an error
    // It can be interesting to log unexpected errors
  }
```

#### Receipt
```kotlin
  Iaphub.setOnReceiptListener { err, receipt ->
    // Called after a receipt has been processed
  }
```

## Login
Call the `login` method to authenticate an user.<br/>

⚠ You should provide an id that is non-guessable and isn't public. (Email not allowed)<br/>

⚠ The user will be reset, `setOnUserUpdateListener` will only be called until after the user has been loaded first (using getProductsForSale/getActiveProducts).<br/>

```kotlin
Iaphub.login("3e4890f6c72fc971cf46db5d") { err: IaphubError? ->
  // On a success the err should be null
}
```

## Get user ID
Call the `getUserId` method to get the user id of the logged user.<br/>
If no user is logged the anonymous user id will be returned (prefixed with 'a:').

```kotlin
val userId = Iaphub.getUserId()
```

## Logout
Call the `logout` method to log the user out.<br/>
The user will switch back to his anonymous user id (prefixed with 'a:').<br/>

⚠ The user will be reset, `setOnUserUpdateListener` will only be called until after the user has been loaded first (using getProductsForSale/getActiveProducts).<br/>

```kotlin
Iaphub.logout()
```

## Set user tags
Call the `setUserTags` method to update the user tags.<br/>
User tags will appear on the user page of the IAPHUB dashboard.<br/>
When using IAPHUB's [smart listings](https://www.iaphub.com/docs/resources/smart-listing), you'll be able to return different products depending on the user tags.<br/>

⚠ This method will throw an error if the tag name hasn't been created on the IAPHUB dashboard

```kotlin
// To set a tag
Iaphub.setUserTags(mapOf("gender" to "male")) { err: IaphubError? ->
  // On a success the err should be null
}

// To remove a tag pass a empty string
Iaphub.setUserTags(mapOf("gender" to "")) { err: IaphubError? ->
  // On a success the err should be null
}
```

A few details:
  - A tag must be created on the IAPHUB dashboard (otherwise the method will throw an error)
  - When creating a tag on the IAPHUB dashboard you must check the option to allow editing the tag from the client (otherwise you'll only be able to edit the tag using the [IAPHUB API](https://www.iaphub.com/docs/api/post-user) from your server)
  - A tag key is limited to 32 characters
  - A tag value is limited to 64 characters

## Set device params
Call the `setDeviceParams` method to set parameters for the device<br/>
When using IAPHUB's [smart listings](https://www.iaphub.com/docs/resources/smart-listing), you'll be able to return different products depending on the device params.

```kotlin
// For instance you can provide the app version on app launch
// Useful to return a product only supported in a new version
Iaphub.setDeviceParams(mapOf("appVersion" to "2.0.0"))
// To clear the device params
Iaphub.setDeviceParams(mapOf())
```

A few details:
  - The params are not saved on the device, they won't persist if the app is restarted
  - The params are not saved on IAPHUB, they are just provided to the API when fetching the products for sale
  - A param key limited to 32 characters and must be a valid key (``^[a-zA-Z_]*$``)
  - A param value limited to 32 characters
  - You can provide up to 5 params

## Get products for sale
Call the ``getProductsForSale`` method to get the user's products for sale<br/>
You should use this method when displaying the page with the list of your products for sale.

⚠ If the request fails because of a network issue, the method returns the latest request in cache (if available, otherwise an error is thrown).

⚠ If a product is returned by the [API](https://www.iaphub.com/docs/api/get-user/) but the sku cannot be loaded, it'll be filtered from the list and an 'unexpected' error will be returned in the `setOnErrorListener` listener.

⚠ If you have multiple Android offers, the oldest (first one you've created) will be used by default. We do not support the Android multiple offers system in order to have a common system with iOS. To have a different offer simply create a new product, you can do pretty much everything with [smart listings](https://www.iaphub.com/docs/resources/smart-listing).

```kotlin
Iaphub.getProductsForSale { err: IaphubError?, products: List<Product>? ->
  // On a success err should be null
}
```

## Get active products
If you're relying on IAPHUB on the client side (instead of using your server with webhooks) to detect if the user has active products (auto-renewable subscriptions, non-renewing subscriptions or non-consumables), you should use the `getActiveProducts` method.<br/>

⚠ If the request fails because of a network issue, the method returns the latest request in cache (if available with no expired subscription, otherwise an error is thrown).

⚠ If an active product is returned by the API but the sku cannot be loaded, the product will be returned but only with the properties coming from the [API](https://www.iaphub.com/docs/api/get-user/) (The price, title, description.... properties won't be returned).

#### Subscription state

Value | Description |
| :------------ |:---------------
| active | The subscription is active
| grace_period | The subscription is in the grace period, the user should still access the features offered by your subscription
| retry_period | The subscription is in the retry period, you must restrict the access to the features offered by your subscription and display a message asking for the user to update its payment informations.
| paused | The subscription is paused and will automatically resume at a later date (`autoResumeDate` property), you must restrict the access to the features offered by your subscription.

By default only subscriptions with an `active` or `grace_period` state are returned by the `getActiveProducts` method because you must restrict the access to the features offered by your subscription on a `retry_period` or `paused` state.<br/>
<br/>
If you're looking to display a message when a user has a subscription on a `retry_period` or `paused` state, you can use the `includeSubscriptionStates` option.
```swift
  Iaphub.getActiveProducts(includeSubscriptionStates=listOf("retry_period", "paused")) { err: IaphubError?, products: List<ActiveProduct>? ->
    // On a success err should be null
  }
```

## Get all products
A method `getProducts` is also available in order to get the products for sale and active products in one call.
```kotlin
  Iaphub.getProducts() { err: IaphubError?, products: Products? ->
    // On a success err should be null
  }
```

## Get billing status
The `getBillingStatus` method will return useful informations if you have an issue with the products returned by the `getProducts` or `getProductsForSale` methods (for instance if no products for sale were returned).
```kotlin
  val status = Iaphub.getBillingStatus()

  // You should display an appropriate message if the billing is unavailable
  if (status.error?.code == "billing_unavailable") {
    if (status.error?.subcode == "play_store_outdated") {
      // Display a message saying that the Play Store app on the user's device is out of date, it must be updated
    }
    else {
      // Display a message saying that the in-app billing isn't available on the device
    }
  }
  // Check the products that were filtered from the products for sale
  if (status.filteredProductIds.isNotEmpty()) {
    // The product ids in the array were not returned by Google Play
  }
```

## Buy a product
Call the ``buy`` method to buy a product<br/><br/>
ℹ️ The method needs the product sku that you would get from one of the products of `getProductsForSale`.<br/>

```kotlin
  Iaphub.buy(activity=requireActivity(), sku=product.sku) { err: IaphubError?, transaction: ReceiptTransaction? ->
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
       * IAPHUB will automatically retry to process the receipt if possible (depends on the error)
       */
      else if (err.code == "receipt_failed") {
        message = "We're having trouble validating your transaction, give us some time we'll retry to validate your transaction ASAP!"
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
```

## Restore user purchases
Call the ``restore`` method to restore the user purchases<br/><br/>
ℹ️ You must display a button somewhere in your app in order to allow the user to restore its purchases.<br/>

```kotlin
Iaphub.restore { err: IaphubError?, response: RestoreResponse? ->
  // On a success err should be null
}
```

## Show manage subscriptions
Call the ``showManageSubscriptions`` to display the Google Play manage subscriptions page.<br/><br/>
ℹ️ Optional: You can specify the sku of an active subscription and you'll be redirected to the specified susbcription.<br/>

```swift
Iaphub.showManageSubscriptions({ (err: IHError?) in
  if (err != nil) {
    self.openAlert("Couldn't redirect to the app store, please check your subscriptions directly from the App Store App")
  }
})
```

## Properties

### Product
| Prop  | Type | Description |
| :------------ |:---------------:| :-----|
| id | `String` | Product id (From IAPHUB) |
| type | `String` | Product type (Possible values: 'consumable', 'non_consumable', 'subscription', 'renewable_subscription') |
| sku | `String` | Product sku (Ex: "membership_tier1") |
| price | `BigDecimal?` | Price amount (Ex: 12.99) |
| currency | `String?` | Price currency code (Ex: "USD") |
| localizedPrice | `String?` | Localized price (Ex: "$12.99") |
| localizedTitle | `String?` | Product title (Ex: "Membership") |
| localizedDescription | `String?` | Product description (Ex: "Join the community with a membership") |
| group | `String?` | ⚠ Only available if the product as a group<br>Group id (From IAPHUB) |
| groupName | `String?` | ⚠ Only available if the product as a group<br>Name of the product group created on IAPHUB (Ex: "premium") |
| subscriptionDuration | `String?` | ⚠ Only available for a subscription<br> Duration of the subscription cycle specified in the ISO 8601 format (Possible values: 'P1W', 'P1M', 'P3M', 'P6M', 'P1Y') |
| subscriptionIntroPhases | `[SubscriptionIntroPhase]?` | ⚠ Only available for a subscription<br> Ordered list of the subscription intro phases (intro price, free trial) |

### SubscriptionIntroPhase
| Prop  | Type | Description |
| :------------ |:---------------:| :-----|
| type | `String` | Introductory type (Possible values: 'trial', 'intro')  |
| price | `Double` | Introductory price amount (Ex: 2.99) |
| currency | `String` | Introductory price currency code (Ex: "USD") |
| localizedPrice | `String` | Localized introductory price (Ex: "$2.99") |
| cycleCount | `String` | Number of cycles in the introductory offer |
| cycleDuration | `String` | Duration of a introductory cycle specified in the ISO 8601 format (Possible values: 'P1W', 'P1M', 'P3M', 'P6M', 'P1Y') |

### ActiveProduct (inherit from Product)
| Prop  | Type | Description |
| :------------ |:---------------:| :-----|
| purchase | `String?` | Purchase id (From IAPHUB) |
| purchaseDate | `Date?` | Purchase date |
| platform | `String?` | Platform of the purchase (Possible values: 'ios', 'android') |
| expirationDate | `Date?` | Subscription expiration date |
| isSubscriptionRenewable | `Boolean = false` | True if the auto-renewal is enabled |
| isFamilyShare | `Boolean = false` | True if the subscription is shared by a family member (iOS subscriptions only) |
| isPromo | `Boolean = false` | True if purchased using a promo code |
| promoCode | `String?` | Promo code (Android: only available for subscriptions vanity codes, not available for one time codes) (iOS: the value is the offer reference name) |
| originalPurchase | `String?` | Original purchase id of a renewable subscription (From IAPHUB) |
| subscriptionRenewalProduct | `String?` | Subscription product id of the next renewal (only defined if different than the current product) |
| subscriptionRenewalProductSku | `String?` | Subscription product sku of the next renewal |
| subscriptionState | `String?` | State of the subscription<br>(Possible values: 'active', 'grace_period', 'retry_period', 'paused') |
| subscriptionPeriodType | `String?` | Current phase type of the subscription<br>(Possible values: 'normal', 'trial', 'intro') |
| androidToken | `String?` | Android purchase token of the transaction |

### ReceiptTransaction (inherit from ActiveProduct)
| Prop  | Type | Description |
| :------------ |:---------------:| :-----|
| webhookStatus | `String?` | Webhook status (Possible values: 'success', 'failed', 'disabled') |
| user | `String?` | User id (From IAPHUB) |

### Products
| Prop  | Type | Description |
| :------------ |:---------------:| :-----|
| productsForSale | `[Product]` | Products for sale |
| activeProducts | `[ActiveProduct]` | Active products |

### RestoreResponse
| Prop  | Type | Description |
| :------------ |:---------------:| :-----|
| newPurchases | `[ReceiptTransaction]` | New purchases processed during the restore |
| transferredActiveProducts | `[ActiveProduct]` | Active products transferred (from another user) during the restore |

### IaphubError
| Prop  | Type | Description |
| :------------ |:---------------:| :-----|
| message | `String` | Error message |
| code | `String` | Error code |
| params | `Map<String, Any>` | Error params |

## Full example

You should check out the [example app](https://github.com/iaphub/iaphub-android-sdk/tree/master/examples).
<br/>
