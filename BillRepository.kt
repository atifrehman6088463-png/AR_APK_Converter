package com.example.app.data.repository

import com.example.app.data.db.KhataDatabase
import com.example.app.data.db.Mappers
import com.example.app.data.db.toDomain
import com.example.app.data.db.toEntity
import com.example.app.models.Bill
import com.example.app.models.BillStatus
import com.example.app.models.Customer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * BillRepository
 *
 * Single source of truth for [Bill] and [Customer] data. All UI reads go
 * through [Flow] so the UI reacts automatically whenever the database changes.
 * All writes are suspend functions meant to be called from a coroutine scope
 * (typically [androidx.lifecycle.viewModelScope]).
 *
 * The repository is the only layer that knows about Room entities; the rest of
 * the app works purely with domain models ([Bill], [Customer], [BillItem]).
 */
class BillRepository(private val db: KhataDatabase) {

    // ─── Reactive streams ─────────────────────────────────────────────────────

    /** All bills with full details, newest first. */
    val allBills: Flow<List<Bill>> =
        db.billDao().getAllBillsWithDetails().map { list -> list.map { it.toDomain() } }

    /** All customers, newest first. */
    val allCustomers: Flow<List<Customer>> =
        db.customerDao().getAllCustomers().map { list -> list.map { it.toDomain() } }

    /** Bills for a specific customer. */
    fun billsForCustomer(customerId: String): Flow<List<Bill>> =
        db.billDao().getBillsForCustomer(customerId).map { list -> list.map { it.toDomain() } }

    /** Bills filtered by [BillStatus]. */
    fun billsByStatus(status: BillStatus): Flow<List<Bill>> =
        db.billDao().getBillsByStatus(status.name).map { list -> list.map { it.toDomain() } }

    /** Bills that are past due and not yet paid or cancelled. */
    fun overdueBills(): Flow<List<Bill>> =
        db.billDao().getOverdueBills(System.currentTimeMillis()).map { list -> list.map { it.toDomain() } }

    /** Customer search by name, email, or phone. */
    fun searchCustomers(query: String): Flow<List<Customer>> =
        db.customerDao().searchCustomers(query).map { list -> list.map { it.toDomain() } }

    // ─── One-shot reads ───────────────────────────────────────────────────────

    suspend fun getBillById(id: String): Bill? =
        db.billDao().getBillWithDetailsById(id)?.toDomain()

    suspend fun getCustomerById(id: String): Customer? =
        db.customerDao().getCustomerById(id)?.toDomain()

    // ─── Writes ───────────────────────────────────────────────────────────────

    /**
     * Insert or replace a [Customer].
     * Uses REPLACE strategy — the same [Customer.id] will overwrite the row.
     */
    suspend fun saveCustomer(customer: Customer) {
        db.customerDao().insertCustomer(customer.toEntity())
    }

    suspend fun deleteCustomer(customer: Customer) {
        db.customerDao().deleteCustomer(customer.toEntity())
    }

    /**
     * Insert or fully replace a [Bill] and all its items atomically.
     *
     * Deletes existing items first so removed line items don't linger.
     * Wrapped in a transaction so the DB is never left in a partial state.
     */
    suspend fun saveBill(bill: Bill) {
        db.runInTransaction {
            // These are suspend funs called inside a blocking transaction lambda.
            // Use runBlocking only here — this block itself runs on a background thread.
            kotlinx.coroutines.runBlocking {
                // Ensure the customer exists before inserting the bill (FK constraint).
                db.customerDao().insertCustomer(bill.customer.toEntity())
                db.billDao().insertBill(bill.toEntity())
                db.billDao().deleteBillItems(bill.id)
                db.billDao().insertBillItems(bill.items.map { it.toEntity(bill.id) })
            }
        }
    }

    /**
     * Update only the [BillStatus] of an existing bill.
     * Lighter-weight than a full [saveBill] when only the status changes.
     */
    suspend fun updateBillStatus(billId: String, status: BillStatus) {
        db.billDao().updateBillStatus(billId, status.name)
    }

    suspend fun deleteBill(bill: Bill) {
        // Cascading FK on bill_items handles item deletion automatically.
        db.billDao().deleteBill(bill.toEntity())
    }

    suspend fun deleteBillById(billId: String) {
        db.billDao().deleteBillById(billId)
    }

    suspend fun deleteCustomerById(customerId: String) {
        db.customerDao().deleteCustomerById(customerId)
    }
}
