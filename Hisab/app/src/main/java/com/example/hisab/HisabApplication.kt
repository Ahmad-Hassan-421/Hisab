package com.example.hisab

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HisabApplication : Application() {
    @javax.inject.Inject lateinit var push: com.example.hisab.features.notifications.PushCoordinator
    override fun onCreate() { super.onCreate(); push.start() }
}
