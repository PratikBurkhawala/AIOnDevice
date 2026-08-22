package com.example.aiondevicebenchmark.domain.model

data class ModelDownloadState(
    val key: String,
    val fileName: String,
    val engineFolder: String,
    val status: ModelDownloadStatus,
    val progressPercent: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val localPath: String = "",
    val message: String = "",
) {
    val isReady: Boolean = status == ModelDownloadStatus.Downloaded && localPath.isNotBlank()
    val isDownloading: Boolean = status == ModelDownloadStatus.Downloading
}

enum class ModelDownloadStatus {
    NotDownloaded,
    Downloading,
    Downloaded,
    Failed,
}
