package com.example.pedulidiriku.data

import android.content.Context
import android.os.SystemClock
import com.example.pedulidiriku.domain.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import javax.net.ssl.HttpsURLConnection

class NetworkClock(context: Context) : AppClock {
    private val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
    @Volatile
    private var anchor: Pair<Instant, Long>? = null
    override fun zone(): ZoneId = ZoneId.of(prefs.getString("zone", "Asia/Jakarta")!!)
    fun setZone(zone: String) {
        ZoneId.of(zone); prefs.edit().putString("zone", zone).apply(); anchor = null
    }

    override fun now(): Instant =
        anchor?.let { it.first.plusMillis(SystemClock.elapsedRealtime() - it.second) }
            ?: Instant.now()

    override suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        val connection =
            URL("https://worldtimeapi.org/api/timezone/${zone().id}").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            val body = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            anchor = Instant.parse(body.getString("utc_datetime")) to SystemClock.elapsedRealtime()
            true
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
