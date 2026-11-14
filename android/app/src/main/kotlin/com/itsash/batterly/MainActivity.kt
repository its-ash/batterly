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
 *   - getConfig() -> {monitor, chargeAlarm, chargeLimit, lowReminder, lowLevel, vibrate, tempUnit,
 *     statusIcon, btAlert, btLowLevel, running}
 *   - setConfig({key, value}): saves one setting; "monitor" also starts/stops MonitorService,
 *     "statusIcon" redraws its notification.
 *   - status() -> {notifications, batteryOptimizationsIgnored, hasCurrentSensor, bluetooth, sdk}
 *   - openPermission({name}): "notifications" | "battery" | "bluetooth".
 *   - samples({sinceMs}) -> [{ts, level, currentMa, voltageMv, tempC, plugged, screenOn}] oldest first.
 *   - clearHistory(): samples only; capacity estimates stay.
 *   - health() -> {designMah?, estimates: [{ts, mah, fromLevel, toLevel, charging}], chargedPercent,
 *     cycleSinceMs, cycleCount?, statsGranted, stateOfHealth?, manufactureDateMs?, firstUseDateMs?, chargingPolicy?}
 *   - bluetoothDevices() -> [{address, name, kind, connected, level?}]
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
                            if (key == "statusIcon") MonitorService.refreshNotification(this)
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
                    "health" -> io.execute {
                        val info = runCatching { health(prefs, reader, history) }
                        ui.post {
                            info.onSuccess { result.success(it) }
                                .onFailure { result.error("health", it.message, null) }
                        }
                    }
                    "bluetoothDevices" -> io.execute {
                        val list = runCatching { BluetoothBatteries.list(this) }.getOrDefault(emptyList())
                        ui.post { result.success(list) }
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
            "bluetooth" to BluetoothBatteries.permitted(this),
            "sdk" to Build.VERSION.SDK_INT,
        )
    }

    private fun health(prefs: BatteryPrefs, reader: BatteryReader, history: HistoryDb): Map<String, Any?> =
        mapOf(
            "designMah" to DeviceBattery.designCapacityMah(this),
            "estimates" to history.capacityEstimates(),
            "chargedPercent" to prefs.chargedPercent,
            "cycleSinceMs" to prefs.cycleSinceMs,
            "cycleCount" to runCatching { reader.read().cycleCount }.getOrNull(),
        ) + DeviceBattery.advanced(this)

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
            "bluetooth" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
                val sp = getSharedPreferences(BatteryPrefs.FILE, MODE_PRIVATE)
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
                    (!sp.getBoolean(KEY_ASKED_BLUETOOTH, false) || shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT))
                ) {
                    sp.edit().putBoolean(KEY_ASKED_BLUETOOTH, true).apply()
                    requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2)
                } else {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
        }
    }

    private companion object {
        const val KEY_ASKED_BLUETOOTH = "asked_bluetooth"
    }

    /** Ask with the system dialog once; after that, open the app's notification settings. */
    private fun askedNotifications(): Boolean {
        val sp = getSharedPreferences(BatteryPrefs.FILE, MODE_PRIVATE)
        val asked = sp.getBoolean(BatteryPrefs.KEY_ASKED_NOTIFICATIONS, false)
        if (!asked) sp.edit().putBoolean(BatteryPrefs.KEY_ASKED_NOTIFICATIONS, true).apply()
        return asked
    }
}
