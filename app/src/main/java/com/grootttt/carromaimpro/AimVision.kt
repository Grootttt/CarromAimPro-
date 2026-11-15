package com.grootttt.carromaimpro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class AimPoint(val x: Float, val y: Float)
data class DetectedDisc(val point: AimPoint, val radius: Float)
data class ShotSuggestion(val coin: AimPoint, val pocket: AimPoint, val contact: AimPoint, val score: Float)
data class AimResult(
    val striker: AimPoint,
    val coin: AimPoint,
    val pocket: AimPoint,
    val contact: AimPoint,
    val detectedCoins: Int,
    val confidence: Float,
    val board: RectF,
    val coins: List<DetectedDisc>,
    val alternatives: List<ShotSuggestion>
)

object CarromVision {
    fun analyze(bitmap: android.graphics.Bitmap): AimResult? {
        if (!OpenCVLoader.initLocal()) return null
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val woodMask = Mat()
        val gray = Mat()
        val circles = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

            // The board is the large, nearly-square warm wood-coloured region.
            Core.inRange(hsv, Scalar(3.0, 35.0, 55.0), Scalar(35.0, 255.0, 255.0), woodMask)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
            Imgproc.morphologyEx(woodMask, woodMask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(woodMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var boardX = bitmap.width * .035f
            var boardY = bitmap.height * .20f
            var boardSize = min(bitmap.width * .93f, bitmap.height * .62f)
            var boardW = boardSize
            var boardH = boardSize
            var largestArea = 0.0
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                val w = rect.width.toFloat()
                val h = rect.height.toFloat()
                val aspect = w / h.coerceAtLeast(1f)
                val area = w * h
                if (aspect in .78f..1.22f && area > bitmap.width * bitmap.height * .16 &&
                    rect.x > bitmap.width * .005 && rect.x + rect.width < bitmap.width * .995 &&
                    area > largestArea) {
                    largestArea = area
                    boardX = rect.x.toFloat()
                    boardY = rect.y.toFloat()
                    boardW = w
                    boardH = h
                }
            }
            // Avoid accepting a full-screen wood-coloured background as the board.
            if (largestArea == 0.0) {
                boardSize = min(bitmap.width * .93f, bitmap.height * .62f)
                boardW = boardSize
                boardH = boardSize
                boardX = (bitmap.width - boardW) / 2f
                boardY = (bitmap.height * .56f - boardH / 2f).coerceIn(0f, bitmap.height - boardH)
            }

            val left = boardX.toInt().coerceIn(0, bitmap.width - 1)
            val top = boardY.toInt().coerceIn(0, bitmap.height - 1)
            val right = (boardX + boardW).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = (boardY + boardH).toInt().coerceIn(top + 1, bitmap.height)
            val roi = rgb.submat(top, bottom, left, right)
            try {
                Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGB2GRAY)
                Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.1)
                val boardMin = min(right - left, bottom - top).toDouble()
                Imgproc.HoughCircles(
                    gray, circles, Imgproc.HOUGH_GRADIENT, 1.15,
                    max(12.0, boardMin * .038), 90.0, 18.0,
                    max(6, (boardMin / 105.0).toInt()),
                    max(12, (boardMin / 25.0).toInt())
                )
            } finally {
                roi.release()
            }

            if (circles.empty()) return null
            val candidates = ArrayList<DetectedDisc>()
            val minX = boardX + boardW * .055f
            val maxX = boardX + boardW * .945f
            val minY = boardY + boardH * .055f
            val maxY = boardY + boardH * .945f
            for (i in 0 until circles.cols()) {
                val v = circles.get(0, i) ?: continue
                if (v.size < 3) continue
                val x = left + v[0].toFloat()
                val y = top + v[1].toFloat()
                val radius = v[2].toFloat()
                if (x !in minX..maxX || y !in minY..maxY) continue
                if (candidates.none { hypot(it.point.x - x, it.point.y - y) < radius * .85f }) {
                    candidates.add(DetectedDisc(AimPoint(x, y), radius))
                }
            }
            if (candidates.size < 2) return null

            // In Carrom Pool the player shoots from the bottom edge. Prefer a larger
            // disc in the lower striker lane; otherwise use the lowest detected disc.
            val strikerLane = candidates.filter { it.point.y > boardY + boardH * .77f }
            val strikerDisc = strikerLane.maxByOrNull { it.radius * 1.4f + (it.point.y - boardY) / boardH * 8f }
                ?: candidates.maxByOrNull { it.point.y } ?: return null
            val striker = strikerDisc.point
            val coins = candidates.filter { it !== strikerDisc }
            if (coins.isEmpty()) return null

