package com.urbancointabpro.admin

import android.app.Application
import android.util.Log

class AdminApp : Application() {
    companion object {
        private const val TAG = "AdminCointabPro"
        const val VERSION_NAME = "1.3.1"
        const val VERSION_CODE = 6
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Admin CointabPro v$VERSION_NAME ($VERSION_CODE) initialized")
    }
}
