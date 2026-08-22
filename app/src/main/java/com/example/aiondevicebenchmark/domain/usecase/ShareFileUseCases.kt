package com.example.aiondevicebenchmark.domain.usecase

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class ShareJsonFileUseCase(
    context: Context,
) {
    private val appContext = context.applicationContext

    operator fun invoke(absolutePath: String): Boolean {
        return shareFile(
            appContext = appContext,
            absolutePath = absolutePath,
            title = "Share benchmark JSON",
            mimeType = "application/json",
        )
    }
}

class ShareCrashReportUseCase(
    context: Context,
) {
    private val appContext = context.applicationContext

    operator fun invoke(absolutePath: String): Boolean {
        return shareFile(
            appContext = appContext,
            absolutePath = absolutePath,
            title = "Share crash JSON",
            mimeType = "application/json",
        )
    }
}

private fun shareFile(
    appContext: Context,
    absolutePath: String,
    title: String,
    mimeType: String,
): Boolean {
    val file = File(absolutePath)
    if (!file.exists() || !file.isFile) return false

    val uri = FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    appContext.startActivity(chooser)
    return true
}
