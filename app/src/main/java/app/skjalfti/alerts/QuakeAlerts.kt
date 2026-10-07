package app.skjalfti.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.skjalfti.Engine
import app.skjalfti.Fmt
import app.skjalfti.MainActivity
import app.skjalfti.R
import app.skjalfti.data.AlertRule
import app.skjalfti.data.Geo
import app.skjalfti.data.Quake
import app.skjalfti.data.Quakes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Big-quake alerts: a notification for every quake at or above the chosen magnitude within the
 * radius. Checked every refresh while the app is open and every ~15 minutes in the background
 * (Android's shortest repeating job). Each quake alerts once; nothing older than when alerts
 * were turned on, or older than three hours, ever alerts.
 */
object QuakeAlerts {
    private const val CH = "quake_alerts"
    private const val WORK = "skjalfti-quake-alerts"
    private const val MAX_AGE_MS = AlertRule.MAX_AGE_MS
    const val EXTRA_QUAKE = "app.skjalfti.extra.QUAKE_ID"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH, "Quake alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Bigger quakes near you"
            }
        )
    }

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<QuakeAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Checks [quakes] (or fetches the last three hours) and notifies for new big ones. */
    suspend fun check(context: Context, quakes: List<Quake>? = null) {
        Engine.init(context)
        val prefs = Engine.prefs
        val minMag = prefs.alertMag
        if (minMag <= 0f) return
        val now = System.currentTimeMillis()
        if (prefs.alertsSince == 0L) {
            // First run ever: start the clock rather than alerting about the backlog.
            prefs.alertsSince = now
            return
        }
        val list = quakes ?: withContext(Dispatchers.IO) { Quakes.fetch(now - MAX_AGE_MS, now) }
        val home = Engine.home.value
        val picks = AlertRule.pick(list, minMag, Engine.radius.value, home, prefs.alertsSince, prefs.alerted, now)
        if (picks.isEmpty()) return
        prefs.alerted = prefs.alerted + picks.map { it.id }
        if (!canNotify(context)) return
        createChannel(context)
        picks.forEach { notify(context, it, Geo.epicentreKm(it, home)) }
    }

    private fun notify(context: Context, q: Quake, km: Double) {
        val open = PendingIntent.getActivity(
            context, q.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_QUAKE, q.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH)
            .setSmallIcon(R.drawable.ic_stat_quake)
            .setContentTitle("M${Fmt.mag(q.magnitude)} · ${q.region}")
            .setContentText("${Fmt.km(km)} from you · ${Fmt.hhmm(q.timeMs)} · depth ${Fmt.mag(q.depthKm)} km")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(q.id.hashCode(), n) }
    }
}

class QuakeAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try { QuakeAlerts.check(applicationContext) } catch (e: Exception) { }
        return Result.success()
    }
}
