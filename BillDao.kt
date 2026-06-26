package com.example.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.app.data.db.BillWithDetails
import com.example.app.data.db.entity.BillEntity
import com.example.app.data.db.entity.BillItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    // ─── Queries returning full joined results ────────────────────────────────

    /** All bills with their customer and items — newest first. */
    @Transaction
    @Query("SELECT * FROM bills ORDER BY createdAt DESC")
    fun getAllBillsWithDetails(): Flow<List<BillWithDetails>>

    /** Single bill by id (suspend — for one-shot fetches). */
    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getBillWithDetailsById(id: String): BillWithDetails?

    /** All bills belonging to a specific customer. */
    @Transaction
    @Query("SELECT * FROM bills WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getBillsForCustomer(customerId: String): Flow<List<BillWithDetails>>

    /** Bills filtered by status string (e.g. "DRAFT", "PAID"). */
    @Transaction
    @Query("SELECT * FROM bills WHERE status = :status ORDER BY createdAt DESC")
    fun getBillsByStatus(status: String): Flow<List<BillWithDetails>>

    /** Bills whose dueAt has passed and are not yet PAID. */
    @Transaction
    @Query("""
        SELECT * FROM bills
        WHERE dueAt IS NOT NULL
          AND dueAt < :nowMillis
          AND status NOT IN ('PAID', 'CANCELLED')
        ORDER BY dueAt ASC
    """)
    fun getOverdueBills(nowMillis: Long): Flow<List<BillWithDetails>>

    // ─── Mutations ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Delete
    suspend fun deleteBill(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :billId")
    suspend fun deleteBillById(billId: String)

    /** Remove all items for a bill — used before re-inserting updated items. */
    @Query("DELETE FROM bill_items WHERE billId = :billId")
    suspend fun deleteBillItems(billId: String)

    @Query("UPDATE bills SET status = :status WHERE id = :billId")
    suspend fun updateBillStatus(billId: String, status: String)

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun count(): Int
}
