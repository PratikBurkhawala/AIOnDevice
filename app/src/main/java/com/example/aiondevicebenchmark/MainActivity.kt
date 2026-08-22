package com.example.aiondevicebenchmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.aiondevicebenchmark.ui.screen.BenchmarkRoute
import com.example.aiondevicebenchmark.ui.theme.AiOnDeviceBenchmarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiOnDeviceBenchmarkTheme {
                BenchmarkRoute()
            }
        }
    }
}
