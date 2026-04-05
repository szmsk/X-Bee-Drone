package com.xbeedrone.controller.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sztuczny horyzont (Attitude Indicator) pokazujący przechył i pochylenie drona.
 */
class AttitudeIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var rollDeg: Float = 0f
    private var pitchDeg: Float = 0f

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        style = Paint.Style.FILL
    }
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5D4037")
        style = Paint.Style.FILL
    }
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val aircraftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun setAttitude(roll: Float, pitch: Float) {
        rollDeg = roll
        pitchDeg = pitch
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f - 4f

        // Clip do okręgu
        val clipPath = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        // Zapisz i obróć według roll
        canvas.save()
        canvas.rotate(rollDeg, cx, cy)

        // Przesunięcie pionowe według pitch (1 stopień = r/45)
        val pitchOffset = pitchDeg * (r / 45f)

        // Niebo
        canvas.drawRect(0f, 0f, w, cy + pitchOffset, skyPaint)

        // Ziemia
        canvas.drawRect(0f, cy + pitchOffset, w, h, groundPaint)

        // Linia horyzontu
        canvas.drawLine(0f, cy + pitchOffset, w, cy + pitchOffset, horizonPaint)

        // Podziałka pitch
        for (deg in -30..30 step 5) {
            if (deg == 0) continue
            val yPos = cy + pitchOffset - deg * (r / 45f)
            val lineWidth = if (deg % 10 == 0) r * 0.35f else r * 0.18f
            canvas.drawLine(cx - lineWidth, yPos, cx + lineWidth, yPos, tickPaint)
            if (deg % 10 == 0) {
                canvas.drawText("${deg}°", cx + lineWidth + 20f, yPos + 6f, textPaint)
            }
        }

        canvas.restore()

        // Symbol samolotu (stały, nie obraca się z roll)
        val wingLen = r * 0.5f
        canvas.drawLine(cx - wingLen, cy, cx - wingLen * 0.15f, cy, aircraftPaint)
        canvas.drawLine(cx + wingLen * 0.15f, cy, cx + wingLen, cy, aircraftPaint)
        canvas.drawCircle(cx, cy, 6f, aircraftPaint.apply { style = Paint.Style.FILL })
        aircraftPaint.style = Paint.Style.STROKE

        // Wskaźnik roll na górze
        canvas.save()
        canvas.rotate(rollDeg, cx, cy)
        canvas.drawLine(cx, cy - r + 10f, cx, cy - r + 30f, aircraftPaint)
        canvas.restore()

        // Ramka
        canvas.drawCircle(cx, cy, r, borderPaint)

        // Wartości
        val rollStr = "R: ${rollDeg.toInt()}°"
        val pitchStr = "P: ${pitchDeg.toInt()}°"
        textPaint.color = Color.argb(200, 255, 255, 100)
        canvas.drawText(rollStr, cx - r * 0.4f, h - 8f, textPaint)
        canvas.drawText(pitchStr, cx + r * 0.4f, h - 8f, textPaint)
    }
}
