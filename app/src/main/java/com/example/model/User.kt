package com.example.model

data class User(
    val id: String,
    val phoneNumber: String,
    val fullName: String,
    val avatarUrl: String = "",
    val initials: String = fullName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
    val preferences: NotificationPreferences = NotificationPreferences()
)
