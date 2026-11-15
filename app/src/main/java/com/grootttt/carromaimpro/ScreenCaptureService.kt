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
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
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
                    // Keep the stream active, but save at most one preview frame per second.
                    if (now - lastSavedAt >= 900L) {
                        val plane = image.planes[0]
                        val pixelStride = plane.pixelStride
                        val rowPadding = plane.rowStride - pixelStride * width
                        val paddedWidth = width + rowPadding / pixelStride
                        val full = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                        full.copyPixelsFromBuffer(plane.buffer)
                        val bitmap = Bitmap.createBitmap(full, 0, 0, width, height)
                        full.recycle()
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

    private fun notification(): Notification {
        val channelId = "carrom_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Carrom screen capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("Carrom Aim Pro • Screen analysis")
            .setContentText("Screen sharing is active. Switch to Disc Pool; stop sharing from Android when finished.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build()
    }

    private fun stopCapture() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        reader?.close(); reader = null
        thread?.quitSafely(); thread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        reader?.close(); reader = null
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
