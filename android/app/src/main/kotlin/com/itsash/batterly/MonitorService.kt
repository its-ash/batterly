package com.itsash.batterly

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.concurrent.Executors

/**
 * Foreground service behind the "Background monitor" switch. It
 *  - keeps an ongoing notification with live "+12.4 W · 2,450 mA · 63%",
 *  - logs a sample to [HistoryDb] about once a minute (and on screen on/off),
 *  - raises the charge-limit and low-battery alerts via [AlarmTracker].
 *
 * It refreshes every [TICK_MS] with a Handler and on every battery / screen broadcast.
 * No wake lock is held (this is a battery app), so while the CPU sleeps deeply the
 * Handler pauses; battery broadcasts still arrive and refresh the state, so history
 * can have gaps of a few minutes on idle phones. The app treats long gaps as unknown.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val tracker = AlarmTracker()

    private lateinit var prefs: BatteryPrefs
    private lateinit var reader: BatteryReader
    private lateinit var db: HistoryDb
    private lateinit var nm: NotificationManager

    private var lastLogMs = 0L
    private var lastText = ""
    private var receiversRegistered = false

    private val tick = object : Runnable {
        override fun run() {
            refresh(forceLog = false)
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh(forceLog = intent.action != Intent.ACTION_BATTERY_CHANGED)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = BatteryPrefs(this)
        reader = BatteryReader(this)
        db = HistoryDb(this)
        nm = getSystemService(NotificationManager::class.java)
        createChannels()
        startInForeground(reader.read())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        receiversRegistered = true
        running = true
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.monitorEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (receiversRegistered) runCatching { unregisterReceiver(receiver) }
        io.shutdown()
        super.onDestroy()
    }

    private fun refresh(forceLog: Boolean) {
        if (!prefs.monitorEnabled) {
            stopSelf()
            return
        }
        val s = runCatching { reader.read() }.getOrNull() ?: return
        updateNotification(s)
        tracker.evaluate(s.level, s.pluggedIn, prefs.alarmConfig)?.let { alert(it, s) }
        if (forceLog || s.timestampMs - lastLogMs >= LOG_INTERVAL_MS) {
            lastLogMs = s.timestampMs
            io.execute {
                runCatching {
                    db.insert(s)
                    db.prune(s.timestampMs)
                }
            }
        }
    }

    // --- notifications ---------------------------------------------------------------

    private fun createChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, "Live battery", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing notification with the live charging power and level."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Battery alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Charge limit reached and low battery reminders."
                enableVibration(false) // the vibrate option is applied per alert in code
            },
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun liveNotification(s: BatterySnapshot): Notification =
        Notification.Builder(this, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(NotificationText.title(s))
            .setContentText(NotificationText.summary(s))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

    private fun startInForeground(s: BatterySnapshot) {
        val n = liveNotification(s)
        lastText = NotificationText.title(s) + NotificationText.summary(s)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_LIVE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_LIVE, n)
        }
    }

    private fun updateNotification(s: BatterySnapshot) {
        val text = NotificationText.title(s) + NotificationText.summary(s)
        if (text == lastText) return
        lastText = text
        nm.notify(NOTIFICATION_LIVE, liveNotification(s))
    }

    private fun alert(alert: Alert, s: BatterySnapshot) {
        val (title, text) = when (alert) {
            Alert.CHARGE_LIMIT -> "Charge limit reached" to "Battery is at ${s.level}%. Unplug to protect its health."
            Alert.LOW_BATTERY -> "Battery low" to "Battery is at ${s.level}%. Time to plug in."
        }
        val n = Notification.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        nm.notify(NOTIFICATION_ALARM, n)
        if (prefs.vibrate) {
            getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1),
            )
        }
    }

    companion object {
        private const val CHANNEL_LIVE = "battery_live"
        private const val CHANNEL_ALARM = "battery_alarm"
        private const val NOTIFICATION_LIVE = 11
        private const val NOTIFICATION_ALARM = 12
        private const val TICK_MS = 5_000L
        private const val LOG_INTERVAL_MS = 60_000L

        /** True while the service is alive in this process. */
        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }

        /** Applies the "background monitor" setting: starts or stops the service. */
        fun sync(context: Context) {
            if (BatteryPrefs(context).monitorEnabled) start(context) else stop(context)
        }
    }
}

/** Restarts the monitor after a reboot or an app update, if it is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (BatteryPrefs(context).monitorEnabled) {
                    runCatching { MonitorService.start(context) }
                }
            }
        }
    }
}
