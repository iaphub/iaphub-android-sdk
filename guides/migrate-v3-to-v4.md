## Migrate iaphub-android-sdk 3.X.X to 4.X.X

We're happy to release the version 4 of the SDK.

To update the library, update your `build.gradle` file
```js
implementation 'com.iaphub:iaphub-android-sdk:4.0.+'
```

### What's new?

#### A new `onDeferredPurchase` event

Thanks to this event you'll now be able to easily detect when a new purchase occurs outside of the `buy` method.<br/>
For instance this event can be trigerred:
- After a purchase is made outside the app with a promo code
- After a deferred payment (when the error code 'deferred_payment' is returned by the buy method)
- After a payment fails because it couldn't be validated by IAPHUB (and succeeds later)

```kotlin
  Iaphub.setOnDeferredPurchaseListener { transaction ->
    
  }
```

#### The enhancement of the `restore` method

The `restore` method will now return a `RestoreResponse` object.<br/>
This object will contain two properties:
- `newPurchases`: The new purchases processed during the restore
- `transferredActiveProducts`: The active products transferred (from another user) during the restore

```kotlin
Iaphub.restore { err: IaphubError?, response: RestoreResponse? ->
  
}
```

#### The enhancement of the `getProducts` method

The `getProducts` method will now return a `Products` object.<br/>
This object will contain two properties:
- `productsForSale`: The products for sale of the user
- `activeProducts`: The active products of the user

```kotlin
  Iaphub.getProducts() { err: IaphubError?, products: Products? ->
    
  }
```

#### The enhancement of the `ActiveProduct` object

The object has new properties:
- `isPromo`
- `promoCode`
- `originalPurchase`

### Need help?

If you have any questions you can of course contact us at `support@iaphub.com`.