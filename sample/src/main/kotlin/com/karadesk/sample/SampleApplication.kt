package com.karadesk.sample

import android.app.Application
import com.karadesk.KaraDesk

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KaraDesk.initialize(this, spaceId = BuildConfig.KARADESK_SPACE_ID, appId = BuildConfig.KARADESK_APP_ID)
    }
}
