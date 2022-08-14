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
  fun send(type: String, route: String, params: Map<String, Any> = emptyMap(), timeout: Long = 6, retry: Int = 2, silentLog: Boolean = false, completion: (IaphubError?, Map<String, Any>?) -> Unit) {
    // Retry request up to 2 times with a delay of 1 second
    Util.retry<Map<String, Any>>(
      times=retry,
      delay=1,
      task={ callback ->
        this.sendRequest(type=type, route=route, params=params, timeout=timeout) { err, data, httpResponse ->
          // Retry request if the request failed with a network error
          if (err != null && err.code == "network_error") {
            callback(true, err, data)
          }
          // Retry request if the request failed with status code >= 500
          else if (httpResponse != null && httpResponse.code >= 500) {
            callback(true, err, data)
          }
          // Otherwise do not retry
          else {
            callback(false, err, data)
          }
        }
      },
      completion={ err, data ->
        // Send error if there is one
        if (err != null && silentLog != true) {
          err.send()
        }
        // Call completion
        completion(err, data)
      }
    )
  }

  /***************************** PRIVATE ******************************/

  /**
   * Send a request
   */
  fun sendRequest(type: String, route: String, params: Map<String, Any> = emptyMap(), timeout: Long = 6, completion: (IaphubError?, Map<String, Any>?, Response?) -> Unit) {
    val infos = mutableMapOf("type" to type, "route" to route)

    // Return mock if defined
    if (this.mock != null) {
      val response = this.mock?.let { it(type, route, params) }
      if (response != null) {
        return completion(null, response, null)
      }
    }
    // Otherwise send request
    try {
      val client = OkHttpClient
        .Builder()
        .callTimeout(timeout, TimeUnit.SECONDS)
        .build()
      var request = Request.Builder()
      var urlBase = (this.endpoint + route).toHttpUrlOrNull()
      var url = HttpUrl.Builder()
      // Build url
      if (urlBase == null) {
        return completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.url_invalid, params=infos, silent=true), null, null)
      }
      url.scheme(urlBase.scheme)
      url.host(urlBase.host)
      url.addEncodedPathSegments(urlBase.encodedPath)
      // Add headers
      for ((name, value) in this.headers) {
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
          completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.request_failed, err.message ?: "", silent=true), null, null)
        }
        // When the request succeed
        override fun onResponse(call: Call, response: Response) {
          response.use {
            // Add status code to infos
            infos["statusCode"] = "${response.code}"
            // Get json string
            val jsonString = response.body?.string()
            // Check that json string isn't empty
            if (jsonString == null) {
              return completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.response_empty, params=infos, silent=true), null, response)
            }
            // Parse json
            var jsonMap = Util.jsonStringToMap(jsonString)
            if (jsonMap == null) {
              return completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.response_parsing_failed, params=infos + mapOf("response" to jsonString), silent=true), null, response)
            }
            // Check if the response returned an error
            val error = jsonMap["error"] as? String
            if (error != null) {
              return completion(IaphubError(IaphubErrorCode.server_error, IaphubCustomError(error, "code: ${error}"), params=infos, silent=true), null, response)
            }
            // Otherwise the request is successful, return the data
            completion(null, jsonMap, response)
          }
        }
      })
    }
    catch (err: Exception) {
      completion(IaphubError(IaphubErrorCode.network_error, IaphubNetworkErrorCode.unknown_exception, err.message ?: "", params=infos, silent=true), null, null)
    }
  }

}