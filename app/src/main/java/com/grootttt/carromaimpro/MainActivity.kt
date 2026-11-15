package com.grootttt.carromaimpro

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private val captureRequest = 4102
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(ScreenCaptureService.EXTRA_PATH) ?: return
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap != null) {
                preview.setImageBitmap(bitmap)
                preview.visibility = View.VISIBLE
                status.text = "Live screen feed active • latest frame updated. Coin detection is the next development step."
            } else {
                status.text = "Capture saved, but preview could not be opened."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(ScreenCaptureService.ACTION_CAPTURED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(captureReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(captureReceiver, filter)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(24, 27, 32))
        }
        val heading = TextView(this).apply {
            text = "CARROM AIM PRO • LIVE"
            textSize = 21f
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 8)
        }
        val help = TextView(this).apply {
            text = "Tap Capture and approve screen sharing. Then switch to Carrom Disc Pool; this app will keep updating the latest screen frame until you stop sharing in Android."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(4, 0, 4, 6)
        }
        status = TextView(this).apply {
            text = "Ready • Start screen sharing, then switch to Disc Pool"
            textSize = 13f
            setTextColor(Color.rgb(120, 220, 170))
            setPadding(4, 6, 4, 6)
        }
        val capture = Button(this).apply {
            text = "CAPTURE GAME SCREEN"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                @Suppress("DEPRECATION")
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        }
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
        }
        val boardTitle = TextView(this).apply {
            text = "SHOT PLANNER • Set S (striker), C (coin), P (pocket)"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(4, 8, 4, 4)
        }
        val boardStatus = TextView(this).apply {
            text = "Tap the board to place S, C and P; drag to adjust."
            textSize = 12f
            setTextColor(Color.rgb(120, 220, 170))
        }
        val board = AimBoardView(this) { step ->
            boardStatus.text = when (step) {
                0 -> "Tap to place striker (S)"
                1 -> "Tap to place target coin (C)"
                2 -> "Tap to place pocket (P)"
                else -> "Ready • Drag S, C or P to fine-tune"
            }
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply {
            text = "Reset"
            setOnClickListener { board.reset() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = "Direct / Bank"
            setOnClickListener { board.toggleBank() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val note = TextView(this).apply {
            text = "Continuous screen preview only at this stage. Automatic coin/pocket detection and aiming guidance are not implemented yet."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 4)
        }
        root.addView(heading)
        root.addView(help)
        root.addView(capture)
        root.addView(status)
        root.addView(preview, LinearLayout.LayoutParams(-1, -2))
        root.addView(boardTitle)
        root.addView(boardStatus)
        root.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls)
        root.addView(note)
        setContentView(root)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "Screen sharing started • switch to Carrom Disc Pool now. Keep sharing active while playing."
        } else if (requestCode == captureRequest) {
            status.text = "Screen capture cancelled. Tap Capture to try again."
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}

private data class Point(val x: Float, val y: Float)

private class AimBoardView(context: Context, private val onStep: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var striker: Point? = null
    private var coin: Point? = null
    private var pocket: Point? = null
    private var bankMode = false
    private var dragging = -1

    fun reset() { striker = null; coin = null; pocket = null; dragging = -1; onStep(0); invalidate() }
    fun toggleBank() { bankMode = !bankMode; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height) * .92f
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val right = left + side
        val bottom = top + side
        paint.color = Color.rgb(211, 163, 103)
        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, paint)
        val inset = side * .075f
        val l = left + inset; val t = top + inset; val r = right - inset; val b = bottom - inset
        paint.color = Color.rgb(244, 222, 183); canvas.drawRect(l, t, r, b, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        paint.color = Color.rgb(115, 65, 38); canvas.drawRect(l, t, r, b, paint); paint.style = Paint.Style.FILL
        val pr = side * .035f
        listOf(Point(l,t), Point(r,t), Point(l,b), Point(r,b)).forEach { paint.color = Color.BLACK; canvas.drawCircle(it.x,it.y,pr,paint) }
        paint.color = Color.rgb(170,105,55); canvas.drawCircle(width/2f,height/2f,side*.12f,paint)
        paint.color = Color.rgb(244,222,183); canvas.drawCircle(width/2f,height/2f,side*.105f,paint)
        val s=striker; val c=coin; val p=pocket
        if (s!=null && c!=null) {
            paint.color=Color.rgb(30,110,220); paint.strokeWidth=4f; canvas.drawLine(s.x,s.y,c.x,c.y,paint)
            if (p!=null) {
                paint.color=Color.rgb(30,160,100); canvas.drawLine(c.x,c.y,p.x,p.y,paint)
                if (bankMode) drawBankPath(canvas,c,p,l,t,r,b)
            }
        }
        drawPoint(canvas,s,Color.rgb(30,110,220),"S")
        drawPoint(canvas,c,Color.rgb(220,65,55),"C")
        drawPoint(canvas,p,Color.rgb(30,160,100),"P")
        paint.color=Color.DKGRAY; paint.textSize=12f
        canvas.drawText(if(bankMode) "BANK GUIDE" else "DIRECT GUIDE",l+8,b-10,paint)
    }

    private fun drawPoint(canvas: Canvas, point: Point?, color: Int, label: String) {
        if(point==null)return
        paint.color=color; canvas.drawCircle(point.x,point.y,15f,paint)
        paint.color=Color.WHITE; paint.textSize=11f; paint.textAlign=Paint.Align.CENTER
        canvas.drawText(label,point.x,point.y+4f,paint); paint.textAlign=Paint.Align.LEFT
    }

    private fun drawBankPath(canvas: Canvas, coin: Point, pocket: Point, l: Float, t: Float, r: Float, b: Float) {
        val targets=listOf(Point(2*r-pocket.x,pocket.y) to 0,Point(2*l-pocket.x,pocket.y) to 1,Point(pocket.x,2*b-pocket.y) to 2,Point(pocket.x,2*t-pocket.y) to 3)
        for((target,side) in targets) {
            val dx=target.x-coin.x; val dy=target.y-coin.y
            if(side<=1) {
                if(kotlin.math.abs(dx)<.001f)continue
                val wall=if(side==0)r else l; val f=(wall-coin.x)/dx; val hit=Point(wall,coin.y+dy*f)
                if(f !in 0f..1f || hit.y !in t..b)continue
                paint.color=Color.rgb(145,65,180); paint.strokeWidth=3f
                canvas.drawLine(coin.x,coin.y,hit.x,hit.y,paint); canvas.drawLine(hit.x,hit.y,pocket.x,pocket.y,paint); canvas.drawCircle(hit.x,hit.y,6f,paint); break
            } else {
                if(kotlin.math.abs(dy)<.001f)continue
                val wall=if(side==2)b else t; val f=(wall-coin.y)/dy; val hit=Point(coin.x+dx*f,wall)
                if(f !in 0f..1f || hit.x !in l..r)continue
                paint.color=Color.rgb(145,65,180); paint.strokeWidth=3f
                canvas.drawLine(coin.x,coin.y,hit.x,hit.y,paint); canvas.drawLine(hit.x,hit.y,pocket.x,pocket.y,paint); canvas.drawCircle(hit.x,hit.y,6f,paint); break
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val radius=42f
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touch=Point(event.x,event.y); val points=listOf(striker,coin,pocket)
                dragging=points.indices.filter{points[it]!=null}.minByOrNull{ i -> val q=points[i]!!; kotlin.math.hypot((touch.x-q.x).toDouble(),(touch.y-q.y).toDouble()) }?.takeIf{i -> val q=points[i]!!; kotlin.math.hypot((touch.x-q.x).toDouble(),(touch.y-q.y).toDouble())<=radius} ?: -1
                if(dragging>=0) movePoint(dragging,touch) else {
                    val next=when { striker==null->0; coin==null->1; pocket==null->2; else->{striker=null;coin=null;pocket=null;0} }
                    movePoint(next,touch); onStep(if(next==0)1 else if(next==1)2 else 3)
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> { if(dragging>=0){movePoint(dragging,Point(event.x,event.y));invalidate()}; return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging=-1; performClick(); return true }
        }
        return true
    }

    private fun movePoint(index:Int, point:Point) {
        val side=minOf(width,height)*.92f; val left=(width-side)/2f; val top=(height-side)/2f; val inset=side*.075f
        val adjusted=Point(point.x.coerceIn(left+inset,left+side-inset),point.y.coerceIn(top+inset,top+side-inset))
        when(index){0->striker=adjusted;1->coin=adjusted;2->pocket=adjusted}
    }
    override fun performClick():Boolean { super.performClick(); return true }
}
