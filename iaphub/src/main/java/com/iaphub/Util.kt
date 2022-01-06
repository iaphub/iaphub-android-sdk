package com.iaphub

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import java.lang.Exception
import java.text.ParsePosition
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.timerTask

internal object Util {

  private var mainDispatchDisabled = false

  /*
   * Get value from cache
   */
  fun getFromCache(context: Context?, key: String): String? {
    if (context != null) {
      val pref = context.getSharedPreferences("iaphub", Context.MODE_PRIVATE)

      return pref.getString(key, null)
    }
    return null
  }

  /*
   * Save value to cache
   */
  fun setToCache(context: Context?, key: String, value: String?) {
    if (context != null) {
      val pref = context.getSharedPreferences("iaphub", Context.MODE_PRIVATE)
      val editor = pref.edit()

      editor.putString(key, value).commit()
    }
  }

  /*
   * Convert ISO string to date
   */
  fun dateFromIsoString(str: Any?, allowNull: Boolean = false, failure: ((Exception) -> Unit)? = null): Date? {
    if (str == null) {
      if (!allowNull && failure != null) {
        failure(Exception("date cast to string failed"))
      }
      return null
    }

    var strDate = str as? String
    var date: Date? = null

    if (strDate == null) {
      if (!allowNull && failure != null) {
        failure(Exception("date cast to string failed"))
      }
      return null
    }

    try {
      date = ISO8601Utils.parse(strDate, ParsePosition(0))
    }
    catch (exception: Exception) {
      if (failure != null) {
        failure(exception)
      }
    }

    return date
  }

  /*
   * Convert date to ISO string
   */
  fun dateToIsoString(date: Date?): String? {
    if (date == null) return null

    return ISO8601Utils.format(date)
  }

  /*
   * Convert map to json string
   */
  fun mapToJsonString(jsonMap: Map<String, Any>): String {
    return Gson().toJson(jsonMap)
  }

  /*
   * Convert json string to map
   */
  fun jsonStringToMap(jsonString: String): Map<String, Any>? {
    var jsonMap: Map<String, Any>? = HashMap()

    // Parse json
    try {
      if (jsonMap != null) {
        jsonMap = Gson().fromJson(jsonString, jsonMap.javaClass)
      }
    }
    catch (err: Exception) {
      jsonMap = null
    }

    return jsonMap
  }

  /*
   * Parse items
   */
  inline fun <reified T: Parsable>parseItems(data: Any?, allowNull: Boolean = false, failure: (Exception, Map<String, Any>?) -> Unit): List<T> {
    val arr: List<Map<String, Any>>? = (data as? List<Map<String, Any>>)
    val items: MutableList<T> = mutableListOf()

    if (arr == null) {
      if (!allowNull) {
        failure(Exception("cast to array failed"), null)
      }
      return items
    }

    arr.forEach { elem ->
      try {
        val item = T::class.constructors.first { it.parameters.isEmpty() == false }.call(elem)
        items.add(item)
      }
      catch (err: Exception) {
        failure(err, elem)
      }
    }

    return items
  }

  /*
   * Applies the function iterator to each item in arr in series.
   */
  fun <ListType, ErrorType>eachSeries(
    list: List<ListType>,
    iterator: (item: ListType, callback: (error: ErrorType?) -> Unit) -> Unit,
    finished: (error: ErrorType?) -> Unit
  ) {
    val list: MutableList<ListType> = list.toMutableList()
    var isFinishedCalled = false

    val finishedOnce = { error: ErrorType? ->
      if (!isFinishedCalled) {
        isFinishedCalled = true
        finished(error)
      }
    }

    var next: (() -> Unit)? = null
    next = { ->
      if (list.isNotEmpty()) {
        val item = list.removeAt(0)

        iterator(item) { error ->
          if (error != null) {
            finishedOnce(error)
          }
          else {
            next?.let { it() }
          }
        }
      }
      else {
        finishedOnce(null)
      }
    }

    next()
  }

  /**
   * Dispatch to main thread
   */
  fun disableMainDispatch(value: Boolean) {
    this.mainDispatchDisabled = value
  }

  /**
   * Dispatch to main thread
   */
  private var dispatchHandler: Handler? = null
  fun dispatchToMain(action: () -> Unit) {
    var handler = this.dispatchHandler

    if (this.mainDispatchDisabled) {
      action()
    }
    else {
      if (handler == null) {
        this.dispatchHandler = Handler(Looper.getMainLooper())
        handler = this.dispatchHandler
      }
      if (Thread.currentThread() != Looper.getMainLooper().thread) {
        handler?.post(action)
      } else {
        action()
      }
    }
  }

  /**
   * Dispatch out of main thread
   */
  fun dispatch(action: () -> Unit) {
    if (Thread.currentThread() == Looper.getMainLooper().thread) {
      thread { action() }
    } else {
      action()
    }
  }

  /**
   * Retry task multiple times until it succeed
   */
  fun <DataType>retry(times: Int, delay: Long, task: ((Boolean, IaphubError?, DataType?) -> Unit) -> Unit, completion: (IaphubError?, DataType?) -> Unit) {
    task { shouldRetry, error, data ->
      // If there is no error it is a success
      if (error == null) {
        completion(null, data)
      }
      // If time left retry
      else if (times > 0 && shouldRetry) {
        Timer().schedule(timerTask {
          retry<DataType>(times - 1, delay, task, completion)
        }, delay * 1000)
      }
      // Otherwise it failed
      else {
        completion(error, data)
      }
    }
  }

}