/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

/** Sets a mock GPS fix. Requires this app to be the mock location app in Developer options. */
object MockLocation {
    fun apply(context: Context, lat: Double, lng: Double): String {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = LocationManager.GPS_PROVIDER
            try {
                lm.addTestProvider(provider, false, true, false, false, true, true, true, 1, 5)
            } catch (_: Exception) {
            }
            lm.setTestProviderEnabled(provider, true)
            val loc = Location(provider).apply {
                latitude = lat
                longitude = lng
                accuracy = 1f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            lm.setTestProviderLocation(provider, loc)
            "Mock location set to $lat, $lng"
        } catch (e: SecurityException) {
            "Enable this app as the mock location app in Developer options first."
        } catch (e: Exception) {
            "Could not set mock location: ${e.message}"
        }
    }
}
