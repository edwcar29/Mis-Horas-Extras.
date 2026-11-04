package com.mishorasextras.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Entry(
    val id: Long, val date: String, val start: String, val end: String,
    val holiday: Boolean, val note: String, val h50: Double, val h100: Double
)

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hours_records", MODE_PRIVATE) }
    private val entries = mutableListOf<Entry>()
    private lateinit var dateText: TextView
    private lateinit var weekdayText: TextView
    private lateinit var startText: TextView
    private lateinit var endText: TextView
    private lateinit var holidayBox: CheckBox
    private lateinit var noteInput: EditText
    private lateinit var live50: TextView
    private lateinit var live100: TextView
    private lateinit var monthText: TextView
    private lateinit var summary50: TextView
    private lateinit var summary100: TextView
    private lateinit var summaryTotal: TextView
    private lateinit var history: LinearLayout
    private var selectedDate = LocalDate.now()
    private var selectedMonth = LocalDate.now().withDayOfMonth(1)
    private var startTime = LocalTime.of(8, 30)
    private var endTime = LocalTime.of(19, 30)

    private val csvCreate = registerForActivityResultCompat()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadEntries()
        buildUi()
        refresh()
    }

    // Uses Android's native document picker (Storage Access Framework).
    private fun registerForActivityResultCompat() = 41

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 20)
            setBackgroundColor(Color.WHITE)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        content.addView(title("📝 Registrar jornada", 23))
        dateText = valueButton(selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) { chooseDate() }
        content.addView(label("Fecha")); content.addView(dateText)
        weekdayText = textValue("")
        content.addView(label("Día de la semana")); content.addView(weekdayText)

        content.addView(label("Hora de entrada"))
        startText = valueButton(startTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale("es","EC")))) { chooseTime(true) }
        content.addView(startText)
        content.addView(label("Hora de salida real"))
        endText = valueButton(endTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale("es","EC")))) { chooseTime(false) }
        content.addView(endText)

        holidayBox = CheckBox(this).apply { text = "Feriado o descanso obligatorio"; setTextColor(Color.BLACK); setOnCheckedChangeListener { _, _ -> refreshLive() } }
        content.addView(holidayBox)
        content.addView(label("Observaciones (opcional)"))
        noteInput = EditText(this).apply { hint = "Ej. Apoyo en inventario, cierre de mes..."; minLines = 2; gravity = Gravity.TOP; setTextColor(Color.BLACK) }
        content.addView(noteInput)
        content.addView(TextView(this).apply {
            text = "Sábados, domingos y feriados: todas las horas de la jornada ingresada se calculan al 100%. De lunes a viernes, el sobretiempo comienza a las 17:30."
            setTextColor(Color.GRAY); textSize = 14f; setPadding(0, 12, 0, 8)
        })
        val liveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val card50 = card("#DFF7E9", "#166534", "Horas al 50%").also { live50 = it.second }
        val card100 = card("#E1EDFF", "#1D4ED8", "Horas al 100%").also { live100 = it.second }
        liveRow.addView(card50.first, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 8 })
        liveRow.addView(card100.first, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 8 })
        content.addView(liveRow)
        content.addView(actionButton("＋ Guardar registro", "#2563EB") { addEntry() })

        content.addView(title("📊 Resumen mensual", 21))
        monthText = valueButton(selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es","EC")))) { chooseMonth() }
        content.addView(label("Mes")); content.addView(monthText)
        val sumRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val s50 = card("#DFF7E9", "#166534", "Total al 50%").also { summary50 = it.second }
        val s100 = card("#E1EDFF", "#1D4ED8", "Total al 100%").also { summary100 = it.second }
        sumRow.addView(s50.first, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 8 })
        sumRow.addView(s100.first, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 8 })
        content.addView(sumRow)
        val totalCard = card("#F0E5FF", "#6B21A8", "Total de sobretiempo").also { summaryTotal = it.second }
        content.addView(totalCard.first)
        val export = actionButton("📄 Exportar CSV", "#FFFFFF") { exportCsv() }
        export.setTextColor(Color.BLACK)
        content.addView(export)
        content.addView(actionButton("🗑️ Borrar registros del mes", "#FFFFFF") { clearMonth() }.apply { setTextColor(Color.BLACK) })
        content.addView(title("📋 Historial del mes", 21))
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(history)
    }

    private fun title(s: String, size: Int) = TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(Color.BLACK); setPadding(0, 10, 0, 12) }
    private fun label(s: String) = TextView(this).apply { text=s; textSize=15f; setTextColor(Color.DKGRAY); setPadding(0, 8, 0, 4) }
    private fun textValue(s: String) = TextView(this).apply { text=s; textSize=18f; setTextColor(Color.BLACK); setPadding(14, 14, 14, 14); setBackgroundColor(Color.rgb(246,246,246)) }
    private fun valueButton(s: String, click: () -> Unit) = TextView(this).apply {
        text=s; textSize=18f; setTextColor(Color.BLACK); setPadding(14, 14, 14, 14); setBackgroundColor(Color.rgb(246,246,246)); setOnClickListener { click() }
    }
    private fun actionButton(s: String, color: String, click: () -> Unit) = Button(this).apply {
        text=s; textSize=16f; setBackgroundColor(Color.parseColor(color)); setOnClickListener { click() }
    }
    private fun card(bg: String, fg: String, caption: String): Pair<LinearLayout,TextView> {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,16,12,16); setBackgroundColor(Color.parseColor(bg)) }
        box.addView(TextView(this).apply { text=caption; textSize=14f; setTextColor(Color.parseColor(fg)) })
        val num=TextView(this).apply { text="0.00"; textSize=31f; setTypeface(null,1); setTextColor(Color.parseColor(fg)); setPadding(0,8,0,0) }
        box.addView(num); return Pair(box,num)
    }

    private fun chooseDate() {
        DatePickerDialog(this, { _, y, m, d -> selectedDate=LocalDate.of(y,m+1,d); dateText.text=selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); refresh() },
            selectedDate.year, selectedDate.monthValue-1, selectedDate.dayOfMonth).show()
    }
    private fun chooseMonth() {
        DatePickerDialog(this, { _, y, m, _ -> selectedMonth=LocalDate.of(y,m+1,1); monthText.text=selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es","EC"))); refresh() },
            selectedMonth.year, selectedMonth.monthValue-1, 1).show()
    }
    private fun chooseTime(isStart: Boolean) {
        val t=if(isStart) startTime else endTime
        TimePickerDialog(this, { _, h, m ->
            if(isStart) startTime=LocalTime.of(h,m) else endTime=LocalTime.of(h,m)
            val fmt=DateTimeFormatter.ofPattern("h:mm a", Locale("es","EC"))
            if(isStart) startText.text=startTime.format(fmt) else endText.text=endTime.format(fmt)
            refreshLive()
        }, t.hour,t.minute,false).show()
    }

    private fun calculate(): Pair<Double,Double> {
        var a=0; var b=0
        val full=holidayBox.isChecked || selectedDate.dayOfWeek==DayOfWeek.SATURDAY || selectedDate.dayOfWeek==DayOfWeek.SUNDAY
        var t=selectedDate.atTime(startTime)
        var finish=selectedDate.atTime(endTime)
        if(!finish.isAfter(t)) finish=finish.plusDays(1)
        while(t.isBefore(finish)) {
            val minute=t.hour*60+t.minute
            if(full || minute<360) b++ else if(minute>=1050) a++
            t=t.plusMinutes(1)
        }
        return Pair(a/60.0,b/60.0)
    }
    private fun refreshLive() {
        val c=calculate(); live50.text=String.format(Locale.US,"%.2f",c.first); live100.text=String.format(Locale.US,"%.2f",c.second)
    }
    private fun addEntry() {
        val c=calculate()
        entries.add(Entry(System.currentTimeMillis(),selectedDate.toString(),startTime.toString(),endTime.toString(),holidayBox.isChecked,noteInput.text.toString(),c.first,c.second))
        noteInput.setText(""); holidayBox.isChecked=false; persist(); refresh()
        Toast.makeText(this,"Registro guardado",Toast.LENGTH_SHORT).show()
    }
    private fun refresh() {
        weekdayText.text=selectedDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL,Locale("es","EC")).replaceFirstChar { it.titlecase(Locale("es","EC")) }
        refreshLive()
        val filtered=entries.filter { it.date.startsWith(selectedMonth.toString().substring(0,7)) }.sortedBy { it.date }
        val a=filtered.sumOf { it.h50 }; val b=filtered.sumOf { it.h100 }
        summary50.text=String.format(Locale.US,"%.2f",a); summary100.text=String.format(Locale.US,"%.2f",b); summaryTotal.text=String.format(Locale.US,"%.2f",a+b)
        history.removeAllViews()
        if(filtered.isEmpty()) history.addView(TextView(this).apply { text="No hay registros para este mes."; setTextColor(Color.GRAY); setPadding(0,10,0,10) })
        filtered.forEach { r ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(10,10,10,10); setBackgroundColor(Color.rgb(247,247,247)) }
            row.addView(TextView(this).apply { text="${r.date}  •  ${r.start} - ${r.end}\n50%: ${"%.2f".format(r.h50)} h   |   100%: ${"%.2f".format(r.h100)} h\n${r.note}"; setTextColor(Color.DKGRAY) })
            row.addView(Button(this).apply { text="Eliminar"; setOnClickListener { entries.removeAll { it.id==r.id }; persist(); refresh() } })
            history.addView(row)
        }
    }
    private fun persist() {
        val arr=JSONArray()
        entries.forEach { r -> arr.put(JSONObject().put("id",r.id).put("date",r.date).put("start",r.start).put("end",r.end).put("holiday",r.holiday).put("note",r.note).put("h50",r.h50).put("h100",r.h100)) }
        prefs.edit().putString("entries",arr.toString()).apply()
    }
    private fun loadEntries() {
        val arr=JSONArray(prefs.getString("entries","[]"))
        for(i in 0 until arr.length()) { val o=arr.getJSONObject(i); entries.add(Entry(o.getLong("id"),o.getString("date"),o.getString("start"),o.getString("end"),o.optBoolean("holiday"),o.optString("note"),o.optDouble("h50"),o.optDouble("h100"))) }
    }
    private fun clearMonth() {
        entries.removeAll { it.date.startsWith(selectedMonth.toString().substring(0,7)) }; persist(); refresh()
    }
    private fun exportCsv() {
        val data=entries.filter { it.date.startsWith(selectedMonth.toString().substring(0,7)) }.sortedBy { it.date }
        if(data.isEmpty()) { Toast.makeText(this,"No hay registros de este mes",Toast.LENGTH_SHORT).show(); return }
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="text/csv"
            putExtra(Intent.EXTRA_TITLE,"horas_extras_${selectedMonth.toString().substring(0,7)}.csv")
        }
        startActivityForResult(intent,csvCreate)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==csvCreate && resultCode==Activity.RESULT_OK && data?.data!=null) {
            try {
                val uri=data.data!!
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                    w.write("\uFEFFFecha;Entrada;Salida;Horas 50%;Horas 100%;Feriado/descanso;Observación\r\n")
                    entries.filter { it.date.startsWith(selectedMonth.toString().substring(0,7)) }.sortedBy { it.date }.forEach { r ->
                        val vals=listOf(r.date,r.start,r.end,"%.2f".format(Locale.US,r.h50),"%.2f".format(Locale.US,r.h100),if(r.holiday)"Sí" else "No",r.note)
                        w.write(vals.joinToString(";") { "\""+it.replace("\"",""\"\"")+"\"" }+"\r\n")
                    }
                }
                Toast.makeText(this,"CSV guardado en la ubicación elegida",Toast.LENGTH_LONG).show()
            } catch(e:Exception) { Toast.makeText(this,"No se pudo guardar el CSV: ${e.message}",Toast.LENGTH_LONG).show() }
        }
    }
}
