package com.grootttt.carromaimpro

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(24, 27, 32))
        }
        val heading = TextView(this).apply {
            text = "CARROM AIM PRO"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 12)
        }
        val help = TextView(this).apply {
            text = "Tap the board to place the striker, target coin and pocket in order."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(4, 0, 4, 8)
        }
        val status = TextView(this).apply {
            text = "1/3  •  Select striker"
            textSize = 15f
            setTextColor(Color.rgb(120, 220, 170))
            setPadding(4, 8, 4, 8)
        }
        val board = AimBoardView(this) { step ->
            status.text = when (step) {
                0 -> "1/3  •  Select striker"
                1 -> "2/3  •  Select target coin"
                2 -> "3/3  •  Select pocket"
                else -> "Ready  •  Choose Reset to start again"
            }
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val reset = Button(this).apply {
            text = "Reset"
            setOnClickListener { board.reset() }
        }
        val bank = Button(this).apply {
            text = "Bank shot"
            setOnClickListener { board.toggleBank() }
        }
        val swap = Button(this).apply {
            text = "Next setup"
            setOnClickListener { board.reset() }
        }
        controls.addView(reset, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(bank, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(swap, LinearLayout.LayoutParams(0, -2, 1f))
        val note = TextView(this).apply {
            text = "Planning aid only • Adjust points to match your board. Real shots vary with speed, spin and collisions."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(4, 10, 4, 4)
        }
        root.addView(heading)
        root.addView(help)
        root.addView(status)
        root.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls)
        root.addView(note)
        setContentView(root)
    }
}

private data class Point(val x: Float, val y: Float)

private class AimBoardView(
    context: android.content.Context,
    private val onStep: (Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var striker: Point? = null
    private var coin: Point? = null
    private var pocket: Point? = null
    private var bankMode = false

    fun reset() {
        striker = null; coin = null; pocket = null
        onStep(0); invalidate()
    }

    fun toggleBank() {
        bankMode = !bankMode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height) * 0.92f
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val right = left + side
        val bottom = top + side

        paint.color = Color.rgb(211, 163, 103)
        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, paint)
        val inset = side * 0.075f
        val l = left + inset; val t = top + inset
        val r = right - inset; val b = bottom - inset
        paint.color = Color.rgb(244, 222, 183)
        canvas.drawRect(l, t, r, b, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(115, 65, 38)
        canvas.drawRect(l, t, r, b, paint)
        paint.style = Paint.Style.FILL

        val pr = side * 0.035f
        listOf(Point(l,t), Point(r,t), Point(l,b), Point(r,b)).forEach {
            paint.color = Color.BLACK
            canvas.drawCircle(it.x, it.y, pr, paint)
        }
        paint.color = Color.rgb(170, 105, 55)
        canvas.drawCircle(width / 2f, height / 2f, side * .12f, paint)
        paint.color = Color.rgb(244, 222, 183)
        canvas.drawCircle(width / 2f, height / 2f, side * .105f, paint)

        val s = striker; val c = coin; val p = pocket
        if (s != null && c != null) {
            paint.color = Color.rgb(30, 110, 220)
            paint.strokeWidth = 4f
            canvas.drawLine(s.x, s.y, c.x, c.y, paint)
            if (p != null) {
                paint.color = Color.rgb(30, 160, 100)
                canvas.drawLine(c.x, c.y, p.x, p.y, paint)
                if (bankMode) drawBankPath(canvas, s, c, p, l, t, r, b)
            }
        }
        drawPoint(canvas, s, Color.rgb(30, 110, 220), "S")
        drawPoint(canvas, c, Color.rgb(220, 65, 55), "C")
        drawPoint(canvas, p, Color.rgb(30, 160, 100), "P")
        paint.color = Color.DKGRAY
        paint.textSize = 12f
        canvas.drawText(if (bankMode) "BANK MODE" else "DIRECT MODE", l + 8, b - 10, paint)
    }

    private fun drawPoint(canvas: Canvas, point: Point?, color: Int, label: String) {
        if (point == null) return
        paint.color = color
        canvas.drawCircle(point.x, point.y, 13f, paint)
        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, point.x, point.y + 4f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawBankPath(canvas: Canvas, s: Point, c: Point, p: Point, l: Float, t: Float, r: Float, b: Float) {
        // Reflect the pocket across the right cushion to estimate a one-cushion route.
        val reflected = Point(2f * r - p.x, p.y)
        val dx = reflected.x - c.x
        val dy = reflected.y - c.y
        if (abs(dx) < 0.001f) return
        val fraction = (r - c.x) / dx
        val hitY = c.y + dy * fraction
        if (fraction in 0f..1f && hitY in t..b) {
            paint.color = Color.rgb(145, 65, 180)
            paint.strokeWidth = 3f
            canvas.drawLine(c.x, c.y, r, hitY, paint)
            canvas.drawLine(r, hitY, p.x, p.y, paint)
            paint.color = Color.rgb(145, 65, 180)
            canvas.drawCircle(r, hitY, 6f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val point = Point(event.x.coerceIn(0f, width.toFloat()), event.y.coerceIn(0f, height.toFloat()))
            when {
                striker == null -> striker = point
                coin == null -> coin = point
                pocket == null -> pocket = point
                else -> { striker = point; coin = null; pocket = null }
            }
            val step = when { striker == null -> 0; coin == null -> 1; pocket == null -> 2; else -> 3 }
            onStep(step)
            invalidate()
            return true
        }
        return true
    }
}
