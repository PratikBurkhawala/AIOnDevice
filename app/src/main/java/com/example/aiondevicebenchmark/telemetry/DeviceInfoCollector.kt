package com.example.aiondevicebenchmark.telemetry

import android.content.Context
import android.os.Build
import com.example.aiondevicebenchmark.data.CpuJson
import com.example.aiondevicebenchmark.data.DeviceJson
import com.example.aiondevicebenchmark.data.GpuJson
import com.example.aiondevicebenchmark.data.NpuJson
import com.example.aiondevicebenchmark.data.RamJson
import com.example.aiondevicebenchmark.data.SocJson

class DeviceInfoCollector(context: Context) {
    private val memoryMonitor = MemoryMonitor(context)

    fun collect(): DeviceJson {
        return DeviceJson(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            soc = SocJson(
                manufacturer = Build.HARDWARE,
                model = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
            ),
            cpu = CpuJson(
                architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch"),
                cores = Runtime.getRuntime().availableProcessors(),
            ),
            gpu = GpuJson(name = null),
            npu = NpuJson(name = null, available = null),
            ram = RamJson(totalMb = memoryMonitor.totalRamMb()),
        )
    }
}
