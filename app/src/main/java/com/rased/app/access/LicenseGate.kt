package com.rased.app.access

import java.util.Calendar

/** Free access ends at the start of 21 October 2026 in the device's local time. */
fun requiresPaidVersion(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val expiry = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 21, 0, 0, 0)
    }.timeInMillis
    return nowMillis >= expiry
}
