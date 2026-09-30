package fr.hugo.paliers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
    private const val BASE_CODE = 100
    private const val MAX_MARKS = 50

    private fun alarmIntent(ctx: Context, i: Int, mark: Mark?, last: Boolean, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, BASE_CODE + i,
            Intent(ctx, AlarmReceiver::class.java)
                .putExtra("min", mark?.min ?: 0.0)
                .putExtra("vib", mark?.vib ?: 5)
                .putExtra("last", last),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    fun start(ctx: Context, marks: List<Mark>) {
        cancel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val t0 = System.currentTimeMillis()
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        marks.take(MAX_MARKS).forEachIndexed { i, m ->
            val at = t0 + (m.min * 60_000).toLong()
            val pi = alarmIntent(ctx, i, m, i == marks.lastIndex, PendingIntent.FLAG_UPDATE_CURRENT)!!
            // setAlarmClock : alarme exacte, traverse le mode Doze et l'écran verrouillé.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        }
        val p = Prefs(ctx)
        p.runMarks = marksToString(marks)
        p.startAt = t0
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_MARKS) {
            val pi = alarmIntent(ctx, i, null, false, PendingIntent.FLAG_NO_CREATE) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
        Prefs(ctx).startAt = 0L
    }
}
