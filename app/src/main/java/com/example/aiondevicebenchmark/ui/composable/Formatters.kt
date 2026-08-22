package com.example.aiondevicebenchmark.ui.composable

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val localDateTimeFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

fun formatDouble(value: Double): String = "%.2f".format(Locale.getDefault(), value)

fun formatDate(value: Long): String {
    return localDateTimeFormatter.format(Date(value).toInstant())
}

fun formatLocalTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        localDateTimeFormatter.format(Instant.parse(value))
    }.recoverCatching {
        LocalDateTime.parse(value).format(localDateTimeFormatter)
    }.getOrDefault(value)
}

fun formatSeconds(milliseconds: Long?): String {
    if (milliseconds == null) return ""
    return "%.3f s".format(Locale.getDefault(), milliseconds / 1000.0)
}

fun formatMemoryMb(megabytes: Int?): String {
    if (megabytes == null) return ""
    return "$megabytes MB"
}

fun formatFileSizeGb(bytes: Long?): String {
    if (bytes == null) return ""
    return "%.2f GB".format(Locale.getDefault(), bytes / 1_000_000_000.0)
}
