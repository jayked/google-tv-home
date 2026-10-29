package com.jayked.tvhome.data

import java.net.HttpURLConnection
import java.net.URL

/** Blocking GET returning the response body; call off the main thread. */
internal fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    headers.forEach(connection::setRequestProperty)
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
