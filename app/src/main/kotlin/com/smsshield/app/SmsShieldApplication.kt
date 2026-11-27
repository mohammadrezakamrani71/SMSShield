package com.smsshield.app

import android.app.Application
import com.smsshield.app.data.database.AppDatabase
import com.smsshield.app.data.database.ShieldPreferences
import com.smsshield.app.data.database.ShieldRepository

class SmsShieldApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: ShieldRepository
        private set

    lateinit var preferences: ShieldPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = ShieldRepository(database)
        preferences = ShieldPreferences(this)
    }
}
