package com.fitlens.companion

import android.app.Application
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.TimerService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Settings.init(this)
        AutoBackup.start(this)
        // The timers' notification follows the workout timer wherever it is started or stopped (#12).
        TimerService.watch(this)
    }
}
