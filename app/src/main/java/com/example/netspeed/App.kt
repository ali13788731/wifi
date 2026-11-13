package com.example.netspeed

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MainActivity.wrapLocale(this)   // sets the Persian digits/units flag before anything is drawn
    }
}
