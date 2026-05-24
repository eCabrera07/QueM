package com.quem.app

import android.app.Application
import com.quem.data.sync.SyncScheduler

class QueMApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
    }
}
