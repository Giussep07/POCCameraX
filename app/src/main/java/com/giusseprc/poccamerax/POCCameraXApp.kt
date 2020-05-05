package com.giusseprc.poccamerax

import android.app.Application
import timber.log.Timber

class POCCameraXApp : Application(){

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}