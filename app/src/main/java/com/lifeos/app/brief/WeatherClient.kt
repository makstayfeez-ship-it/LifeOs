package com.lifeos.app.brief

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Weather(val tempC: Int, val minC: Int, val maxC: Int, val description: String)

data class GeoPoint(val name: String, val lat: Double, val lon: Double)

/** Погода через Open-Meteo: бесплатно и без ключа. */
class WeatherClient {
    suspend fun geocode(city: String): Result<GeoPoint> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(city.trim(), "UTF-8")
            val json = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ru")
            val first = json.optJSONArray("results")?.optJSONObject(0) ?: error("Город не найден")
            GeoPoint(first.optString("name", city), first.getDouble("latitude"), first.getDouble("longitude"))
        }
    }

    suspend fun forecast(lat: Double, lon: Double): Result<Weather> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            val json = getJson(url)
            val cur = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            Weather(
                tempC = cur.getDouble("temperature_2m").roundToInt(),
                minC = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                maxC = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                description = describe(cur.getInt("weather_code")),
            )
        }
    }

    private fun getJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Коды погоды WMO в человеческих словах. */
    private fun describe(code: Int): String = when (code) {
        0 -> "ясно"
        1 -> "в основном ясно"
        2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55, 56, 57 -> "морось"
        61, 63, 65, 66, 67 -> "дождь"
        71, 73, 75, 77 -> "снег"
        80, 81, 82 -> "ливень"
        85, 86 -> "снегопад"
        95, 96, 99 -> "гроза"
        else -> "погода без данных"
    }
}
