package com.itsash.batterly

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/**
 * MethodChannel "com.itsash.batterly/control" (Dart: lib/battery/battery_platform.dart):
 *   - getConfig() -> {monitor, chargeAlarm, chargeLimit, lowReminder, lowLevel, vibrate, tempUnit, running}
 *   - setConfig({key, value}): saves one setting; "monitor" also starts/stops MonitorService.
 *   - status() -> {notifications, batteryOptimizationsIgnored, hasCurrentSensor}
 *   - openPermission({name}): "notifications" | "battery".
 *   - samples({sinceMs}) -> [{ts, level, currentMa, voltageMv, tempC, plugged, screenOn}] oldest first.
 *   - clearHistory().
 * EventChannel "com.itsash.batterly/live": a BatterySnapshot map (see Snapshot.kt) every second
 * while Flutter listens.
 */
class MainActivity : FlutterActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val liveHandler = Handler(Looper.getMainLooper())
    private var liveTick: Runnable? = null
    private var db: HistoryDb? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        val prefs = BatteryPrefs(this)
        val reader = BatteryReader(this)
        val history = HistoryDb(this).also { db = it }

        MethodChannel(messenger, "com.itsash.batterly/control").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "getConfig" -> result.success(prefs.toMap() + ("running" to MonitorService.running))
                    "setConfig" -> {
                        val key = call.argument<String>("key")
                        if (!prefs.set(key, call.argument<Any>("value"))) {
                            result.error("bad_setting", "Unknown or invalid setting: $key", null)
                        } else {
                            if (key == "monitor") MonitorService.sync(this)
                            result.success(null)
                        }
                    }
                    "status" -> result.success(status(reader))
                    "openPermission" -> {
                        openPermission(call.argument<String>("name"))
                        result.success(null)
                    }
                    "samples" -> {
                        val since = call.argument<Number>("sinceMs")?.toLong() ?: 0L
                        io.execute {
                            val rows = runCatching { history.samples(since) }
                            ui.post {
                                rows.onSuccess { result.success(it) }
                                    .onFailure { result.error("db", it.message, null) }
                            }
                        }
                    }
                    "clearHistory" -> io.execute {
                        runCatching { history.clear() }
                        ui.post { result.success(null) }
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Throwable) {
                result.error("failed", e.message, null)
            }
        }

        EventChannel(messenger, "com.itsash.batterly/live").setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                val tick = object : Runnable {
                    override fun run() {
                        try {
                            events.success(reader.read().toMap())
                        } catch (e: Throwable) {
                            events.error("read_failed", e.message, null)
                        }
                        liveHandler.postDelayed(this, 1000)
                    }
                }
                liveTick = tick
                tick.run()
            }

            override fun onCancel(arguments: Any?) {
                liveTick?.let { liveHandler.removeCallbacks(it) }
                liveTick = null
            }
        })
    }

    override fun onDestroy() {
        liveTick?.let { liveHandler.removeCallbacks(it) }
        liveTick = null
        io.shutdown()
        db?.close()
        db = null
        super.onDestroy()
    }

    private fun status(reader: BatteryReader): Map<String, Any?> {
        val nm = getSystemService(NotificationManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            nm.areNotificationsEnabled()
        }
        return mapOf(
            "notifications" to notifications,
            "batteryOptimizationsIgnored" to power.isIgnoringBatteryOptimizations(packageName),
            "hasCurrentSensor" to reader.hasCurrentSensor(),
        )
    }

    private fun openPermission(name: String?) {
        when (name) {
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !askedNotifications()
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                } else {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                    )
                }
            }
            "battery" -> {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
    }

    /** Ask with the system dialog once; after that, open the app's notification settings. */
    private fun askedNotifications(): Boolean {
        val sp = getSharedPreferences(BatteryPrefs.FILE, MODE_PRIVATE)
        val asked = sp.getBoolean(BatteryPrefs.KEY_ASKED_NOTIFICATIONS, false)
        if (!asked) sp.edit().putBoolean(BatteryPrefs.KEY_ASKED_NOTIFICATIONS, true).apply()
        return asked
    }
}
