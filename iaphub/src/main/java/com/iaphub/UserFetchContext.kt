package com.iaphub

enum class UserFetchContextSource(val value: String) {
   PRODUCTS("products"),      // When fetching the products
   RECEIPT("receipt"),        // When posting a receipt
   BUY("buy"),               // When a buy occurs
   RESTORE("restore")         // When a restore occurs
}

enum class UserFetchContextProperty(val value: String) {
   WITH_ACTIVE_SUBSCRIPTION("was"),          // The user has an active subscription
   WITH_EXPIRED_SUBSCRIPTION("wes"),         // The user has an expired subscription
   WITH_ACTIVE_NON_CONSUMABLE("wanc"),       // The user has an active non consumable
   LAST_FETCH_UNDER_TEN_SECONDS("lfu10s"),   // Last fetch occurred under ten seconds
   LAST_FETCH_UNDER_ONE_MINUTE("lfu1m"),     // Last fetch occurred under one minute
   LAST_FETCH_UNDER_ONE_HOUR("lfu1h"),       // Last fetch occurred under one hour
   LAST_FETCH_UNDER_ONE_DAY("lfu1d"),        // Last fetch occurred under one day
   ON_FOREGROUND("ofg"),                     // Occurred when the app went to foreground
   INITIALIZATION("init")                    // Occurred on the user's first fetch call
}

class UserFetchContext(
   val source: UserFetchContextSource,
   val properties: MutableList<UserFetchContextProperty> = mutableListOf()
) {
   fun getValue(): String {
      return (listOf(source.value) + properties.map { it.value }).joinToString("/")
   }
}