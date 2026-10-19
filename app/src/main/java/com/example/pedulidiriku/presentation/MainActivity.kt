package com.example.pedulidiriku.presentation

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pedulidiriku.R
import com.example.pedulidiriku.TrackerApplication
import com.example.pedulidiriku.domain.Food
import com.example.pedulidiriku.domain.Nutrients
import com.example.pedulidiriku.reminder.Reminders
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var model: TrackerViewModel
    private val app get() = application as TrackerApplication
    private val dateFormat =
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))

    private fun text(id: Int) = findViewById<TextView>(id)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TrackerViewModel(app.repository, app.clock) as T
        })[TrackerViewModel::class.java]
        intent.getStringExtra("date")
            ?.let { runCatching { model.accept(TrackerIntent.SelectDate(LocalDate.parse(it))) } }
        findViewById<View>(R.id.add).setOnClickListener { addFood() }
        findViewById<View>(R.id.custom).setOnClickListener { customFood() }
        findViewById<View>(R.id.date).setOnClickListener {
            val day = model.state.value.date
            DatePickerDialog(
                this,
                { _, y, m, d -> model.accept(TrackerIntent.SelectDate(LocalDate.of(y, m + 1, d))) },
                day.year,
                day.monthValue - 1,
                day.dayOfMonth
            ).show()
        }
        findViewById<View>(R.id.notifications).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                42
            )
            else if (Build.VERSION.SDK_INT >= 26) startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    packageName
                )
            )
            else startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<View>(R.id.zone).setOnClickListener {
            val zones = arrayOf("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura")
            AlertDialog.Builder(this).setTitle("Zona waktu").setItems(
                arrayOf(
                    "WIB · Jakarta",
                    "WITA · Makassar",
                    "WIT · Jayapura"
                )
            ) { _, index ->
                app.clock.setZone(zones[index]); Reminders.resetDaily(this)
                model.accept(
                    TrackerIntent.SelectDate(
                        app.clock.now().atZone(app.clock.zone()).toLocalDate()
                    )
                )
                model.accept(TrackerIntent.SyncTime)
            }.show()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect { render(it) } }
                launch {
                    while (true) {
                        delay(60_000); model.accept(TrackerIntent.Refresh)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume(); if (::model.isInitialized) model.accept(TrackerIntent.Refresh)
    }

    private fun render(state: TrackerState) {
        text(R.id.date).text = state.date.format(dateFormat)
        text(R.id.timeStatus).text = state.timeStatus
        text(R.id.calories).text = "%.0f kkal".format(state.summary.total.calories)
        val n = state.summary.total
        text(R.id.nutrients).text =
            "Gula total   %.1f g / 50 g\nNatrium     %.0f mg\nProtein       %.1f g".format(
                n.sugar,
                n.sodium,
                n.protein
            )
        findViewById<ProgressBar>(R.id.sugarProgress).progress =
            (n.sugar / 50 * 100).toInt().coerceIn(0, 100)
        text(R.id.warning).apply {
            text =
                if (state.summary.sugarExceeded) "Perhatikan asupanmu: gula tercatat melewati ambang pengingat 50 g. Pertimbangkan pilihan tanpa gula tambahan." else "Gula tercatat belum melewati ambang pengingat 50 g."
            setTextColor(android.graphics.Color.parseColor(if (state.summary.sugarExceeded) "#A53624" else "#387452"))
        }
        findViewById<View>(R.id.add).isEnabled =
            !state.busy && state.date == app.clock.now().atZone(app.clock.zone()).toLocalDate()
        findViewById<View>(R.id.custom).isEnabled = !state.busy
        text(R.id.error).text = state.error.orEmpty()
        findViewById<View>(R.id.empty).visibility =
            if (state.summary.entries.isEmpty()) View.VISIBLE else View.GONE
        val container = findViewById<LinearLayout>(R.id.entries)
        container.removeAllViews()
        state.summary.entries.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_intake, container, false)
            row.findViewById<TextView>(R.id.entryName).text = entry.food.name
            val time = Instant.ofEpochMilli(entry.timestamp).atZone(app.clock.zone())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            row.findViewById<TextView>(R.id.entryDetail).text =
                "$time · ${entry.portions} porsi · ${entry.food.serving}\n" + "%.0f kkal · gula %.1f g · natrium %.0f mg".format(
                    entry.nutrients.calories,
                    entry.nutrients.sugar,
                    entry.nutrients.sodium
                )
            row.findViewById<View>(R.id.delete).setOnClickListener {
                AlertDialog.Builder(this).setTitle("Hapus ${entry.food.name}?")
                    .setMessage("Catatan ini akan dihapus dari riwayat asupan.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus") { _, _ -> model.accept(TrackerIntent.Delete(entry.id)) }
                    .show()
            }
            container.addView(row)
        }
    }

    private fun number(field: EditText): Double? =
        field.text.toString().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0 }

    private fun addFood() {
        val view = layoutInflater.inflate(R.layout.dialog_food, null)
        val spinner = view.findViewById<Spinner>(R.id.food)
        var filtered = model.state.value.foods
        fun update() {
            spinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                filtered.map { it.name })
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?,
                child: View?,
                position: Int,
                id: Long
            ) {
                val food = filtered.getOrNull(position) ?: return
                view.findViewById<TextView>(R.id.foodDetail).text =
                    "${food.serving} / porsi\n" + "%.0f kkal · gula %.1f g · natrium %.0f mg · protein %.1f g".format(
                        food.nutrients.calories,
                        food.nutrients.sugar,
                        food.nutrients.sodium,
                        food.nutrients.protein
                    )
            }
        }
        update()
        view.findViewById<EditText>(R.id.search).doAfterTextChanged { query ->
            filtered = model.state.value.foods.filter {
                it.name.contains(
                    query.toString(),
                    ignoreCase = true
                )
            }; update()
            if (filtered.isEmpty()) view.findViewById<TextView>(R.id.foodDetail).text =
                "Tidak ditemukan. Tambahkan lewat katalog pribadi."
        }
        val dialog = AlertDialog.Builder(this).setTitle("Catat asupan hari ini").setView(view)
            .setNegativeButton("Batal", null).setPositiveButton("Catat", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val field = view.findViewById<EditText>(R.id.portions)
                val amount = number(field)
                val food = filtered.getOrNull(spinner.selectedItemPosition)
                if (amount == null || amount <= 0 || amount > 100) field.error =
                    "Isi porsi lebih dari 0 sampai 100"
                else if (food != null) {
                    model.accept(TrackerIntent.Add(food, amount)); dialog.dismiss()
                }
            }
        }; dialog.show()
    }

    private fun customFood() {
        val view = layoutInflater.inflate(R.layout.dialog_custom, null)
        val dialog = AlertDialog.Builder(this).setTitle("Makanan / minuman sendiri").setView(view)
            .setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = view.findViewById<EditText>(R.id.name)
                val serving = view.findViewById<EditText>(R.id.serving)
                val fields = listOf(
                    R.id.inputCalories,
                    R.id.inputSugar,
                    R.id.inputSodium,
                    R.id.inputProtein
                ).map { view.findViewById<EditText>(it) }
                val numbers = fields.map { number(it) }
                var valid = true
                listOf(name, serving).forEach {
                    if (it.text.isBlank()) {
                        it.error = "Wajib diisi"; valid = false
                    }
                }
                fields.zip(numbers).forEach { (field, value) ->
                    if (value == null) {
                        field.error = "Isi angka 0 atau lebih"; valid = false
                    }
                }
                if (valid) {
                    model.accept(
                        TrackerIntent.CreateFood(
                            Food(
                                UUID.randomUUID().toString(),
                                name.text.toString().trim(),
                                serving.text.toString().trim(),
                                Nutrients(numbers[0]!!, numbers[1]!!, numbers[2]!!, numbers[3]!!)
                            )
                        )
                    )
                    dialog.dismiss()
                }
            }
        }; dialog.show()
    }
}
