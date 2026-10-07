package app.skjalfti.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Finds the phone's location without Google Play services, the same way Ljós does: a fresh fix
 * from the fused, network or GPS provider if one arrives within a few seconds, otherwise the
 * newest last-known fix. Precise permission gives GPS-level accuracy; approximate is ~1–3 km,
 * still fine for wave arrival times.
 */
object Locator {
    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(context: Context): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun precise(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun servicesOn(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).any { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            if (precise(context)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        val providers = candidates.filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }

        if (Build.VERSION.SDK_INT >= 30) {
            for (p in providers) {
                val fix = withTimeoutOrNull(12_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        try {
                            lm.getCurrentLocation(p, signal, context.mainExecutor) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fix != null) return fix
            }
        }
        return providers
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Town name for a fix, e.g. "Njarðvík". Falls back to the nearest known town. */
    @Suppress("DEPRECATION")
    suspend fun nameFor(context: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val geo = if (Geocoder.isPresent()) runCatching {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subLocality ?: a?.subAdminArea
        }.getOrNull() else null
        geo?.takeIf { it.isNotBlank() } ?: Places.nameFor(lat, lon)
    }
}
