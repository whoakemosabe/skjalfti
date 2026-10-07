package app.skjalfti.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.skjalfti.Engine
import app.skjalfti.Fmt
import app.skjalfti.MainActivity
import app.skjalfti.R
import app.skjalfti.data.NightReport
import app.skjalfti.seismo.Seismo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The night watch: a foreground service that keeps the seismometer recording while the phone
 * lies still overnight (holding a partial wake lock so the accelerometer keeps reporting with
 * the screen off), then stops itself at 07:00 and posts the morning report.
 */
class WatchService : Service() {
    companion object {
        private const val ACTION_STOP = "app.skjalfti.action.STOP_WATCH"
        private const val CH_WATCH = "watch"
        private const val CH_REPORT = "report"
        private const val ID_WATCH = 1
        private const val ID_REPORT = 2
        /** Shorter watches are treated as a test and don't make a report. */
        private const val MIN_REPORT_MS = 30 * 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WatchService::class.java).setAction(ACTION_STOP))
        }

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CH_WATCH, "Night watch", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while the phone is recording overnight"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_REPORT, "Morning report", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "What happened while you slept"
                }
            )
        }

        /** Milliseconds from now until the next 07:00 local time. */
        fun untilSeven(nowMs: Long = System.currentTimeMillis()): Long {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
            var next = ZonedDateTime.of(LocalDate.from(now), LocalTime.of(7, 0), zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return next.toInstant().toEpochMilli() - nowMs
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private var startMs = 0L
    private var running = false
    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Engine.init(this)
        createChannels(this)
        if (intent?.action == ACTION_STOP) {
            if (running) finish() else stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY

        ServiceCompat.startForeground(
            this, ID_WATCH, watchNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val pm = getSystemService(PowerManager::class.java)
        wake = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "skjalfti:watch")?.apply {
            setReferenceCounted(false)
            acquire(14 * 3600_000L)
        }
        running = true
        finishing = false
        startMs = System.currentTimeMillis()
        Engine.listen(this, "watch")
        Seismo.startStrip()
        Engine.watching.value = true
        handler.postDelayed({ finish() }, untilSeven(startMs))
        return START_STICKY
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        handler.removeCallbacksAndMessages(null)
        val strip = Seismo.stopStrip()
        Engine.unlisten("watch")
        Engine.watching.value = false
        val end = System.currentTimeMillis()
        val start = startMs
        Engine.scope.launch {
            if (end - start >= MIN_REPORT_MS) {
                val report = Engine.nightReport(start, end, strip)
                postReport(report)
            }
            withContext(Dispatchers.Main) {
                running = false
                releaseWake()
                ServiceCompat.stopForeground(this@WatchService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (running && !finishing) {
            // Killed without a clean finish: stop recording and don't leave the flag stuck.
            Seismo.stopStrip()
            Engine.unlisten("watch")
            Engine.watching.value = false
        }
        releaseWake()
        super.onDestroy()
    }

    private fun releaseWake() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun watchNotification() = NotificationCompat.Builder(this, CH_WATCH)
        .setSmallIcon(R.drawable.ic_stat_quake)
        .setContentTitle("Night watch armed")
        .setContentText("Recording until 07:00. Leave the phone flat and still.")
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(openApp())
        .addAction(
            0, "Stop",
            PendingIntent.getService(
                this, 1,
                Intent(this, WatchService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun postReport(r: NightReport) {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val title = when (r.quakes) {
            0 -> "A quiet night"
            1 -> "While you slept: 1 quake"
            else -> "While you slept: ${r.quakes} quakes"
        }
        val text = buildString {
            append(if (r.felt == 0) "Your phone felt none" else "Your phone felt ${r.felt}")
            if (r.strongestMag != null) append(" · strongest M${Fmt.mag(r.strongestMag)} ${r.strongestRegion ?: ""}")
        }
        val n = NotificationCompat.Builder(this, CH_REPORT)
            .setSmallIcon(R.drawable.ic_stat_quake)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(ID_REPORT, n) }
    }
}
