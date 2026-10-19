package com.example.pedulidiriku

import android.app.Application
import com.example.pedulidiriku.data.LocalRepository
import com.example.pedulidiriku.data.NetworkClock
import com.example.pedulidiriku.reminder.Reminders

class TrackerApplication : Application() {
    val repository by lazy { LocalRepository(this) }
    val clock by lazy { NetworkClock(this) }
    override fun onCreate() { super.onCreate(); Reminders.schedule(this) }
}
