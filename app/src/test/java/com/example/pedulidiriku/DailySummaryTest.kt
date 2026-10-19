package com.example.pedulidiriku

import com.example.pedulidiriku.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailySummaryTest {
    private val tea = Food("tea","Teh","250 ml",Nutrients(100.0,25.0,5.0,0.0))
    private val repository = object : IntakeRepository {
        val data = mutableListOf<Intake>()
        override fun foods() = listOf(tea)
        override fun saveFood(food: Food) = Unit
        override fun entries() = data.toList()
        override fun add(intake: Intake) { data.add(intake) }
        override fun delete(id: String) { data.removeAll { it.id == id } }
    }
    private val clock = object : AppClock {
        override fun now() = Instant.parse("2026-10-19T17:00:00Z")
        override fun zone() = ZoneId.of("Asia/Jakarta")
        override suspend fun sync() = false
    }
    @Test fun `fractional servings multiply every nutrient`() {
        val n = Intake("1",tea,0.5,0).nutrients
        assertEquals(50.0,n.calories,0.001)
        assertEquals(12.5,n.sugar,0.001)
        assertEquals(2.5,n.sodium,0.001)
    }
    @Test fun `midnight uses Indonesian zone and excludes previous day`() {
        repository.add(Intake("before",tea,1.0,Instant.parse("2026-10-19T16:59:59Z").toEpochMilli()))
        repository.add(Intake("after",tea,2.0,Instant.parse("2026-10-19T17:00:00Z").toEpochMilli()))
        val summary = SummarizeDay(repository,clock)(LocalDate.of(2026,10,20))
        assertEquals(listOf("after"),summary.entries.map { it.id })
        assertEquals(50.0,summary.total.sugar,0.001)
        assertFalse(summary.sugarExceeded)
    }
    @Test fun `warning appears above threshold and clears after delete`() {
        repository.add(Intake("tea",tea,2.1,clock.now().toEpochMilli()))
        val summarize = SummarizeDay(repository,clock)
        assertTrue(summarize(LocalDate.of(2026,10,20)).sugarExceeded)
        repository.delete("tea")
        assertEquals(Nutrients(),summarize(LocalDate.of(2026,10,20)).total)
    }
}
