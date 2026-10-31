package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE isActiveUser = 1 LIMIT 1")
    suspend fun getActiveUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET fullName = :fullName, phoneNumber = :phoneNumber, avatarUrl = :avatarUrl, notifyInvitations = :notifyInv, notifyDeadlines = :notifyDead, notifyValidations = :notifyValid, notifyPayoutTurn = :notifyPayout WHERE id = :userId")
    suspend fun updateUserProfile(
        userId: String,
        fullName: String,
        phoneNumber: String,
        avatarUrl: String,
        notifyInv: Boolean,
        notifyDead: Boolean,
        notifyValid: Boolean,
        notifyPayout: Boolean
    )

    @Query("UPDATE users SET isActiveUser = 0")
    suspend fun clearActiveUser()

    @Query("UPDATE users SET isActiveUser = 1 WHERE id = :userId")
    suspend fun setActiveUser(userId: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)

    @Query("UPDATE users SET password = :newPassword WHERE phoneNumber = :phone")
    suspend fun updatePasswordByPhone(phone: String, newPassword: String): Int

    @Query("DELETE FROM users")
    suspend fun clearAll()
}


