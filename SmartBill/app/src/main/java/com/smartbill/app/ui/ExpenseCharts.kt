package com.smartbill.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal val summaryColors = intArrayOf(
    Color.rgb(34, 137, 88), Color.rgb(52, 161, 108), Color.rgb(81, 177, 131),
    Color.rgb(113, 191, 153), Color.rgb(143, 205, 174), Color.rgb(175, 221, 198),
    Color.rgb(93, 156, 151), Color.rgb(134, 173, 129)
)

internal class ExpenseRing(context: Context, private val values: List<Double>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        val total = values.sum()
        if (total <= 0) return
        paint.strokeWidth = 24 * resources.displayMetrics.density
        val radius = minOf(width, height) * 0.37f
        val centerX = width / 2f
        val centerY = height / 2f
        val circle = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        var start = -90f
        values.forEachIndexed { index, amount ->
            val sweep = (amount / total * 360).toFloat()
            val gap = if (values.size == 1) 0f else minOf(1.5f, sweep * 0.15f)
            paint.color = summaryColors[index % summaryColors.size]
            canvas.drawArc(circle, start + gap / 2, sweep - gap, false, paint)
            start += sweep
        }
    }
}

internal class ExpenseBars(
    context: Context,
    private val amounts: List<Double>,
    private val labels: List<String>,
    private val emphasized: Int,
    private val showAmounts: Boolean,
    private val onBarSelected: (Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val format = NumberFormat.getNumberInstance(Locale.SIMPLIFIED_CHINESE).apply {
        maximumFractionDigits = if (showAmounts) 2 else 0
    }
    private val density get() = resources.displayMetrics.density
    private val labelColor = TypedValue().let {
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, it, true)
        if (it.resourceId == 0) it.data else context.getColorStateList(it.resourceId).defaultColor
    }
    private val left get() = if (showAmounts) 0f else 62 * density
    private val chartWidth get() = (width - left).coerceAtLeast(1f)
    private var selected = emphasized

    init {
        isClickable = true
        contentDescription = labels.zip(amounts)
            .filter { (label, amount) -> label.isNotBlank() || amount > 0 }
            .joinToString("，") { (label, amount) -> "$label ¥${format.format(amount)}" }
    }

    override fun onDraw(canvas: Canvas) {
        val top = 22 * density
        val bottom = height - 26 * density
        if (bottom <= top) return
        val slot = chartWidth / amounts.size
        val peak = amounts.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        paint.textSize = 10 * density

        if (!showAmounts) {
            for (line in 0..4) {
                val y = bottom - (bottom - top) * line / 4
                paint.color = Color.argb(48, 110, 130, 122)
                canvas.drawLine(left, y, width.toFloat(), y, paint)
                paint.color = labelColor
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("¥${format.format(peak * line / 4)}", left - 5 * density, y + 3 * density, paint)
            }
        }

        for (index in amounts.indices) {
            val amount = amounts[index]
            val center = left + slot * (index + 0.5f)
            val barHeight = ((bottom - top) * amount / peak).toFloat()
            paint.color = if (index == selected) summaryColors[0] else summaryColors[5]
            val barWidth = slot * if (showAmounts) 0.35f else 0.62f
            if (amount > 0) {
                canvas.drawRect(center - barWidth / 2, bottom - barHeight, center + barWidth / 2, bottom, paint)
            }
            paint.textAlign = Paint.Align.CENTER
            paint.color = labelColor
            if (showAmounts && amount > 0) {
                val amountLabel = "¥${format.format(amount)}"
                if (paint.measureText(amountLabel) < slot) {
                    canvas.drawText(amountLabel, center, bottom - barHeight - 5 * density, paint)
                }
            }
            canvas.drawText(labels[index], center, bottom + 16 * density, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && event.x >= left) {
            val slot = chartWidth / amounts.size
            selected = ((event.x - left) / slot).toInt().coerceIn(amounts.indices)
            performClick()
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (selected !in amounts.indices) selected = amounts.indices.first()
        onBarSelected(selected)
        return true
    }
}
