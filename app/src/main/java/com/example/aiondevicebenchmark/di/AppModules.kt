package com.example.aiondevicebenchmark.di

import com.example.aiondevicebenchmark.background.BackgroundWorkTracker
import com.example.aiondevicebenchmark.benchmark.BenchmarkController
import com.example.aiondevicebenchmark.benchmark.BenchmarkRunner
import com.example.aiondevicebenchmark.data.JsonRepository
import com.example.aiondevicebenchmark.data.ModelDownloadRepositoryImpl
import com.example.aiondevicebenchmark.domain.repository.BenchmarkResultRepository
import com.example.aiondevicebenchmark.domain.repository.ModelDownloadRepository
import com.example.aiondevicebenchmark.domain.usecase.DeleteSavedJsonFileUseCase
import com.example.aiondevicebenchmark.domain.usecase.DeleteModelUseCase
import com.example.aiondevicebenchmark.domain.usecase.DetectModelQuantizationUseCase
import com.example.aiondevicebenchmark.domain.usecase.FindSavedJsonFileUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetDefaultModelForEngineUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetModelStorageDirectoryUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetModelsForEngineUseCase
import com.example.aiondevicebenchmark.domain.usecase.GetSupportedEnginesUseCase
import com.example.aiondevicebenchmark.domain.usecase.LocalizeModelUseCase
import com.example.aiondevicebenchmark.domain.usecase.ListSavedJsonFilesUseCase
import com.example.aiondevicebenchmark.domain.usecase.ObserveModelDownloadsUseCase
import com.example.aiondevicebenchmark.domain.usecase.RefreshModelDownloadsUseCase
import com.example.aiondevicebenchmark.domain.usecase.ShareReportCsvUseCase
import com.example.aiondevicebenchmark.domain.usecase.StartModelDownloadUseCase
import com.example.aiondevicebenchmark.domain.usecase.StartBenchmarkUseCase
import com.example.aiondevicebenchmark.llama.LlamaEngineFactory
import com.example.aiondevicebenchmark.llm.DefaultEngineCatalog
import com.example.aiondevicebenchmark.llm.EngineCatalog
import com.example.aiondevicebenchmark.llm.EngineFactory
import com.example.aiondevicebenchmark.telemetry.TelemetryCollector
import com.example.aiondevicebenchmark.ui.benchmark.BenchmarkViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val engineModule = module {
    single<EngineCatalog> { DefaultEngineCatalog() }
    single<EngineFactory> { LlamaEngineFactory() }
}

val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single { BackgroundWorkTracker(androidContext()) }
    single { TelemetryCollector(androidContext()) }
    single<BenchmarkResultRepository> { JsonRepository(androidContext()) }
    single<ModelDownloadRepository> { ModelDownloadRepositoryImpl(androidContext(), get(), get()) }

    factory { BenchmarkRunner(engineFactory = get(), telemetryCollector = get(), resultRepository = get()) }
    single { BenchmarkController(runner = get(), scope = get(), backgroundWorkTracker = get()) }

    factory { StartBenchmarkUseCase(controller = get()) }
    factory { ListSavedJsonFilesUseCase(repository = get()) }
    factory { FindSavedJsonFileUseCase(repository = get()) }
    factory { DeleteSavedJsonFileUseCase(repository = get()) }
    factory { ShareReportCsvUseCase(context = androidContext(), repository = get()) }
    factory { GetSupportedEnginesUseCase(engineCatalog = get()) }
    factory { GetModelsForEngineUseCase(engineCatalog = get()) }
    factory { GetDefaultModelForEngineUseCase(engineCatalog = get()) }
    factory { DetectModelQuantizationUseCase(engineCatalog = get()) }
    factory { ObserveModelDownloadsUseCase(repository = get()) }
    factory { RefreshModelDownloadsUseCase(repository = get()) }
    factory { StartModelDownloadUseCase(repository = get()) }
    factory { DeleteModelUseCase(repository = get()) }
    factory { LocalizeModelUseCase(repository = get()) }
    factory { GetModelStorageDirectoryUseCase(repository = get()) }

    viewModel {
        BenchmarkViewModel(
            startBenchmarkUseCase = get(),
            listSavedJsonFilesUseCase = get(),
            findSavedJsonFileUseCase = get(),
            deleteSavedJsonFileUseCase = get(),
            shareReportCsvUseCase = get(),
            getSupportedEnginesUseCase = get(),
            getModelsForEngineUseCase = get(),
            getDefaultModelForEngineUseCase = get(),
            detectModelQuantizationUseCase = get(),
            observeModelDownloadsUseCase = get(),
            refreshModelDownloadsUseCase = get(),
            startModelDownloadUseCase = get(),
            deleteModelUseCase = get(),
            localizeModelUseCase = get(),
            getModelStorageDirectoryUseCase = get(),
        )
    }
}
