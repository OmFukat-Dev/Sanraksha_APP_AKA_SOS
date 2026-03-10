package com.sanraksha.sosapp.database

import androidx.room.*

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM users WHERE uniqueId = :uniqueId LIMIT 1")
    suspend fun getUserById(uniqueId: String): User?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): User?

    @Query("SELECT COUNT(*) FROM users WHERE phone = :phone")
    suspend fun checkPhoneExists(phone: String): Int

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>
}