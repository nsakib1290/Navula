package com.example.telemetry

import android.util.Log
import com.example.BuildConfig

/**
 * Debug-only diagnostic logger for critical lifecycle and shield events.
 * Zero allocations, zero StateFlow updates, and zero background loops in production builds.
 */
object PerformanceMonitor {

    private const val TAG = "NavulaPerf"

    fun logDiagnostic(event: String, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        if (detail.isEmpty()) {
            Log.d(TAG, event)
        } else {
            Log.d(TAG, "$event: $detail")
        }
    }

    fun onWebViewCreated() {
        if (BuildConfig.DEBUG) Log.d(TAG, "WebView created")
    }

    fun onWebViewDestroyed() {
        if (BuildConfig.DEBUG) Log.d(TAG, "WebView destroyed")
    }

    fun onWebViewTimersStateChanged(paused: Boolean) {
        if (BuildConfig.DEBUG) Log.d(TAG, "WebView timers paused=$paused")
    }

    fun onPlayerCreated() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Media3 player created")
    }

    fun onPlayerReleased() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Media3 player released")
    }
}
