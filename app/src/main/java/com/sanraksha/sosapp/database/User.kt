package com.sanraksha.sosapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val uniqueId: String,
    val name: String,
    val phone: String?,  // Nullable to avoid UNIQUE constraint issues
    val email: String? = null,
    val bloodGroup: String? = null,
    val age: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)