package com.example.aiondevicebenchmark.domain.usecase

import com.example.aiondevicebenchmark.data.CrashReportStore
import com.example.aiondevicebenchmark.data.SavedCrashReport

class ListCrashReportsUseCase(
    private val store: CrashReportStore,
) {
    operator fun invoke(): List<SavedCrashReport> = store.listCrashReports()
}

class DeleteCrashReportUseCase(
    private val store: CrashReportStore,
) {
    operator fun invoke(fileName: String): Boolean = store.deleteCrashReport(fileName)
}
