package com.fitlens.companion

import android.app.Application
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Settings.init(this)
        AutoBackup.start(this)
    }
}
