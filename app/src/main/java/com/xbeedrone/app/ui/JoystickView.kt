package com.xbeedrone.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface OnMoveListener { fun onMove(x: Float, y: Float) }

    var onMoveListener: OnMoveListener? = null
    var autoCenter = true
    var label: String = ""

    private val paintBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 20, 20, 20) }
    private val paintBase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 50, 50, 50) }
    private val paintKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 255, 87, 34) }
    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 87, 34); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255); strokeWidth = 1f
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255); textSize = 26f; textAlign = Paint.Align.CENTER
    }

    private var cx = 0f; private var cy = 0f
    private var baseR = 0f; private var knobR = 0f
    private var knobX = 0f; private var knobY = 0f

    var xAxis = 0f; var yAxis = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f; cy = h / 2f
        baseR = min(w, h) / 2f * 0.88f
        knobR = baseR * 0.28f
        knobX = cx; knobY = cy
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, baseR, paintBg)
        canvas.drawCircle(cx, cy, baseR * 0.72f, paintBase)
        canvas.drawCircle(cx, cy, baseR * 0.72f, paintRing)
        canvas.drawLine(cx - baseR * 0.72f, cy, cx + baseR * 0.72f, cy, paintLine)
        canvas.drawLine(cx, cy - baseR * 0.72f, cx, cy + baseR * 0.72f, paintLine)
        canvas.drawCircle(knobX, knobY, knobR, paintKnob)
        if (label.isNotEmpty()) canvas.drawText(label, cx, cy + baseR + 32f, paintText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx; val dy = event.y - cy
                val dist = hypot(dx, dy); val maxD = baseR * 0.72f
                if (dist <= maxD) { knobX = event.x; knobY = event.y }
                else { val a = atan2(dy, dx); knobX = cx + maxD * cos(a); knobY = cy + maxD * sin(a) }
                xAxis = (knobX - cx) / maxD
                yAxis = (knobY - cy) / maxD
                onMoveListener?.onMove(xAxis, -yAxis)
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (autoCenter) { knobX = cx; knobY = cy; xAxis = 0f; yAxis = 0f; onMoveListener?.onMove(0f, 0f); invalidate() }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        fun axisToUdp(axis: Float): Int = ((axis * 127) + 128).toInt().coerceIn(0, 255)
    }
}
