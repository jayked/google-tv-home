package com.jayked.tvhome.data

import androidx.annotation.StringRes
import com.jayked.tvhome.BuildConfig
import com.jayked.tvhome.R
import kotlin.math.roundToInt
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Weather(val temperature: Int, val unit: String, @StringRes val description: Int?, val place: String?)

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
        val ip = JSONObject(get("https://ipwho.is/"))
        check(ip.optBoolean("success")) { ip.optString("message") }
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
        0 -> R.string.weather_clear
        1, 2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        in 51..57 -> R.string.weather_drizzle
        in 61..67, in 80..82 -> R.string.weather_rain
        in 71..77, 85, 86 -> R.string.weather_snow
        in 95..99 -> R.string.weather_thunderstorm
        else -> null
    }

    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM")
}
