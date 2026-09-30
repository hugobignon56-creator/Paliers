package fr.hugo.paliers

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Ringer {
    private fun vibrator(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else
            @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)

    /** N impulsions puis arrêt automatique (repeat = -1). Usage ALARME : vibre aussi en mode silencieux. */
    fun vibrate(ctx: Context, n: Int) {
        val v = vibrator(ctx)
        val effect = VibrationEffect.createWaveform(vibPattern(n), -1)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            val aa = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            @Suppress("DEPRECATION") v.vibrate(effect, aa)
        }
    }

    fun stop(ctx: Context) = vibrator(ctx).cancel()
}
