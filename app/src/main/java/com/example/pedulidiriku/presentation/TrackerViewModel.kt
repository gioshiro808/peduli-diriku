package com.example.pedulidiriku.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pedulidiriku.domain.AppClock
import com.example.pedulidiriku.domain.DailySummary
import com.example.pedulidiriku.domain.Food
import com.example.pedulidiriku.domain.Intake
import com.example.pedulidiriku.domain.IntakeRepository
import com.example.pedulidiriku.domain.Nutrients
import com.example.pedulidiriku.domain.SummarizeDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

data class TrackerState(
    val date: LocalDate,
    val foods: List<Food> = emptyList(),
    val summary: DailySummary = DailySummary(emptyList(), Nutrients()),
    val busy: Boolean = false,
    val timeStatus: String = "Menggunakan waktu perangkat",
    val error: String? = null
)

sealed interface TrackerIntent {
    data object Refresh : TrackerIntent
    data object SyncTime : TrackerIntent
    data class SelectDate(val date: LocalDate) : TrackerIntent
    data class Add(val food: Food, val portions: Double) : TrackerIntent
    data class CreateFood(val food: Food) : TrackerIntent
    data class Delete(val id: String) : TrackerIntent
}

class TrackerViewModel(private val repository: IntakeRepository, private val clock: AppClock) :
    ViewModel() {
    private val mutable =
        MutableStateFlow(TrackerState(clock.now().atZone(clock.zone()).toLocalDate()))
    val state = mutable.asStateFlow()
    private val summarize = SummarizeDay(repository, clock)
    private val mutex = Mutex()
    private var today = clock.now().atZone(clock.zone()).toLocalDate()

    init {
        accept(TrackerIntent.Refresh); accept(TrackerIntent.SyncTime)
    }

    fun accept(intent: TrackerIntent) {
        viewModelScope.launch {
            mutex.withLock {
                if (intent == TrackerIntent.SyncTime) {
                    val success = clock.sync()
                    mutable.value =
                        mutable.value.copy(timeStatus = if (success) "Waktu API tersinkron · ${clock.zone()}" else "API tidak tersedia · waktu perangkat · ${clock.zone()}")
                    return@withLock
                }
                mutable.value = mutable.value.copy(busy = true, error = null)
                try {
                    val newToday = clock.now().atZone(clock.zone()).toLocalDate()
                    val date = (intent as? TrackerIntent.SelectDate)?.date
                        ?: if (mutable.value.date == today) newToday else mutable.value.date
                    today = newToday
                    val result = withContext(Dispatchers.IO) {
                        when (intent) {
                            is TrackerIntent.Add -> {
                                require(intent.portions.isFinite() && intent.portions > 0 && intent.portions <= 100)
                                require(
                                    date == clock.now().atZone(clock.zone()).toLocalDate()
                                ) { "Pilih hari ini untuk mencatat asupan." }
                                repository.add(
                                    Intake(
                                        UUID.randomUUID().toString(),
                                        intent.food,
                                        intent.portions,
                                        clock.now().toEpochMilli()
                                    )
                                )
                            }

                            is TrackerIntent.CreateFood -> {
                                require(intent.food.name.isNotBlank() && intent.food.serving.isNotBlank())
                                val n = intent.food.nutrients
                                require(
                                    listOf(
                                        n.calories,
                                        n.sugar,
                                        n.sodium,
                                        n.protein
                                    ).all { it.isFinite() && it >= 0 })
                                repository.saveFood(intent.food)
                            }

                            is TrackerIntent.Delete -> repository.delete(intent.id)
                            else -> Unit
                        }
                        repository.foods() to summarize(date)
                    }
                    mutable.value = mutable.value.copy(
                        date = date,
                        foods = result.first,
                        summary = result.second,
                        busy = false
                    )
                } catch (e: Exception) {
                    mutable.value =
                        mutable.value.copy(busy = false, error = e.message ?: "Data gagal disimpan")
                }
            }
        }
    }
}
