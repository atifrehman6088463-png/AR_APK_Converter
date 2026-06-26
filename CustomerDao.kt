package com.example.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.app.data.db.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    /** Reactive stream of all customers, newest first. */
    @Query("SELECT * FROM customers ORDER BY createdAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    /** Single fetch by primary key — null if not found. */
    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    /** Search by name or email (case-insensitive via LIKE). */
    @Query("""
        SELECT * FROM customers
        WHERE name  LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    /** Insert or fully replace if the same id already exists. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: String)

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int
}
