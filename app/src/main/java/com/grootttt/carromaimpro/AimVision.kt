package com.grootttt.carromaimpro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

data class AimPoint(val x: Float, val y: Float)
data class AimResult(val striker: AimPoint, val coin: AimPoint, val pocket: AimPoint, val contact: AimPoint, val detectedCoins: Int, val confidence: Float)

object CarromVision {
    fun analyze(bitmap: android.graphics.Bitmap): AimResult? {
        if (!OpenCVLoader.initLocal()) return null
        val rgba = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        val gray = Mat()
        val circles = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(7.0, 7.0), 1.5)
            Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.2,
                max(18.0, bitmap.width * 0.018), 105.0, 25.0,
                max(7, bitmap.width / 115), max(18, bitmap.width / 22))
            if (circles.empty()) return null
            val found = ArrayList<AimPoint>()
            for (i in 0 until circles.cols()) {
                val v = circles.get(0, i) ?: continue
                if (v.size < 3) continue
                val x = v[0].toFloat(); val y = v[1].toFloat()
                if (x < bitmap.width * .08f || x > bitmap.width * .92f || y < bitmap.height * .13f || y > bitmap.height * .90f) continue
                found.add(AimPoint(x, y))
            }
            if (found.size < 2) return null
            val strikerIndex = found.indices.maxByOrNull { found[it].y } ?: return null
            val striker = found[strikerIndex]
            if (striker.y < bitmap.height * .62f) return null
            val coins = found.indices.filter { it != strikerIndex }.map { found[it] }
            if (coins.isEmpty()) return null
            val pockets = listOf(
                AimPoint(bitmap.width * .105f, bitmap.height * .17f),
                AimPoint(bitmap.width * .895f, bitmap.height * .17f),
                AimPoint(bitmap.width * .105f, bitmap.height * .855f),
                AimPoint(bitmap.width * .895f, bitmap.height * .855f)
            )
            val radius = (bitmap.width / 55f).coerceAtLeast(8f)
            var best: AimResult? = null
            var bestScore = Float.MAX_VALUE
            for (coin in coins) for (pocket in pockets) {
                val dx = pocket.x - coin.x; val dy = pocket.y - coin.y
                val length = hypot(dx, dy).coerceAtLeast(1f)
                val contact = AimPoint(coin.x - dx / length * radius * 2.05f, coin.y - dy / length * radius * 2.05f)
                if (contact.x !in 0f..bitmap.width.toFloat() || contact.y !in 0f..bitmap.height.toFloat()) continue
                val shotLength = hypot(contact.x - striker.x, contact.y - striker.y)
                val blocked = coins.any { other -> other != coin && distanceToSegment(other, striker, contact) < radius * 1.8f }
                val score = shotLength + length * .42f + if (blocked) bitmap.width * .8f else 0f
                if (score < bestScore) {
                    bestScore = score
                    best = AimResult(striker, coin, pocket, contact, found.size, if (blocked) .25f else .55f)
                }
            }
            return best
        } finally {
            rgba.release(); gray.release(); circles.release()
        }
    }

    private fun distanceToSegment(p: AimPoint, a: AimPoint, b: AimPoint): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val denom = dx * dx + dy * dy
        if (denom <= .001f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / denom).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
}

class AimOverlayView(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 235, 135); strokeWidth = 5f; style = Paint.Style.STROKE }
    private val target = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; strokeWidth = 4f; style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; setShadowLayer(5f, 1f, 1f, Color.BLACK) }
    @Volatile private var result: AimResult? = null
    fun update(value: AimResult?) { result = value; postInvalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shot = result
        if (shot == null) { canvas.drawText("Carrom Aim Pro • detecting board…", 24f, 72f, label); return }
        canvas.drawLine(shot.striker.x, shot.striker.y, shot.contact.x, shot.contact.y, line)
        canvas.drawLine(shot.coin.x, shot.coin.y, shot.pocket.x, shot.pocket.y, target)
        canvas.drawCircle(shot.coin.x, shot.coin.y, 24f, target)
        canvas.drawCircle(shot.pocket.x, shot.pocket.y, 20f, line)
        canvas.drawText("Estimated shot • " + shot.detectedCoins + " circles", 24f, 72f, label)
        canvas.drawText("Experimental • verify before shooting", 24f, 112f, label)
    }
}
