package fr.hugo.paliers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null

    companion object {
        private const val CHANNEL = "paliers_alarme"
        private const val ACTION_STOP = "fr.hugo.paliers.STOP"

        fun test(ctx: Context, vib: Int) {
            ctx.startForegroundService(
                Intent(ctx, RingService::class.java).putExtra("min", -1.0).putExtra("vib", vib)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = Prefs(this)
        val n = (intent?.getIntExtra("vib", prefs.nVib) ?: prefs.nVib).coerceIn(1, 30)
        val min = intent?.getDoubleExtra("min", 0.0) ?: 0.0
        val last = intent?.getBooleanExtra("last", false) ?: false
        val title = when {
            min < 0 -> "Test de sonnerie"
            last -> "Dernier palier : ${fmtMin(min)} min"
            else -> "Palier ${fmtMin(min)} min"
        }
        goForeground(title, n)

        handler.removeCallbacksAndMessages(null)
        Ringer.vibrate(this, n)
        if (prefs.sound) beep(n)
        handler.postDelayed({ stopSelf() }, ringDurationMs(n) + 300)
        return START_NOT_STICKY
    }

    private fun beep(n: Int) {
        if (tone == null) tone = try { ToneGenerator(AudioManager.STREAM_ALARM, 90) } catch (e: Exception) { null }
        val tg = tone ?: return
        for (k in 0 until n) {
            handler.postDelayed({
                tg.startTone(ToneGenerator.TONE_DTMF_D, PULSE_MS.toInt())
            }, k * (PULSE_MS + GAP_MS))
        }
    }

    private fun goForeground(title: String, n: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Sonnerie des paliers", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)       // le son et la vibration sont gérés par l'appli
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)

        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, RingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("S'arrête seule après $n vibrations")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openPi)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Couper", stopPi
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(1, notif)
        }
    }

    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Ringer.stop(this)
        tone?.release()
        tone = null
        super.onDestroy()
    }
}
