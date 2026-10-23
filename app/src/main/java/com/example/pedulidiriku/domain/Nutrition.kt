package com.example.pedulidiriku.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Nutrients(
    val calories: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val protein: Double = 0.0
) {
    operator fun plus(other: Nutrients) = Nutrients(
        calories + other.calories,
        sugar + other.sugar,
        sodium + other.sodium,
        protein + other.protein
    )

    operator fun times(portions: Double) =
        Nutrients(calories * portions, sugar * portions, sodium * portions, protein * portions)
}

data class Food(val id: String, val name: String, val serving: String, val nutrients: Nutrients, val notes: String = "")
data class Intake(val id: String, val food: Food, val portions: Double, val timestamp: Long) {
    val nutrients get() = food.nutrients * portions
}

interface IntakeRepository {
    fun foods(): List<Food>
    fun saveFood(food: Food)
    fun entries(): List<Intake>
    fun add(intake: Intake)
    fun delete(id: String)
}

interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
    suspend fun sync(): Boolean
}

data class DailySummary(val entries: List<Intake>, val total: Nutrients) {
    val sugarExceeded get() = total.sugar > 50.0
}

class SummarizeDay(private val repository: IntakeRepository, private val clock: AppClock) {
    operator fun invoke(date: LocalDate): DailySummary {
        val entries = repository.entries().filter {
            Instant.ofEpochMilli(it.timestamp).atZone(clock.zone()).toLocalDate() == date
        }.sortedByDescending { it.timestamp }
        return DailySummary(
            entries,
            entries.fold(Nutrients()) { sum, entry -> sum + entry.nutrients })
    }
}
