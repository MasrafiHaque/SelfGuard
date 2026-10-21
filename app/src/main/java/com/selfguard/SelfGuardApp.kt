package com.selfguard

import android.app.Application
import com.selfguard.core.AppContainer
import kotlinx.coroutines.launch

class SelfGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch {
            container.seedDefaultsIfNeeded()
        }
    }
}
