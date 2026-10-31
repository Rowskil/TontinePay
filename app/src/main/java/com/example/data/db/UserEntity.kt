package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.NotificationPreferences
import com.example.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val fullName: String,
    val password: String = "",
    val avatarUrl: String = "",
    val notifyInvitations: Boolean = true,
    val notifyDeadlines: Boolean = true,
    val notifyValidations: Boolean = true,
    val notifyPayoutTurn: Boolean = true,
    val isActiveUser: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toUser(): User {
        return User(
            id = id,
            phoneNumber = phoneNumber,
            fullName = fullName,
            avatarUrl = avatarUrl,
            preferences = NotificationPreferences(
                notifyInvitations = notifyInvitations,
                notifyDeadlines = notifyDeadlines,
                notifyValidations = notifyValidations,
                notifyPayoutTurn = notifyPayoutTurn
            )
        )
    }

    companion object {
        fun fromUser(user: User, password: String = "", isActive: Boolean = false): UserEntity {
            return UserEntity(
                id = user.id,
                phoneNumber = user.phoneNumber,
                fullName = user.fullName,
                password = password,
                avatarUrl = user.avatarUrl,
                notifyInvitations = user.preferences.notifyInvitations,
                notifyDeadlines = user.preferences.notifyDeadlines,
                notifyValidations = user.preferences.notifyValidations,
                notifyPayoutTurn = user.preferences.notifyPayoutTurn,
                isActiveUser = isActive
            )
        }
    }
}
