package com.carrfy

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize

class CarrfyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase auto-initializes, but we can ensure it's initialized
        Firebase.initialize(this)
    }
}
