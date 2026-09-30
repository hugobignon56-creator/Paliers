package fr.hugo.paliers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getBooleanExtra("last", false)) Prefs(ctx).startAt = 0L
        val svc = Intent(ctx, RingService::class.java).putExtras(intent)
        try {
            // Une alarme exacte autorise le démarrage d'un service de premier plan depuis l'arrière-plan.
            ctx.startForegroundService(svc)
        } catch (e: Exception) {
            // Secours : vibration seule, directement depuis le récepteur.
            Ringer.vibrate(ctx, intent.getIntExtra("vib", Prefs(ctx).nVib))
        }
    }
}
