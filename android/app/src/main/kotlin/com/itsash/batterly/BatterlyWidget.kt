package com.itsash.batterly

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

/**
 * Home-screen widget. Values come from [BatteryReader] (the same reader the service and app use).
 * [MonitorService] pushes updates while it runs; otherwise the system refreshes it every 30 min
 * (`updatePeriodMillis`) and the widget re-reads the battery on every update.
 */
class BatterlyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, runCatching { BatteryReader(context).read() }.getOrNull())
        for (id in appWidgetIds) manager.updateAppWidget(id, views)
    }

    companion object {
        private var lastPushMs = 0L
        private var lastKey = ""

        /** Updates every placed widget with [s] (or a fresh reading). */
        fun updateAll(context: Context, s: BatterySnapshot? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatterlyWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, s ?: runCatching { BatteryReader(context).read() }.getOrNull())
            for (id in ids) manager.updateAppWidget(id, views)
        }

        /** Called by the service on every refresh; pushes only when [WidgetLogic.pushDue] says so. */
        fun push(context: Context, s: BatterySnapshot) {
            val key = "${s.status}|${s.plugged}|${s.level}"
            val now = s.timestampMs
            val charging = s.status == ChargeState.CHARGING
            if (!WidgetLogic.pushDue(now, lastPushMs, charging, key != lastKey)) return
            lastPushMs = now
            lastKey = key
            updateAll(context, s)
        }

        private fun build(context: Context, s: BatterySnapshot?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_batterly)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            if (s == null) {
                views.setTextViewText(R.id.widget_state, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_main, "--")
                views.setTextViewText(R.id.widget_detail, "")
                views.setViewVisibility(R.id.widget_extra, View.GONE)
                return views
            }

            val charging = s.status == ChargeState.CHARGING
            val prefs = BatteryPrefs(context)
            views.setTextViewText(R.id.widget_state, NotificationText.title(s))

            if (charging) {
                val w = s.watts
                val ma = s.currentMa
                views.setTextViewText(
                    R.id.widget_main,
                    if (w != null) String.format(Locale.US, "%.1f W", w) else "${s.level}%",
                )
                val parts = mutableListOf<String>()
                if (ma != null) parts += String.format(Locale.US, "%.2f A", ma / 1000)
                parts += String.format(Locale.US, "%.1f V", s.voltageMv / 1000.0)
                parts += "${s.level}%"
                views.setTextViewText(R.id.widget_detail, parts.joinToString(" · "))
                val eta = WidgetLogic.minutesToFull(s.level, true, ma, s.chargeCounterUah)
                setExtra(views, eta?.let { context.getString(R.string.widget_full_in, WidgetLogic.duration(it)) })
            } else {
                views.setTextViewText(R.id.widget_main, "${s.level}%")
                val temp = if (prefs.tempUnit == "f") {
                    String.format(Locale.US, "%.1f °F", s.temperatureC * 9 / 5 + 32)
                } else {
                    String.format(Locale.US, "%.1f °C", s.temperatureC)
                }
                views.setTextViewText(R.id.widget_detail, temp)
                val peak = prefs.lastPeakWatts
                setExtra(views, if (peak > 0) context.getString(R.string.widget_last_peak, String.format(Locale.US, "%.1f", peak)) else null)
            }
            return views
        }

        private fun setExtra(views: RemoteViews, text: String?) {
            if (text == null) {
                views.setViewVisibility(R.id.widget_extra, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_extra, View.VISIBLE)
                views.setTextViewText(R.id.widget_extra, text)
            }
        }
    }
}
