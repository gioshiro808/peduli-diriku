package com.example.pedulidiriku.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.pedulidiriku.domain.Food
import com.example.pedulidiriku.domain.Intake
import com.example.pedulidiriku.domain.IntakeRepository
import com.example.pedulidiriku.domain.Nutrients

class LocalRepository(context: Context) : SQLiteOpenHelper(context, "nutrition.db", null, 2),
    IntakeRepository {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE foods(id TEXT PRIMARY KEY, name TEXT NOT NULL, serving TEXT NOT NULL, calories REAL, sugar REAL, sodium REAL, protein REAL, notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE intake(id TEXT PRIMARY KEY, food TEXT NOT NULL, portions REAL NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX intake_time ON intake(timestamp)")
        seeds.forEach { db.insertOrThrow("foods", null, values(it)) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE foods ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            IndonesianFoods.additions.forEach {
                db.insertWithOnConflict("foods", null, values(it), SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }
    private fun values(food: Food) = ContentValues().apply {
        put("id", food.id); put("name", food.name); put("serving", food.serving)
        put("calories", food.nutrients.calories); put("sugar", food.nutrients.sugar)
        put("sodium", food.nutrients.sodium); put("protein", food.nutrients.protein)
        put("notes", food.notes)
    }

    override fun foods(): List<Food> =
        readableDatabase.rawQuery("SELECT * FROM foods ORDER BY name", null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Food(
                        c.getString(0),
                        c.getString(1),
                        c.getString(2),
                        Nutrients(c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getDouble(6)),
                        c.getString(7)
                    )
                )
            }
        }

    override fun saveFood(food: Food) {
        writableDatabase.insertOrThrow("foods", null, values(food))
    }

    override fun entries(): List<Intake> {
        val catalog = foods().associateBy { it.id }
        return readableDatabase.rawQuery("SELECT * FROM intake", null).use { c ->
            buildList {
                while (c.moveToNext()) catalog[c.getString(1)]?.let {
                    add(
                        Intake(
                            c.getString(0),
                            it,
                            c.getDouble(2),
                            c.getLong(3)
                        )
                    )
                }
            }
        }
    }

    override fun add(intake: Intake) {
        writableDatabase.insertOrThrow("intake", null, ContentValues().apply {
            put("id", intake.id); put("food", intake.food.id); put(
            "portions",
            intake.portions
        ); put("timestamp", intake.timestamp)
        })
    }

    override fun delete(id: String) {
        writableDatabase.delete("intake", "id = ?", arrayOf(id))
    }

    companion object {
        // Illustrative estimates per serving, not a verified national food composition dataset.
        val seeds = listOf(
            Food("rice", "Nasi putih", "100 g", Nutrients(175.0, 0.1, 1.0, 3.2)),
            Food(
                "friedrice",
                "Nasi goreng",
                "1 piring · 250 g",
                Nutrients(450.0, 5.0, 900.0, 12.0)
            ),
            Food("tempe", "Tempe goreng", "2 potong · 50 g", Nutrients(160.0, 0.5, 150.0, 9.0)),
            Food("egg", "Telur rebus", "1 butir · 55 g", Nutrients(78.0, 0.6, 62.0, 6.3)),
            Food("chicken", "Ayam goreng", "1 potong · 100 g", Nutrients(260.0, 0.0, 450.0, 25.0)),
            Food("banana", "Pisang", "1 buah · 100 g", Nutrients(89.0, 12.0, 1.0, 1.1)),
            Food("tea", "Es teh manis", "1 gelas · 250 ml", Nutrients(100.0, 25.0, 5.0, 0.0)),
            Food("milk", "Susu UHT plain", "200 ml", Nutrients(120.0, 9.0, 90.0, 6.0)),
            Food(
                "noodles",
                "Mi instan kuah",
                "1 bungkus · 75 g",
                Nutrients(350.0, 5.0, 1200.0, 8.0)
            ),
            Food("water", "Air mineral", "250 ml", Nutrients())
        ) + IndonesianFoods.additions
    }
}
