package com.example.aiondevicebenchmark.telemetry

import android.content.Context
import android.os.PowerManager
import com.example.aiondevicebenchmark.data.BatterySnapshotJson
import com.example.aiondevicebenchmark.data.DeviceJson

class TelemetryCollector(context: Context) {
    private val appContext = context.applicationContext
    val memoryMonitor = MemoryMonitor(appContext)
    private val batteryMonitor = BatteryMonitor(appContext)
    private val deviceInfoCollector = DeviceInfoCollector(appContext)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    fun collectDeviceInfo(): DeviceJson = deviceInfoCollector.collect()
    fun collectBattery(): BatterySnapshotJson = batteryMonitor.snapshot()
    fun isScreenOn(): Boolean = powerManager?.isInteractive ?: true
    fun thermalStatus(): String = ThermalMonitor(appContext).status()
}
