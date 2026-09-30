package fr.hugo.paliers

import android.content.Context
import kotlin.math.ceil

const val PULSE_MS = 600L
const val GAP_MS = 400L

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("paliers", Context.MODE_PRIVATE)

    var marks: String
        get() = sp.getString("marks", "5:5, 10:5, 15:5, 18:5")!!
        set(v) = sp.edit().putString("marks", v).apply()
    var nVib: Int
        get() = sp.getInt("nVib", 5)
        set(v) = sp.edit().putInt("nVib", v).apply()
    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()
    /** Heure de départ du cycle en cours (0 = aucun cycle). */
    var startAt: Long
        get() = sp.getLong("startAt", 0L)
        set(v) = sp.edit().putLong("startAt", v).apply()
    /** Paliers figés au lancement du cycle. */
    var runMarks: String
        get() = sp.getString("runMarks", "")!!
        set(v) = sp.edit().putString("runMarks", v).apply()
}

/** Un palier : instant (min depuis le départ) et nombre de vibrations. */
data class Mark(val min: Double, val vib: Int)

/** Format "minutes:vibrations" séparés par des virgules. Sans ":", on prend defVib (compatible ancien format). */
fun parseMarks(s: String, defVib: Int = 5): List<Mark> =
    s.split(Regex("[,;]+"))
        .mapNotNull { tok ->
            val p = tok.trim().split(":")
            val m = p[0].trim().replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val v = p.getOrNull(1)?.trim()?.toIntOrNull() ?: defVib
            if (m > 0) Mark(m, v.coerceIn(1, 30)) else null
        }
        .distinctBy { it.min }
        .sortedBy { it.min }

fun marksToString(l: List<Mark>): String =
    l.joinToString(", ") { "${fmtMin(it.min)}:${it.vib}" }

fun fmtMin(m: Double): String =
    if (m % 1.0 == 0.0) m.toLong().toString() else m.toString()

fun fmtClock(ms: Long): String {
    val t = ceil(ms.coerceAtLeast(0) / 1000.0).toLong()
    return "%02d:%02d".format(t / 60, t % 60)
}

/** Motif : vibration, pause, vibration... puis arrêt (aucune répétition). */
fun vibPattern(n: Int): LongArray {
    val a = LongArray(n * 2)
    for (k in 0 until n) {
        a[2 * k] = if (k == 0) 0L else GAP_MS
        a[2 * k + 1] = PULSE_MS
    }
    return a
}

fun ringDurationMs(n: Int): Long = n * PULSE_MS + (n - 1) * GAP_MS
