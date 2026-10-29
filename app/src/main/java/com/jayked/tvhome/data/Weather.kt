package com.jayked.tvhome.data

import com.jayked.tvhome.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

data class Weather(val temperature: Int, val unit: String, val description: String, val place: String?)

/** Current conditions from Open-Meteo (no API key needed). Blocking; call off the main thread. */
object WeatherClient {
    fun fetch(): Weather {
        val (lat, lon, place) = location()
        val fahrenheit = Locale.getDefault().country in FAHRENHEIT_COUNTRIES
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val current = JSONObject(get(url)).getJSONObject("current")
        return Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            unit = if (fahrenheit) "°F" else "°C",
            description = describe(current.getInt("weather_code")),
            place = place,
        )
    }

    private fun location(): Triple<Double, Double, String?> {
        val lat = BuildConfig.WEATHER_LAT.toDoubleOrNull()
        val lon = BuildConfig.WEATHER_LON.toDoubleOrNull()
        if (lat != null && lon != null) return Triple(lat, lon, BuildConfig.WEATHER_PLACE.ifBlank { null })
        val ip = JSONObject(get("https://ipapi.co/json/"))
        return Triple(ip.getDouble("latitude"), ip.getDouble("longitude"), ip.optString("city").ifBlank { null })
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // WMO weather interpretation codes.
    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }

    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM")
}
