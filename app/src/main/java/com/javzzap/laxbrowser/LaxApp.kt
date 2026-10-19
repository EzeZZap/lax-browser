package com.javzzap.laxbrowser

import android.app.Application
import android.content.Context
import com.javzzap.laxbrowser.platform.CrashLog

class LaxApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LaxApp).container
