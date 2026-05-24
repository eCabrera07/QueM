package com.quem.app

import android.app.Application
import com.quem.data.sync.SyncScheduler

class QueMApplication : Application() {
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
        SyncScheduler.schedulePeriodic(this)
    }
}
