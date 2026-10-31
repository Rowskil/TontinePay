package com.example.model

enum class NotificationType {
    INVITATION,
    DEADLINE,
    VALIDATION,
    PAYOUT_TURN,
    SYSTEM
}

data class AppNotification(
    val id: String,
    val userId: String,
    val tontineId: String? = null,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val actionData: String? = null
)

data class NotificationPreferences(
    val notifyInvitations: Boolean = true,
    val notifyDeadlines: Boolean = true,
    val notifyValidations: Boolean = true,
    val notifyPayoutTurn: Boolean = true
)
