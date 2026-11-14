package com.itsash.batterly

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun wattsText(w: Double): String = String.format(Locale.US, "%.1f W", w)

/**
 * 1x1 widget: the level, and charging watts (or the temperature) under it.
 * Updated together with [BatterlyWidget] (see [BatterlyWidget.updateAll]).
 */
class BatterlySmallWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, runCatching { BatteryReader(context).read() }.getOrNull())
        for (id in appWidgetIds) manager.updateAppWidget(id, views)
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, reading: () -> BatterySnapshot?) {
            val ids = manager.getAppWidgetIds(ComponentName(context, BatterlySmallWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, reading())
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun build(context: Context, s: BatterySnapshot?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_small)
            views.setOnClickPendingIntent(R.id.small_root, openApp(context))
            if (s == null) {
                views.setTextViewText(R.id.small_main, "--")
                views.setTextViewText(R.id.small_detail, "")
                return views
            }
            views.setTextViewText(R.id.small_main, "${s.level}%")
            val w = s.watts
            val detail = when {
                s.status == ChargeState.CHARGING && w != null && w > 0 -> "+" + wattsText(w)
                s.status == ChargeState.CHARGING -> context.getString(R.string.widget_charging)
                s.status == ChargeState.FULL -> context.getString(R.string.widget_full)
                else -> WidgetLogic.temperature(s.temperatureC, BatteryPrefs(context).tempUnit == "f")
            }
            views.setTextViewText(R.id.small_detail, detail)
            val accent = s.status == ChargeState.CHARGING || s.status == ChargeState.FULL
            views.setTextColor(R.id.small_detail, context.getColor(if (accent) R.color.widget_accent else R.color.widget_muted))
            return views
        }
    }
}

/**
 * 4x2 widget: state, level and watts, and a line graph of the level over the last 24 hours from
 * [HistoryDb] (needs the background monitor). The graph bitmap is redrawn at most once a minute.
 */
class BatterlyGraphWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, runCatching { BatteryReader(context).read() }.getOrNull())
        for (id in appWidgetIds) manager.updateAppWidget(id, views)
    }

    companion object {
        private const val WIDTH = 640
        private const val HEIGHT = 200
        private const val REDRAW_MS = 60_000L
        private const val SPAN_MS = 24 * 60 * 60_000L

        private var graph: Bitmap? = null
        private var graphMs = 0L
        private var graphNight = false
        private var graphHasData = false

        fun update(context: Context, manager: AppWidgetManager, reading: () -> BatterySnapshot?) {
            val ids = manager.getAppWidgetIds(ComponentName(context, BatterlyGraphWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, reading())
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun build(context: Context, s: BatterySnapshot?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_graph)
            views.setOnClickPendingIntent(R.id.graph_root, openApp(context))
            if (s == null) {
                views.setTextViewText(R.id.graph_state, context.getString(R.string.app_name))
                views.setTextViewText(R.id.graph_main, "--")
                views.setTextViewText(R.id.graph_detail, "")
            } else {
                views.setTextViewText(R.id.graph_state, NotificationText.title(s))
                val w = s.watts
                val charging = s.status == ChargeState.CHARGING
                views.setTextViewText(
                    R.id.graph_main,
                    if (charging && w != null && w > 0) "${s.level}%  +${wattsText(w)}" else "${s.level}%",
                )
                val parts = mutableListOf<String>()
                if (charging) {
                    WidgetLogic.minutesToFull(s.level, true, s.currentMa, s.chargeCounterUah)?.let {
                        parts += context.getString(R.string.widget_full_in, WidgetLogic.duration(it))
                    }
                }
                parts += WidgetLogic.temperature(s.temperatureC, BatteryPrefs(context).tempUnit == "f")
                views.setTextViewText(R.id.graph_detail, parts.joinToString(" · "))
            }

            val bmp = graphBitmap(context, s)
            views.setImageViewBitmap(R.id.graph_image, bmp)
            views.setTextViewText(
                R.id.graph_caption,
                context.getString(if (graphHasData) R.string.widget_graph_caption else R.string.widget_graph_empty),
            )
            views.setViewVisibility(R.id.graph_caption, View.VISIBLE)
            return views
        }

        private fun graphBitmap(context: Context, s: BatterySnapshot?): Bitmap {
            val now = System.currentTimeMillis()
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            graph?.let { if (now - graphMs < REDRAW_MS && night == graphNight) return it }

            val samples = runCatching {
                val db = HistoryDb(context)
                try {
                    db.levels(now - SPAN_MS)
                } finally {
                    db.close()
                }
            }.getOrDefault(emptyList()).toMutableList()
            if (s != null) samples += s.timestampMs to s.level
            val lines = WidgetLogic.graphLines(samples, now - SPAN_MS, now + 1)
            graphHasData = lines.sumOf { it.size } >= 2

            val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            draw(Canvas(bmp), lines, context.getColor(R.color.widget_accent), context.getColor(R.color.widget_muted))
            graph = bmp
            graphMs = now
            graphNight = night
            return bmp
        }

        private fun draw(canvas: Canvas, lines: List<List<WidgetLogic.GraphPoint>>, accent: Int, muted: Int) {
            val pad = 6f
            val w = WIDTH - 2 * pad
            val h = HEIGHT - 2 * pad
            fun x(p: WidgetLogic.GraphPoint) = pad + p.x * w
            fun y(p: WidgetLogic.GraphPoint) = pad + (1 - p.y) * h

            val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = muted
                alpha = 60
                strokeWidth = 2f
            }
            for (level in listOf(0f, 0.5f, 1f)) {
                val gy = pad + (1 - level) * h
                canvas.drawLine(pad, gy, WIDTH - pad, gy, grid)
            }

            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                alpha = 50
                style = Paint.Style.FILL
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            for (line in lines) {
                if (line.size == 1) {
                    canvas.drawCircle(x(line[0]), y(line[0]), 5f, dot)
                    continue
                }
                val path = Path()
                line.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p)) else path.lineTo(x(p), y(p)) }
                val area = Path(path).apply {
                    lineTo(x(line.last()), pad + h)
                    lineTo(x(line.first()), pad + h)
                    close()
                }
                canvas.drawPath(area, fill)
                canvas.drawPath(path, stroke)
            }
        }
    }
}
