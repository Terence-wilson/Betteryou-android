package com.betteryou.app

import android.app.Application
import com.betteryou.app.data.BetterYouDatabase
import com.betteryou.app.data.BetterYouRepository
import com.betteryou.app.notifications.Reminders

class BetterYouApplication : Application() {
    val repository: BetterYouRepository by lazy {
        BetterYouRepository(this, BetterYouDatabase.create(this))
    }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}
