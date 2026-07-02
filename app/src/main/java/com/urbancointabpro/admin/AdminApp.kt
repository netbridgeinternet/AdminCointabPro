package com.urbancointabpro.admin

import android.app.Application
import android.util.Log

class AdminApp : Application() {
    companion object {
        private const val TAG = "AdminCointabPro"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Admin CointabPro v1.0.0 initialized")
    }
}
