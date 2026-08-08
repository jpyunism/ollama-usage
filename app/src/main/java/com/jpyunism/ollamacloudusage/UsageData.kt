package com.jpyunism.ollamacloudusage

import java.time.Instant

data class ModelUsage(
    val model: String,
    val requests: Long,
    val percent: Double,
)

data class UsageData(
    val sessionPercent: Double,
    val weeklyPercent: Double,
    val sessionResetAt: Instant?,
    val sessionModels: List<ModelUsage>,
    val weeklyModels: List<ModelUsage>,
    val plan: String,
) {
    val sessionUsed: Boolean get() = sessionPercent > 0.0
    val weeklyUsed: Boolean get() = weeklyPercent > 0.0
}
