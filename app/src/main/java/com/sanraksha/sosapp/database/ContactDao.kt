package com.sanraksha.sosapp.database

import androidx.room.*

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contacts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getContactsByUserId(userId: String): List<Contact>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Int): Contact?

    @Query("DELETE FROM contacts WHERE userId = :userId")
    suspend fun deleteAllContactsForUser(userId: String)
}