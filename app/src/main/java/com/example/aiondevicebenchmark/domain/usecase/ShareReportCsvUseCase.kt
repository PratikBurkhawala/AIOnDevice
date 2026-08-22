package com.example.aiondevicebenchmark.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.aiondevicebenchmark.data.SavedJsonFile
import com.example.aiondevicebenchmark.domain.repository.BenchmarkResultRepository

class ShareReportCsvUseCase(
    context: Context,
    private val repository: BenchmarkResultRepository,
) {
    private val appContext = context.applicationContext

    operator fun invoke(files: List<SavedJsonFile>): Boolean {
        val uri = repository.saveReportCsv(files.flatMap { it.records }) ?: return false
        shareUris(listOf(uri), "Share benchmark CSV", mimeType = "text/csv")
        return true
    }

    private fun shareUris(uris: List<Uri>, title: String, mimeType: String) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(chooser)
    }
}
