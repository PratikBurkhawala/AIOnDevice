package com.example.aiondevicebenchmark

import android.app.Application
import com.example.aiondevicebenchmark.data.CrashReportStore
import com.example.aiondevicebenchmark.di.appModule
import com.example.aiondevicebenchmark.di.engineModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AiOnDeviceBenchmarkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReportStore(this).install()
        registerActivityLifecycleCallbacks(AppVisibilityTracker)
        startKoin {
            androidContext(this@AiOnDeviceBenchmarkApplication)
            modules(engineModule, appModule)
        }
    }
}
