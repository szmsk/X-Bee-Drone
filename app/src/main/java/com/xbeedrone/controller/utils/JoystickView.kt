package com.xbeedrone.controller.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * Wirtualny joystick dotykowy dla sterowania dronem.
 *
 * Zwraca wartości osi X i Y w zakresie -1.0 do 1.0.
 * Obsługuje tryb powrotu do centrum (throttle) lub trzymania pozycji.
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface JoystickListener {
        fun onJoystickMove(x: Float, y: Float)
        fun onJoystickReleased()
    }

    var listener: JoystickListener? = null

    // Czy joystick wraca do centrum po puszczeniu (false = throttle)
    var returnToCenter: Boolean = true

    // Kolor joysticka
    var outerColor: Int = Color.argb(80, 255, 255, 255)
    var innerColor: Int = Color.argb(180, 0, 150, 255)
    var innerColorActive: Int = Color.argb(220, 0, 200, 255)
    var crosshairColor: Int = Color.argb(60, 255, 255, 255)

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var innerRadius = 0f

    private var thumbX = 0f
    private var thumbY = 0f
    private var isPressed = false
    private var activePointerId = -1

    // Wartości wyjściowe -1.0 .. 1.0
    var axisX: Float = 0f; private set
    var axisY: Float = 0f; private set

    init {
        setupPaints()
    }

    private fun setupPaints() {
        outerPaint.apply {
            style = Paint.Style.FILL
            color = outerColor
        }
        borderPaint.apply {
            style = Paint.Style.STROKE
            color = Color.argb(120, 255, 255, 255)
            strokeWidth = 2f
        }
        innerPaint.apply {
            style = Paint.Style.FILL
            color = innerColor
        }
        crosshairPaint.apply {
            style = Paint.Style.STROKE
            color = crosshairColor
            strokeWidth = 1.5f
        }
        textPaint.apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        outerRadius = (minOf(w, h) / 2f) - 8f
        innerRadius = outerRadius * 0.36f
        thumbX = centerX
        thumbY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Zewnętrzny okrąg z gradientem
        outerPaint.color = outerColor
        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
        canvas.drawCircle(centerX, centerY, outerRadius, borderPaint)

        // Celownik (crosshair)
        canvas.drawLine(centerX - outerRadius + 8, centerY,
            centerX + outerRadius - 8, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - outerRadius + 8,
            centerX, centerY + outerRadius - 8, crosshairPaint)

        // Koncentryczne kółka pomocnicze
        canvas.drawCircle(centerX, centerY, outerRadius * 0.5f, crosshairPaint)

        // Kciuk joysticka
        innerPaint.color = if (isPressed) innerColorActive else innerColor

        // Cień kciuka
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(60, 0, 0, 0)
        }
        canvas.drawCircle(thumbX + 4f, thumbY + 4f, innerRadius, shadowPaint)
        canvas.drawCircle(thumbX, thumbY, innerRadius, innerPaint)

        // Blask na kciuku
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(80, 255, 255, 255)
        }
        canvas.drawCircle(thumbX - innerRadius * 0.2f, thumbY - innerRadius * 0.2f,
            innerRadius * 0.4f, glowPaint)

        // Wartości osi (debug)
        if (isPressed) {
            val xStr = String.format("%.2f", axisX)
            val yStr = String.format("%.2f", axisY)
            canvas.drawText("X:$xStr Y:$yStr", centerX, centerY + outerRadius + 30f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                activePointerId = event.getPointerId(idx)
                isPressed = true
                updateThumb(event.getX(idx), event.getY(idx))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx != -1) {
                    updateThumb(event.getX(idx), event.getY(idx))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == activePointerId) {
                    activePointerId = -1
                    isPressed = false
                    if (returnToCenter) {
                        thumbX = centerX
                        thumbY = centerY
                        axisX = 0f
                        axisY = 0f
                    } else {
                        // Throttle: zatrzymaj tylko X
                        thumbX = centerX
                        axisX = 0f
                        // Y zostaje na miejscu
                    }
                    listener?.onJoystickReleased()
                    listener?.onJoystickMove(axisX, axisY)
                    invalidate()
                }
                return true
            }
        }
        return false
    }

    private fun updateThumb(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist <= outerRadius) {
            thumbX = touchX
            thumbY = touchY
        } else {
            // Klip do granicy
            val angle = atan2(dy, dx)
            thumbX = centerX + cos(angle) * outerRadius
            thumbY = centerY + sin(angle) * outerRadius
        }

        axisX = (thumbX - centerX) / outerRadius
        axisY = (thumbY - centerY) / outerRadius

        listener?.onJoystickMove(axisX, axisY)
        invalidate()
    }

    fun reset() {
        thumbX = centerX
        thumbY = centerY
        axisX = 0f
        axisY = 0f
        isPressed = false
        activePointerId = -1
        invalidate()
    }
}
