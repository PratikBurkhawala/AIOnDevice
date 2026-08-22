package com.example.aiondevicebenchmark

import android.app.Activity
import android.app.Application
import android.os.Bundle

object AppVisibilityTracker : Application.ActivityLifecycleCallbacks {
    private val lock = Any()
    private var startedActivities = 0

    val currentState: String
        get() = synchronized(lock) {
            if (startedActivities > 0) "FOREGROUND" else "BACKGROUND"
        }

    override fun onActivityStarted(activity: Activity) {
        synchronized(lock) {
            startedActivities += 1
        }
    }

    override fun onActivityStopped(activity: Activity) {
        synchronized(lock) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
