package fr.hugo.paliers

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private class Row(
        val view: LinearLayout,
        val minIn: EditText,
        val vibIn: EditText,
        val testBtn: Button,
        val delBtn: Button
    )

    private lateinit var prefs: Prefs
    private lateinit var big: TextView
    private lateinit var info: TextView
    private lateinit var rowsBox: LinearLayout
    private lateinit var addBtn: Button
    private lateinit var soundCb: CheckBox
    private lateinit var goBtn: Button
    private val rows = mutableListOf<Row>()

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        big = TextView(this).apply { textSize = 64f; typeface = Typeface.DEFAULT_BOLD }
        info = TextView(this).apply { textSize = 16f }
        val head = TextView(this).apply {
            text = "Paliers : minutes depuis le départ, vibrations"
            textSize = 15f
            setPadding(0, pad, 0, pad / 4)
        }
        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addBtn = Button(this).apply {
            text = "Ajouter un palier"
            setOnClickListener {
                val last = rows.lastOrNull()?.minIn?.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
                addRow(Mark((last ?: 0.0) + 1.0, 5))
            }
        }
        soundCb = CheckBox(this).apply { text = "Bip en plus de la vibration"; isChecked = prefs.sound }
        goBtn = Button(this).apply { setOnClickListener { toggle() } }

        root.addView(big)
        root.addView(info)
        root.addView(head)
        root.addView(rowsBox)
        root.addView(addBtn)
        root.addView(soundCb)
        root.addView(goBtn)
        setContentView(ScrollView(this).apply { addView(root) })

        parseMarks(prefs.marks, prefs.nVib).forEach { addRow(it) }
    }

    private fun addRow(m: Mark) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val minIn = EditText(this).apply {
            setText(fmtMin(m.min))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = Gravity.END
        }
        val vibIn = EditText(this).apply {
            setText(m.vib.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.END
        }
        fun unit(t: String) = TextView(this).apply { text = t; setPadding(8, 0, 16, 0) }
        val testBtn = Button(this).apply { text = "▶" }
        val delBtn = Button(this).apply { text = "✕" }
        val r = Row(row, minIn, vibIn, testBtn, delBtn)

        testBtn.setOnClickListener {
            val v = vibIn.text.toString().toIntOrNull()?.coerceIn(1, 30)
            if (v == null) toast("Nombre de vibrations invalide")
            else { prefs.sound = soundCb.isChecked; RingService.test(this, v) }
        }
        delBtn.setOnClickListener {
            rowsBox.removeView(row)
            rows.remove(r)
        }

        row.addView(minIn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(unit("min"))
        row.addView(vibIn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(unit("vib."))
        row.addView(testBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        row.addView(delBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        rowsBox.addView(row)
        rows.add(r)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    /** Lit et valide les lignes, les trie, les enregistre. Null si une ligne est invalide. */
    private fun saveSettings(): List<Mark>? {
        val list = mutableListOf<Mark>()
        for (r in rows) {
            val m = r.minIn.text.toString().replace(',', '.').toDoubleOrNull()
            val v = r.vibIn.text.toString().toIntOrNull()
            if (m == null || m <= 0 || v == null || v < 1 || v > 30) {
                toast("Ligne invalide : minutes > 0, vibrations entre 1 et 30")
                return null
            }
            list.add(Mark(m, v))
        }
        val marks = list.distinctBy { it.min }.sortedBy { it.min }
        if (marks.isEmpty()) { toast("Ajoute au moins un palier"); return null }
        prefs.marks = marksToString(marks)
        prefs.sound = soundCb.isChecked
        rowsBox.removeAllViews()
        rows.clear()
        marks.forEach { addRow(it) }
        return marks
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun toggle() {
        if (prefs.startAt > 0) {
            Scheduler.cancel(this)
            refresh()
            return
        }
        val marks = saveSettings() ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(this, "Autorise les alarmes exactes, puis relance", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
                return
            }
        }
        Scheduler.start(this, marks)
        refresh()
    }

    private fun setEditable(on: Boolean) {
        rows.forEach { it.minIn.isEnabled = on; it.vibIn.isEnabled = on; it.delBtn.isEnabled = on }
        addBtn.isEnabled = on
        soundCb.isEnabled = on
    }

    private fun refresh() {
        val start = prefs.startAt
        val marks = parseMarks(prefs.runMarks, prefs.nVib)
        if (start > 0 && marks.isNotEmpty()) {
            val e = System.currentTimeMillis() - start
            val next = marks.firstOrNull { it.min * 60_000 > e }
            if (next != null) {
                big.text = fmtClock((next.min * 60_000).toLong() - e)
                info.text = "Prochain palier : ${fmtMin(next.min)} min, ${next.vib} vibrations. " +
                        "Écoulé : ${fmtClock(e)}.\nTu peux verrouiller l'écran."
                goBtn.text = "Annuler"
                setEditable(false)
                return
            }
            prefs.startAt = 0L
        }
        big.text = "--:--"
        info.text = "Prêt."
        goBtn.text = "Démarrer"
        setEditable(true)
    }
}
