## Migrate iaphub-android-sdk 2.X.X to 3.X.X

The version 3 of iaphub-android-sdk implement the Google Play Billing Library 5.0!<br/><br/>
It is an important update, especially now that Google has completely updated the Google Play Console with the possibility to create multiple offers and intro phases.

You must update the SDK to v3 in order to detect the multiple intro phases correctly so we recommend to update the library to v3 as soon as you can.

To update the library, update your `build.gradle` file
```js
implementation 'com.iaphub:iaphub-android-sdk:3.0.+'
```

The only major change is that the following properties are removed:
- subscriptionIntroPrice
- subscriptionIntroLocalizedPrice
- subscriptionIntroPayment
- subscriptionIntroDuration
- subscriptionIntroCycles
- subscriptionTrialDuration

Instead you'll have a property `subscriptionIntroPhases` that is an ordered list containing the intro phases the user is eligible to.<br/><br/>
So for instance, if you create 2 intro phases: a free trial of 1 month followed by an introductory price of $4.99 for 3 months, the `subscriptionIntroPhases` property will contain the following:

```js
[
  {
    type: "trial",
    price: 0,
    currency: "USD",
    localizedPrice: "FREE",
    cycleDuration: "P1M",
    cycleCount: 1,
    payment: "upfront"
  },
  {
    type: "intro",
    price: 4.99,
    currency: "USD",
    localizedPrice: "$4.99",
    cycleDuration: "P1M",
    cycleCount: 3,
    payment: "as_you_go"
  }
]
```

If you have multiple Android offers, the oldest (first one you've created) will be used by default.<br/>
We do not support the Android multiple offers system in order to have a common system with iOS. To have a different offer simply create a new product, you can do pretty much everything with [smart listings](https://www.iaphub.com/docs/resources/smart-listing).

### Need help?

If you have any questions you can of course contact us at `support@iaphub.com`.