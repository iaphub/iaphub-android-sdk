package com.iaphub

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.HttpUrl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.TimeUnit;

internal class NetworkResponse(
    var data: Map<String, Any>? = null,
    var httpResponse: Response? = null
) {
    fun getHeader(name: String): String? {
      return httpResponse?.header(name)
    }

    fun hasSuccessStatusCode(): Boolean {
      return (httpResponse?.code ?: 0) == 200
    }

    fun hasNotModifiedStatusCode(): Boolean {
      return (httpResponse?.code ?: 0) == 304
    }

    fun hasTooManyRequestsStatusCode(): Boolean {
      return (httpResponse?.code ?: 0) == 429
    }

    fun hasServerErrorStatusCode(): Boolean {
      val statusCode = httpResponse?.code ?: 0

      return statusCode >= 500 && statusCode < 600
    }

}

internal class Network {

  internal var endpoint: String
  internal var headers: HashMap<String, String>
  internal var params: HashMap<String, String>
  internal var mock: ((String, String, Map<String, Any>) -> Map<String, Any>?)? = null

  constructor(endpoint: String) {
    this.endpoint = endpoint
    this.headers = hashMapOf(
      "Accept" to "application/json",
      "Content-Type" to "application/json"
    )
    this.params = hashMapOf()
  }

  /**
   * Set the headers of the requests
   */
  fun setHeaders(headers: Map<String, String>) {
    this.headers.putAll(headers)
  }
   
  /**
   * Set params of the requests
   */
  fun setParams(params: Map<String, String>) {
    this.params.putAll(params)
  }

  /**
   * Mock request
   */
  fun mockRequest(mock: ((String, String, Map<String, Any>) -> Map<String, Any>?)?) {
    this.mock = mock
  }

  /**
   * Send a request
   */
  fun send(type: String, route: String, params: Map<String, Any> = emptyMap(), headers: Map<String, String> = emptyMap(), connectTimeout: Long = 4, timeout: Long = 6, retry: Int = 2, silentLog: Boolean = false, completion: (IaphubError?, NetworkResponse?) -> Unit) {
    // Retry request up to 2 times with a delay of 1 second
    Util.retry<NetworkResponse>(
      times=retry,
      delay=1,
      task={ callback ->
        this.sendRequest(type=type, route=route, params=params, headers=headers, connectTimeout=connectTimeout, timeout=timeout) { err, networkResponse ->
          // Retry request if we have no response
          if (networkResponse == null) {
            callback(true, err, null)
          }
          // Retry request if we have a 5XX status code
          else if (networkResponse.hasServerErrorStatusCode()) {
            callback(true, err, networkResponse)
          }
          // Otherwise do not retry
          else {
            callback(false, err, networkResponse)
          }
        }
      },
      completion={ err, networkResponse ->
        // Send error if there is one
        if (err != null && !silentLog && networkResponse?.hasTooManyRequestsStatusCode() != true) {
          err.send()
        }
        // Call completion
        completion(err, networkResponse)
      }
    )
  }

  /***************************** PRIVATE ******************************/

  /**
   * Send a request
   */
  fun sendRequest(type: String, route: String, params: Map<String, Any> = emptyMap(), headers: Map<String, String> = emptyMap(), connectTimeout: Long = 4, timeout: Long = 6, completion: (IaphubError?, NetworkResponse?) -> Unit) {
    val startTime = System.currentTimeMillis()
    val infos = mutableMapOf("type" to type, "route" to route)

    // Return mock if defined
    if (this.mock != null) {
      val response = this.mock?.let { it(type, route, params) }
      if (response != null) {
        return completion(null, NetworkResponse(data = response))
      }
    }
    // Otherwise send request
    try {
      val client = OkHttpClient
        .Builder()
        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
        .callTimeout(connectTimeout + timeout, TimeUnit.SECONDS)
        .build()
      var request = Request.Builder()
      var urlBase = (this.endpoint + route).toHttpUrlOrNull()
      var url = HttpUrl.Builder()
      // Build url
      if (urlBase == null) {
        return completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.url_invalid, params=infos, silent=true), null)
      }
      url.scheme(urlBase.scheme)
      url.host(urlBase.host)
      url.port(urlBase.port)
      url.addEncodedPathSegments(urlBase.encodedPath.replaceFirst(oldValue="/", newValue=""))
      // Add headers
      for ((name, value) in this.headers) {
        request.addHeader(name, value)
      }
      // Add custom headers
      for ((name, value) in headers) {
        request.addHeader(name, value)
      }
      // Handle GET request
      if (type == "GET") {
        for ((name, value) in this.params) {
          url.addEncodedQueryParameter(name, value)
        }
        for ((name, value) in params) {
          url.addEncodedQueryParameter(name, value as? String)
        }
      }
      // Handle POST request
      else if (type == "POST") {
        var paramsMap: HashMap<String, Any> = HashMap(this.params)
        paramsMap.putAll(params)
        val paramsJson: String = Util.mapToJsonString(paramsMap)
        request.post(paramsJson.toRequestBody("application/json".toMediaTypeOrNull()))
      }
      // Add url
      request.url(url.build())
      // Execute request
      client.newCall(request.build()).enqueue(object : Callback {
        // When the request fails
        override fun onFailure(call: Call, err: IOException) {
          // Add duration to infos
          val endTime = System.currentTimeMillis()
          infos["duration"] = "${endTime - startTime}"
          // Return completion
          completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.request_failed, err.message ?: "", params=infos, silent=true), null)
        }
        // When the request succeed
        override fun onResponse(call: Call, response: Response) {
          response.use {
            // Add duration to infos
            val endTime = System.currentTimeMillis()
            infos["duration"] = "${endTime - startTime}"
            // Add status code to infos
            infos["statusCode"] = "${response.code}"
            // Create network response
            val networkResponse = NetworkResponse(httpResponse = response)
            // Return response on not modified status code
            if (networkResponse.hasNotModifiedStatusCode()) {
              return completion(null, networkResponse)
            }
            // Return error if we did not receive a 200 status code
            if (!networkResponse.hasSuccessStatusCode()) {
              return completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.status_code_error, params=infos, silent=true), networkResponse)
            }
            // Get json string
            val jsonString = response.body?.string()
            // Check that json string isn't empty
            if (jsonString == null) {
              return completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.response_empty, params=infos, silent=true), networkResponse)
            }
            // Parse json
            var jsonMap = Util.jsonStringToMap(jsonString)
            if (jsonMap == null) {
              return completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.response_parsing_failed, params=infos + mapOf("response" to jsonString), silent=true), networkResponse)
            }
            // Check if the response returned an error
            val error = jsonMap["error"] as? String
            if (error != null) {
              return completion(IaphubError(IaphubErrorCode.server_error, IaphubCustomError(error, "code: ${error}"), params=infos, silent=true), networkResponse)
            }
            // Set data in network response
            networkResponse.data = jsonMap
            // Return the network response
            completion(null, networkResponse)
          }
        }
      })
    }
    catch (err: Exception) {
      completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.unknown_exception, err.message ?: "", params=infos, silent=true), null)
    }
  }

}