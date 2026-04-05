package com.xbeedrone.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Niestandardowy widok joysticka dla sterowania dronem.
 *
 * Używany w parze: lewy = gaz (throttle) + obrót (yaw)
 *                  prawy = pitch + roll
 *
 * Zwraca wartości osi w zakresie 0–255 (środek = 128).
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnMoveListener {
        /** xAxis, yAxis: wartości -1.0 do 1.0 */
        fun onMove(xAxis: Float, yAxis: Float)
    }

    var onMoveListener: OnMoveListener? = null

    // Kolory
    private val colorBg     = Color.argb(180, 30, 30, 30)
    private val colorBase   = Color.argb(200, 60, 60, 60)
    private val colorKnob   = Color.argb(230, 255, 80, 0)
    private val colorRing   = Color.argb(160, 255, 80, 0)
    private val colorText   = Color.WHITE

    private val paintBg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBg }
    private val paintBase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBase }
    private val paintKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorKnob }
    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorRing
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var cx = 0f
    private var cy = 0f
    private var baseRadius = 0f
    private var knobRadius = 0f

    private var knobX = 0f
    private var knobY = 0f

    // Aktualne wartości osi (-1 do 1)
    var xAxis = 0f
        private set
    var yAxis = 0f
        private set

    // Czy joystick wraca do centrum po zwolnieniu
    var autoCenter = true

    // Etykieta (np. "GAZ/YAW")
    var label: String = ""

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        baseRadius = min(w, h) / 2f * 0.85f
        knobRadius = baseRadius * 0.30f
        knobX = cx
        knobY = cy
    }

    override fun onDraw(canvas: Canvas) {
        // Tło
        canvas.drawCircle(cx, cy, baseRadius, paintBg)
        // Baza
        canvas.drawCircle(cx, cy, baseRadius * 0.75f, paintBase)
        // Pierścień graniczny
        canvas.drawCircle(cx, cy, baseRadius * 0.75f, paintRing)
        // Siatka (krzyż)
        paintRing.alpha = 80
        canvas.drawLine(cx - baseRadius, cy, cx + baseRadius, cy, paintRing)
        canvas.drawLine(cx, cy - baseRadius, cx, cy + baseRadius, paintRing)
        paintRing.alpha = 160

        // Gałka
        paintKnob.setShadowLayer(8f, 0f, 4f, Color.argb(120, 0, 0, 0))
        canvas.drawCircle(knobX, knobY, knobRadius, paintKnob)

        // Etykieta
        if (label.isNotEmpty()) {
            canvas.drawText(label, cx, cy + baseRadius + 36f, paintText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                val dist = hypot(dx, dy)
                val maxDist = baseRadius * 0.75f

                if (dist <= maxDist) {
                    knobX = event.x
                    knobY = event.y
                } else {
                    // Ogranicz do okręgu
                    val angle = atan2(dy, dx)
                    knobX = cx + maxDist * kotlin.math.cos(angle)
                    knobY = cy + maxDist * kotlin.math.sin(angle)
                }

                xAxis = (knobX - cx) / maxDist
                yAxis = (knobY - cy) / maxDist

                onMoveListener?.onMove(xAxis, -yAxis) // -yAxis: góra = dodatnia
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (autoCenter) {
                    knobX = cx
                    knobY = cy
                    xAxis = 0f
                    yAxis = 0f
                    onMoveListener?.onMove(0f, 0f)
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Przelicza wartości osi na zakres 0–255 dla protokołu UDP.
     * @param axis wartość -1.0 do 1.0
     * @return 0–255 (środek = 128)
     */
    companion object {
        fun axisToUdp(axis: Float): Int {
            return ((axis * 127) + 128).toInt().coerceIn(0, 255)
        }
    }
}
