package com.example.model

enum class ActivityType {
    PAYMENT_SUBMITTED,
    PAYMENT_VALIDATED,
    PAYMENT_REJECTED,
    CYCLE_STARTED,
    MEMBER_JOINED,
    SYSTEM_ALERT,
    TONTINE_UPDATED
}

data class ActivityItem(
    val id: String,
    val tontineId: String,
    val type: ActivityType,
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val iconName: String = "check"
)
