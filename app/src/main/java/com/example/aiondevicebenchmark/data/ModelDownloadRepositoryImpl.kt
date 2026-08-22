package com.example.aiondevicebenchmark.data

import android.content.Context
import com.example.aiondevicebenchmark.background.BackgroundWorkTracker
import com.example.aiondevicebenchmark.domain.model.ModelDownloadState
import com.example.aiondevicebenchmark.domain.model.ModelDownloadStatus
import com.example.aiondevicebenchmark.domain.repository.ModelDownloadRepository
import com.example.aiondevicebenchmark.llm.EngineType
import com.example.aiondevicebenchmark.llm.ModelConfig
import java.io.Closeable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelDownloadRepositoryImpl(
    context: Context,
    private val scope: CoroutineScope,
    private val backgroundWorkTracker: BackgroundWorkTracker,
) : ModelDownloadRepository {
    private val rootModelDirectory = File(context.getExternalFilesDir(null), "models")

    private val _states = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    override val states: StateFlow<Map<String, ModelDownloadState>> = _states.asStateFlow()

    override fun refresh(models: List<ModelConfig>) {
        ensureRootDirectory()
        _states.update { current ->
            models.associate { model ->
                val key = stateKey(model)
                val existing = current[key]
                val file = localFile(model)
                val state = when {
                    existing?.isDownloading == true -> existing
                    file.exists() && file.length() > 0L -> downloadedState(model, file)
                    existing?.status == ModelDownloadStatus.Failed -> existing
                    else -> notDownloadedState(model)
                }
                key to state
            }
        }
    }

    override fun startDownload(model: ModelConfig) {
        if (model.downloadUrl.isBlank()) {
            setState(model, notDownloadedState(model).copy(status = ModelDownloadStatus.Failed, message = "Download URL is missing."))
            return
        }
        if (stateFor(model).isDownloading) {
            return
        }
        val destination = localFile(model)
        if (destination.exists() && destination.length() > 0L) {
            setState(model, downloadedState(model, destination))
            return
        }

        scope.launch {
            download(model, destination)
        }
    }

    override fun deleteModel(model: ModelConfig): Boolean {
        if (stateFor(model).isDownloading) {
            setState(model, stateFor(model).copy(message = "Cannot delete while download is running."))
            return false
        }
        val destination = localFile(model)
        val partial = File(destination.parentFile, "${destination.name}.part")
        val deleted = listOf(destination, partial)
            .filter { it.exists() }
            .fold(false) { anyDeleted, file -> file.delete() || anyDeleted }
        setState(model, notDownloadedState(model))
        return deleted
    }

    override fun stateFor(model: ModelConfig): ModelDownloadState {
        val current = states.value[stateKey(model)]
        if (current != null) return current
        val file = localFile(model)
        return if (file.exists() && file.length() > 0L) downloadedState(model, file) else notDownloadedState(model)
    }

    override fun localModel(model: ModelConfig): ModelConfig {
        val state = stateFor(model)
        return if (state.isReady) model.copy(filePath = state.localPath, fileSizeBytes = state.totalBytes) else model.copy(filePath = "")
    }

    override fun storageDirectory(engineType: EngineType): File = engineDirectory(engineType)

    private fun download(model: ModelConfig, destination: File) {
        val modelDirectory = engineDirectory(model.engineType)
        if (!modelDirectory.exists()) {
            modelDirectory.mkdirs()
        }

        val partial = File(destination.parentFile, "${destination.name}.part")
        setState(model, notDownloadedState(model).copy(status = ModelDownloadStatus.Downloading, message = "Starting download"))

        var connection: HttpURLConnection? = null
        var backgroundWork: Closeable? = null
        try {
            backgroundWork = backgroundWorkTracker.begin("Downloading ${model.name}")
            connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                setFailed(model, "Download failed: HTTP $responseCode")
                return
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var downloaded = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        updateDownloadProgress(model, downloaded, totalBytes)
                    }
                }
            }

            if (destination.exists()) {
                destination.delete()
            }
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            setState(model, downloadedState(model, destination))
        } catch (error: Exception) {
            partial.delete()
            setFailed(model, "Download failed: ${error.message ?: error::class.java.simpleName}")
        } finally {
            connection?.disconnect()
            backgroundWork?.close()
        }
    }

    private fun updateDownloadProgress(model: ModelConfig, downloaded: Long, totalBytes: Long?) {
        val progress = totalBytes?.let { ((downloaded * 100) / it).toInt().coerceIn(0, 100) } ?: 0
        setState(
            model,
            ModelDownloadState(
                key = stateKey(model),
                fileName = model.fileName,
                engineFolder = model.engineType.storageName,
                status = ModelDownloadStatus.Downloading,
                progressPercent = progress,
                bytesDownloaded = downloaded,
                totalBytes = totalBytes,
                localPath = "",
                message = if (totalBytes == null) "Downloading" else "Downloading $progress%",
            ),
        )
    }

    private fun setFailed(model: ModelConfig, message: String) {
        setState(model, notDownloadedState(model).copy(status = ModelDownloadStatus.Failed, message = message))
    }

    private fun setState(model: ModelConfig, state: ModelDownloadState) {
        _states.update { it + (stateKey(model) to state) }
    }

    private fun localFile(model: ModelConfig): File = File(engineDirectory(model.engineType), model.fileName)

    private fun engineDirectory(engineType: EngineType): File = File(rootModelDirectory, engineType.storageName)

    private fun stateKey(model: ModelConfig): String = "${model.engineType.storageName}/${model.fileName}"

    private fun ensureRootDirectory() {
        if (!rootModelDirectory.exists()) {
            rootModelDirectory.mkdirs()
        }
    }

    private fun downloadedState(model: ModelConfig, file: File): ModelDownloadState {
        return ModelDownloadState(
            key = stateKey(model),
            fileName = model.fileName,
            engineFolder = model.engineType.storageName,
            status = ModelDownloadStatus.Downloaded,
            progressPercent = 100,
            bytesDownloaded = file.length(),
            totalBytes = file.length(),
            localPath = file.absolutePath,
            message = "Downloaded",
        )
    }

    private fun notDownloadedState(model: ModelConfig): ModelDownloadState {
        return ModelDownloadState(
            key = stateKey(model),
            fileName = model.fileName,
            engineFolder = model.engineType.storageName,
            status = ModelDownloadStatus.NotDownloaded,
            message = model.fileSizeBytes
                ?.let { "Not downloaded (${formatFileSizeMb(it)})" }
                ?: "Not downloaded",
        )
    }

    private fun formatFileSizeMb(bytes: Long): String {
        return "%.0f MB".format(Locale.getDefault(), bytes / 1_000_000.0)
    }
}
