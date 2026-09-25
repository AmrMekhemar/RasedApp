package com.rased.app.access

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


private val EXPIRY_ZONE: ZoneId = ZoneId.of("Africa/Cairo")
private val EXPIRY_DATE: LocalDate = LocalDate.of(2026, 10, 21)

/** Free access ends at the start of 21 October 2026 in Cairo time. */
fun requiresPaidVersion(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val expiry = EXPIRY_DATE.atStartOfDay(EXPIRY_ZONE).toInstant().toEpochMilli()
    return nowMillis >= expiry
}

/** Uses the server's HTTP Date header when reachable, otherwise the device clock. */
suspend fun requiresPaidVersionWithNetwork(): Boolean = withContext(Dispatchers.IO) {
    val networkNow = runCatching {
        (URL("https://www.google.com").openConnection() as HttpURLConnection).run {
            requestMethod = "HEAD"
            connectTimeout = 2_000
            readTimeout = 2_000
            instanceFollowRedirects = true
            try {
                val date = date
                if (date > 0L) date else null
            } finally {
                disconnect()
            }
        }
    }.getOrNull()
    requiresPaidVersion(networkNow ?: System.currentTimeMillis())
}