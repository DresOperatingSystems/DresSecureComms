/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock

object MockLocation {
    fun apply(context: Context, lat: Double, lng: Double): String {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = ArrayList<String>(3)
        providers.add(LocationManager.GPS_PROVIDER)
        providers.add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(LocationManager.FUSED_PROVIDER)
        }

        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        var applied = 0
        var denied = false
        var failure = ""

        for (provider in providers) {
            try {
                try {
                    lm.removeTestProvider(provider)
                } catch (e: Exception) {
                }
                register(lm, provider)
                lm.setTestProviderEnabled(provider, true)
                lm.setTestProviderLocation(provider, fix(provider, lat, lng, now, elapsed))
                applied++
            } catch (e: SecurityException) {
                denied = true
            } catch (e: Exception) {
                if (failure.isEmpty()) failure = e.message ?: ""
            }
        }

        return when {
            applied > 0 -> "Mock location set to $lat, $lng"
            denied -> "Enable this app as the mock location app in Developer options first."
            failure.isEmpty() -> "Could not set mock location."
            else -> "Could not set mock location. $failure"
        }
    }

    fun clear(context: Context): String {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = ArrayList<String>(3)
        providers.add(LocationManager.GPS_PROVIDER)
        providers.add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(LocationManager.FUSED_PROVIDER)
        }
        var removed = 0
        for (provider in providers) {
            try {
                lm.removeTestProvider(provider)
                removed++
            } catch (e: Exception) {
            }
        }
        return if (removed > 0) "Mock location stopped. Real location restored."
        else "Mock location was not active."
    }

    private fun register(lm: LocationManager, provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lm.addTestProvider(
                provider,
                ProviderProperties.Builder()
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            lm.addTestProvider(provider, false, true, false, false, true, true, true, 1, 1)
        }
    }

    private fun fix(provider: String, lat: Double, lng: Double, now: Long, elapsed: Long): Location =
        Location(provider).apply {
            latitude = lat
            longitude = lng
            altitude = 0.0
            accuracy = 1f
            speed = 0f
            bearing = 0f
            time = now
            elapsedRealtimeNanos = elapsed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 1f
                speedAccuracyMetersPerSecond = 1f
                bearingAccuracyDegrees = 1f
            }
        }
}
