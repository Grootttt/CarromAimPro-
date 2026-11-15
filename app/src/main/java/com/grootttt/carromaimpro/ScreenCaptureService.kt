package com.grootttt.carromaimpro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var aimOverlay: AimOverlayView? = null
    private var windowManager: WindowManager? = null
    private var lastSavedAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                   else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA) as? Intent
        if (data == null || code == 0) { stopSelf(); return START_NOT_STICKY }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            if (Settings.canDrawOverlays(this)) attachAimOverlay()
            thread = HandlerThread("CarromScreenCapture").also { it.start() }
            val handler = Handler(thread!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { cleanup() }
            }, handler)
            display = projection?.createVirtualDisplay(
                "CarromAimProCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, handler
            )
            reader?.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastSavedAt >= 900L) {
                        val plane = image.planes[0]
                        val pixelStride = plane.pixelStride
                        val rowPadding = plane.rowStride - pixelStride * width
                        val paddedWidth = width + rowPadding / pixelStride
                        val full = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                        full.copyPixelsFromBuffer(plane.buffer)
                        val bitmap = Bitmap.createBitmap(full, 0, 0, width, height)
                        full.recycle()

                        // On-device experimental circle detection and manual shot geometry.
                        try { aimOverlay?.update(CarromVision.analyze(bitmap)) }
                        catch (_: Exception) { aimOverlay?.update(null) }

                        val file = File(cacheDir, "latest-disc-pool-screen.jpg")
                        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                        bitmap.recycle()
                        lastSavedAt = now
                        sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName).putExtra(EXTRA_PATH, file.absolutePath))
                    }
                } catch (_: Exception) {
                    sendBroadcast(Intent(ACTION_FAILED).setPackage(packageName))
                } finally {
                    image.close()
                }
            }, handler)
        } catch (_: Exception) {
            sendBroadcast(Intent(ACTION_FAILED).setPackage(packageName))
            stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun attachAimOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                       else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            aimOverlay = AimOverlayView(this)
            windowManager?.addView(aimOverlay, params)
        } catch (_: Exception) {
            aimOverlay = null
        }
    }

    private fun removeAimOverlay() {
        try { aimOverlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        aimOverlay = null
        windowManager = null
    }

    private fun notification(): Notification {
        val channelId = "carrom_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Carrom screen capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        val overlayText = if (Settings.canDrawOverlays(this)) "Live capture and experimental aim overlay active."
                           else "Screen capture active. Enable Display over other apps for aim overlay."
        return builder.setContentTitle("Carrom Aim Pro • Screen analysis")
            .setContentText(overlayText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build()
    }

    private fun stopCapture() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        reader?.close(); reader = null
        removeAimOverlay()
        thread?.quitSafely(); thread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        reader?.close(); reader = null
        removeAimOverlay()
        thread?.quitSafely(); thread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CAPTURED = "com.grootttt.carromaimpro.CAPTURED"
        const val ACTION_FAILED = "com.grootttt.carromaimpro.CAPTURE_FAILED"
        const val EXTRA_PATH = "capture_path"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        private const val NOTIFICATION_ID = 410
    }
}
