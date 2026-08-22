package com.example.aiondevicebenchmark.domain.repository

import android.net.Uri
import com.example.aiondevicebenchmark.data.BenchmarkRecord
import com.example.aiondevicebenchmark.data.SavedJsonFile
import java.io.File

interface BenchmarkResultRepository {
    val outputDirectory: File
    fun save(record: BenchmarkRecord): File
    fun saveRun(runGroupId: String, records: List<BenchmarkRecord>): File
    fun listSavedFiles(): List<SavedJsonFile>
    fun findSavedFile(fileName: String): SavedJsonFile?
    fun delete(fileName: String): Boolean
    fun saveReportCsv(records: List<BenchmarkRecord>): Uri?
}
