package com.itsash.batterly

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Battery levels of paired Bluetooth devices (earbuds, headphones, watches, keyboards).
 *
 * Android reports them through the hidden `BluetoothDevice.getBatteryLevel()` (read by reflection) and the
 * `android.bluetooth.device.action.BATTERY_LEVEL_CHANGED` broadcast. Devices that do not send their
 * level over HFP / BLE battery service show as "no level". Needs `BLUETOOTH_CONNECT` on Android 12+.
 */
object BluetoothBatteries {
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Paired devices, connected ones first: `[{address, name, kind, connected, level?}]`. */
    @SuppressLint("MissingPermission")
    fun list(context: Context): List<Map<String, Any?>> {
        if (!permitted(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return emptyList()
        return bonded.map { d ->
            val level = batteryLevel(d)
            val connected = isConnected(d) ?: (level != null)
            mapOf(
                "address" to d.address,
                "name" to nameOf(d),
                "kind" to kind(d),
                "connected" to connected,
                "level" to if (connected) level else null,
            )
        }.sortedWith(compareByDescending<Map<String, Any?>> { it["connected"] as Boolean }.thenBy { (it["name"] as String).lowercase() })
    }

    /** Level from a [ACTION_BATTERY_LEVEL_CHANGED] intent, or null. */
    @SuppressLint("MissingPermission")
    fun fromIntent(intent: Intent): Pair<BluetoothDevice, Int>? {
        val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return null
        val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
        return if (level in 0..100) device to level else null
    }

    fun displayName(context: Context, d: BluetoothDevice): String =
        if (!permitted(context)) d.address else nameOf(d)

    /** The user's rename (alias, API 30+) or the device name. */
    @SuppressLint("MissingPermission")
    private fun nameOf(d: BluetoothDevice): String = runCatching {
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.alias else null) ?: d.name
    }.getOrNull() ?: d.address

    private fun batteryLevel(d: BluetoothDevice): Int? = runCatching {
        d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int
    }.getOrNull()?.takeIf { it in 0..100 }

    private fun isConnected(d: BluetoothDevice): Boolean? = runCatching {
        d.javaClass.getMethod("isConnected").invoke(d) as Boolean
    }.getOrNull()

    /** headphones | watch | keyboard | mouse | phone | computer | car | other */
    @SuppressLint("MissingPermission")
    private fun kind(d: BluetoothDevice): String {
        val cls = runCatching { d.bluetoothClass }.getOrNull() ?: return "other"
        val device = cls.deviceClass
        return when {
            device == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || device == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "car"
            cls.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> "headphones"
            cls.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE -> "watch"
            device == BluetoothClass.Device.PERIPHERAL_KEYBOARD || device == BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING -> "keyboard"
            cls.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL -> "mouse"
            cls.majorDeviceClass == BluetoothClass.Device.Major.PHONE -> "phone"
            cls.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER -> "computer"
            else -> "other"
        }
    }
}
