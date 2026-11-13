package com.example.netspeed

import android.app.Application
import com.example.netspeed.core.notify.UpdateChecker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MainActivity.wrapLocale(this)   // sets the Persian digits/units flag before anything is drawn
        UpdateChecker.schedule(this)    // background check for a new version (every 12 h)
    }
}