            val pockets = listOf(
                AimPoint(boardX + boardW * .075f, boardY + boardH * .075f),
                AimPoint(boardX + boardW * .925f, boardY + boardH * .075f),
                AimPoint(boardX + boardW * .075f, boardY + boardH * .925f),
                AimPoint(boardX + boardW * .925f, boardY + boardH * .925f)
            )
            val typicalRadius = coins.map { it.radius }.sorted()[coins.size / 2]
            val radius = typicalRadius.coerceAtLeast(boardW * .012f)
            val suggestions = ArrayList<ShotSuggestion>()
            for (disc in coins) for (pocket in pockets) {
                val coin = disc.point
                val dx = pocket.x - coin.x
                val dy = pocket.y - coin.y
                val length = hypot(dx, dy).coerceAtLeast(1f)
                val contact = AimPoint(
                    coin.x - dx / length * radius * 2.05f,
                    coin.y - dy / length * radius * 2.05f
                )
                if (contact.x !in minX..maxX || contact.y !in minY..maxY) continue
                val shotLength = hypot(contact.x - striker.x, contact.y - striker.y)
                val blocked = coins.any { other ->
                    other !== disc && distanceToSegment(other.point, striker, contact) < radius * 1.7f
                }
                val score = shotLength + length * .35f + if (blocked) boardW * .9f else 0f
                suggestions.add(ShotSuggestion(coin, pocket, contact, score))
            }
            val ranked = suggestions.sortedBy { it.score }.take(3)
            val best = ranked.firstOrNull() ?: return null
            return AimResult(
                striker, best.coin, best.pocket, best.contact, coins.size,
                if (ranked.isNotEmpty()) .45f else .2f,
                RectF(boardX, boardY, boardX + boardW, boardY + boardH),
                coins, ranked
            )
        } finally {
            rgba.release(); rgb.release(); hsv.release(); woodMask.release()
            gray.release(); circles.release(); hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun distanceToSegment(p: AimPoint, a: AimPoint, b: AimPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val denom = dx * dx + dy * dy
        if (denom <= .001f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / denom).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
}

class AimOverlayView(context: Context) : View(context) {
    private val mainLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 235, 135); strokeWidth = 6f; style = Paint.Style.STROKE
    }
    private val altLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 190, 255); strokeWidth = 3f; style = Paint.Style.STROKE
    }
    private val coinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; strokeWidth = 4f; style = Paint.Style.STROKE
    }
    private val strikerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN; strokeWidth = 5f; style = Paint.Style.STROKE
    }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255); strokeWidth = 3f; style = Paint.Style.STROKE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 32f; setShadowLayer(5f, 1f, 1f, Color.BLACK)
    }
    @Volatile private var result: AimResult? = null
    fun update(value: AimResult?) { result = value; postInvalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shot = result
        if (shot == null) {
            canvas.drawText("Carrom Aim Pro • finding board and discs…", 24f, 72f, label)
            return
        }
        canvas.drawRect(shot.board, boardPaint)
        shot.coins.forEach { canvas.drawCircle(it.point.x, it.point.y, it.radius + 5f, coinPaint) }
        canvas.drawCircle(shot.striker.x, shot.striker.y, 1.5f * (shot.coins.map { it.radius }.sorted().lastOrNull() ?: 18f), strikerPaint)
        shot.alternatives.drop(1).forEach { alt ->
            canvas.drawLine(shot.striker.x, shot.striker.y, alt.contact.x, alt.contact.y, altLine)
            canvas.drawLine(alt.coin.x, alt.coin.y, alt.pocket.x, alt.pocket.y, altLine)
        }
        canvas.drawLine(shot.striker.x, shot.striker.y, shot.contact.x, shot.contact.y, mainLine)
        canvas.drawLine(shot.coin.x, shot.coin.y, shot.pocket.x, shot.pocket.y, coinPaint)
        canvas.drawCircle(shot.coin.x, shot.coin.y, 24f, coinPaint)
        canvas.drawCircle(shot.pocket.x, shot.pocket.y, 20f, mainLine)
        canvas.drawText("Coins: ${shot.detectedCoins} • Best pot + 2 alternatives", 24f, 72f, label)
        canvas.drawText("Green: recommended • Blue: alternatives", 24f, 112f, label)
        canvas.drawText("Experimental guidance — verify the shot", 24f, 152f, label)
    }
}
