package com.example.aiondevicebenchmark.ui.composable

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDouble(value: Double): String = "%.2f".format(Locale.US, value)

fun formatDate(value: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(value))
}
