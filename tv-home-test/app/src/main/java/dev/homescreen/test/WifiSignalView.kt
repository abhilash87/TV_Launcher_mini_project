package dev.homescreen.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Compact four-level Wi-Fi icon; slash means offline, socket means Ethernet. */
class WifiSignalView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()
    private var connected = false
    private var ethernet = false
    private var level = 0

    fun update(connected: Boolean, ethernet: Boolean, level: Int) {
        if (this.connected == connected && this.ethernet == ethernet && this.level == level) return
        this.connected = connected
        this.ethernet = ethernet
        this.level = level.coerceIn(0, 4)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.scale(width / 32f, height / 32f)
        paint.color = 0xFFECEFF2.toInt()
        paint.style = Paint.Style.STROKE
        if (ethernet) {
            canvas.drawRoundRect(6f, 7f, 26f, 23f, 2f, 2f, paint)
            canvas.drawLine(12f, 23f, 12f, 27f, paint)
            canvas.drawLine(20f, 23f, 20f, 27f, paint)
            for (x in listOf(11f, 16f, 21f)) canvas.drawLine(x, 8f, x, 13f, paint)
        } else {
            for (index in 1..3) {
                val radius = 2f + index * 6f
                paint.color = if (connected && level > index) 0xFFECEFF2.toInt() else 0x55ECEFF2
                arc.set(16f - radius, 27f - radius, 16f + radius, 27f + radius)
                canvas.drawArc(arc, 225f, 90f, false, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = if (connected) 0xFFECEFF2.toInt() else 0x55ECEFF2
            canvas.drawCircle(16f, 26f, 2f, paint)
            if (!connected) {
                paint.color = 0xFFECEFF2.toInt()
                canvas.drawLine(7f, 7f, 26f, 26f, paint)
            }
        }
        canvas.restoreToCount(save)
    }
}
