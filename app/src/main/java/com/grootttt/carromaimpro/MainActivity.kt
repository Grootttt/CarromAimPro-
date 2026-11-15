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
            setPadding(4, 8, 4, 8)
        }
        val help = TextView(this).apply {
            text = "Disc Pool practice guide • Tap to place points, then drag them to fine-tune."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(4, 0, 4, 6)
        }
        val status = TextView(this).apply {
            text = "1/3  •  Tap the striker position"
            textSize = 14f
            setTextColor(Color.rgb(120, 220, 170))
            setPadding(4, 6, 4, 6)
        }
        val board = AimBoardView(this) { step ->
            status.text = when (step) {
                0 -> "1/3  •  Tap the striker position"
                1 -> "2/3  •  Tap the target coin"
                2 -> "3/3  •  Tap the pocket"
                else -> "Ready  •  Drag S, C or P to fine-tune"
            }
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val reset = Button(this).apply {
            text = "Reset"
            setOnClickListener { board.reset() }
        }
        val bank = Button(this).apply {
            text = "Direct / Bank"
            setOnClickListener {
                board.toggleBank()
                text = if (board.isBankMode()) "Bank / Direct" else "Direct / Bank"
            }
        }
        controls.addView(reset, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(bank, LinearLayout.LayoutParams(0, -2, 1f))
        val note = TextView(this).apply {
            text = "Manual planning aid only. Match the points to your game board. Actual shots depend on speed, spin and collisions."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 4)
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
    private var dragging = -1

    fun isBankMode() = bankMode

    fun reset() {
        striker = null
        coin = null
        pocket = null
        dragging = -1
        onStep(0)
        invalidate()
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
        val l = left + inset
        val t = top + inset
        val r = right - inset
        val b = bottom - inset

        paint.color = Color.rgb(244, 222, 183)
        canvas.drawRect(l, t, r, b, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(115, 65, 38)
        canvas.drawRect(l, t, r, b, paint)
        paint.style = Paint.Style.FILL

        val pr = side * 0.035f
        listOf(Point(l, t), Point(r, t), Point(l, b), Point(r, b)).forEach {
            paint.color = Color.BLACK
            canvas.drawCircle(it.x, it.y, pr, paint)
        }
        paint.color = Color.rgb(170, 105, 55)
        canvas.drawCircle(width / 2f, height / 2f, side * .12f, paint)
        paint.color = Color.rgb(244, 222, 183)
        canvas.drawCircle(width / 2f, height / 2f, side * .105f, paint)

        val s = striker
        val c = coin
        val p = pocket
        if (s != null && c != null) {
            paint.color = Color.rgb(30, 110, 220)
            paint.strokeWidth = 4f
            canvas.drawLine(s.x, s.y, c.x, c.y, paint)
            if (p != null) {
                paint.color = Color.rgb(30, 160, 100)
                paint.strokeWidth = 4f
                canvas.drawLine(c.x, c.y, p.x, p.y, paint)
                if (bankMode) drawBankPath(canvas, c, p, l, t, r, b)
            }
        }
        drawPoint(canvas, s, Color.rgb(30, 110, 220), "S")
        drawPoint(canvas, c, Color.rgb(220, 65, 55), "C")
        drawPoint(canvas, p, Color.rgb(30, 160, 100), "P")
        paint.color = Color.DKGRAY
        paint.textSize = 12f
        canvas.drawText(if (bankMode) "BANK GUIDE" else "DIRECT GUIDE", l + 8, b - 10, paint)
    }

    private fun drawPoint(canvas: Canvas, point: Point?, color: Int, label: String) {
        if (point == null) return
        paint.color = color
        canvas.drawCircle(point.x, point.y, 15f, paint)
        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, point.x, point.y + 4f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawBankPath(canvas: Canvas, coin: Point, pocket: Point, l: Float, t: Float, r: Float, b: Float) {
        // Mirror the pocket across each cushion and draw the first valid one-cushion route.
        val targets = listOf(
            Point(2f * r - pocket.x, pocket.y) to 0,
            Point(2f * l - pocket.x, pocket.y) to 1,
            Point(pocket.x, 2f * b - pocket.y) to 2,
            Point(pocket.x, 2f * t - pocket.y) to 3
        )
        for ((target, side) in targets) {
            val dx = target.x - coin.x
            val dy = target.y - coin.y
            val fraction: Float
            val hit: Point
            if (side <= 1) {
                if (abs(dx) < 0.001f) continue
                val wallX = if (side == 0) r else l
                fraction = (wallX - coin.x) / dx
                hit = Point(wallX, coin.y + dy * fraction)
                if (fraction !in 0f..1f || hit.y !in t..b) continue
            } else {
                if (abs(dy) < 0.001f) continue
                val wallY = if (side == 2) b else t
                fraction = (wallY - coin.y) / dy
                hit = Point(coin.x + dx * fraction, wallY)
                if (fraction !in 0f..1f || hit.x !in l..r) continue
            }
            paint.color = Color.rgb(145, 65, 180)
            paint.strokeWidth = 3f
            canvas.drawLine(coin.x, coin.y, hit.x, hit.y, paint)
            canvas.drawLine(hit.x, hit.y, pocket.x, pocket.y, paint)
            canvas.drawCircle(hit.x, hit.y, 6f, paint)
            break
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val radius = 42f
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touch = Point(event.x, event.y)
                val points = listOf(striker, coin, pocket)
                dragging = points.indices
                    .filter { points[it] != null }
                    .minByOrNull { i ->
                        val point = points[i]!!
                        hypot((touch.x - point.x).toDouble(), (touch.y - point.y).toDouble())
                    }?.takeIf { i ->
                        val point = points[i]!!
                        hypot((touch.x - point.x).toDouble(), (touch.y - point.y).toDouble()) <= radius
                    } ?: -1

                if (dragging >= 0) {
                    movePoint(dragging, touch)
                } else {
                    val next = when {
                        striker == null -> 0
                        coin == null -> 1
                        pocket == null -> 2
                        else -> {
                            striker = null
                            coin = null
                            pocket = null
                            0
                        }
                    }
                    movePoint(next, touch)
                    onStep(if (next == 0) 1 else if (next == 1) 2 else 3)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging >= 0) {
                    movePoint(dragging, Point(event.x, event.y))
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = -1
                performClick()
                return true
            }
        }
        return true
    }

    private fun movePoint(index: Int, point: Point) {
        val side = min(width, height) * 0.92f
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val inset = side * 0.075f
        val x = point.x.coerceIn(left + inset, left + side - inset)
        val y = point.y.coerceIn(top + inset, top + side - inset)
        val adjusted = Point(x, y)
        when (index) {
            0 -> striker = adjusted
            1 -> coin = adjusted
            2 -> pocket = adjusted
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
