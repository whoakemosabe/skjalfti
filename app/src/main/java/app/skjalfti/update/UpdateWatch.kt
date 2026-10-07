package app.skjalfti.update

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
import app.skjalfti.MainActivity
import app.skjalfti.R
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

/**
 * Notices new releases on its own, like Ljós: a background job asks GitHub every ~6 hours and
 * posts one quiet notification per new version; opening the app asks at most hourly and shows a
 * card on the Live screen. Tapping either opens Setup with the update ready to download.
 */
object UpdateWatch {
    private const val BACKGROUND_EVERY = 6 * 60 * 60_000L
    private const val OPEN_EVERY = 60 * 60_000L
    private const val CH_UPDATE = "updates"
    private const val ID_UPDATE = 3
    private const val WORK = "skjalfti-update-check"
    const val EXTRA_OPEN_UPDATES = "app.skjalfti.extra.OPEN_UPDATES"

    /** The newer version waiting, if any (shown as the Live card). */
    val waitingVersion = MutableStateFlow<String?>(null)

    /** Set when the app should open Setup's update section (from the notification or card). */
    val openUpdates = MutableStateFlow(false)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("skjalfti_updates", Context.MODE_PRIVATE)

    fun waiting(context: Context): String? {
        val v = prefs(context).getString("available", "") ?: ""
        return v.takeIf { it.isNotBlank() && Updater.isNewer(it, Updater.installedVersion(context)) }
    }

    /** Remembers the result of a manual check so the card and background job agree. */
    fun remember(context: Context, available: String?) {
        prefs(context).edit().putString("available", available ?: "").putLong("last", System.currentTimeMillis()).apply()
        waitingVersion.value = waiting(context)
    }

    /** Asks GitHub if it's been long enough; returns the newer version waiting, if any. */
    suspend fun check(context: Context, background: Boolean): String? {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val every = if (background) BACKGROUND_EVERY else OPEN_EVERY
        if (now - p.getLong("last", 0L) >= every) {
            p.edit().putLong("last", now).apply()
            when (val r = Updater.check(context)) {
                is Updater.Check.Available -> p.edit().putString("available", r.release.version).apply()
                Updater.Check.UpToDate -> p.edit().putString("available", "").apply()
                is Updater.Check.Failed -> {}
            }
        }
        val w = waiting(context)
        waitingVersion.value = w
        return w
    }

    /** Background: check, and notify once per new version. */
    suspend fun checkAndNotify(context: Context) {
        val v = check(context, background = true) ?: return
        val p = prefs(context)
        if (p.getString("notified", "") == v) return
        p.edit().putString("notified", v).apply()
        post(context, v)
    }

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATE, "Updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "When a new version of Skjálfti is ready"
            }
        )
    }

    private fun post(context: Context, version: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_UPDATES, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_UPDATE)
            .setSmallIcon(R.drawable.ic_stat_quake)
            .setContentTitle("Skjálfti $version is ready")
            .setContentText("Tap to update.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_UPDATE, n) }
    }

    /** Schedules the background check (idempotent). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try { UpdateWatch.checkAndNotify(applicationContext) } catch (e: Exception) { }
        return Result.success()
    }
}
