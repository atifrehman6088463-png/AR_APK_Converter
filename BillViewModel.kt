package com.example.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.app.data.db.KhataDatabase
import com.example.app.data.repository.BillRepository
import com.example.app.models.Bill
import com.example.app.models.BillStatus
import com.example.app.models.Customer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * BillViewModel
 *
 * Single source of UI state for invoices and customers.
 * Fragments observe [LiveData] and call action methods — never the repository.
 *
 * Filtering pipeline:
 *   _statusFilter + _customerFilter → filteredBills (via combine + flatMapLatest)
 *   _searchQuery                    → searchResults
 */
class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BillRepository =
        BillRepository(KhataDatabase.getInstance(application))

    // ─── All bills / customers (raw) ─────────────────────────────────────────

    val bills: LiveData<List<Bill>>         = repository.allBills.asLiveData()
    val customers: LiveData<List<Customer>> = repository.allCustomers.asLiveData()
    val overdueBills: LiveData<List<Bill>>  = repository.overdueBills().asLiveData()

    // ─── Bill filter state ────────────────────────────────────────────────────

    private val _statusFilter   = MutableStateFlow<BillStatus?>(null)
    private val _customerFilter = MutableStateFlow<Customer?>(null)

    /**
     * Bills filtered by status AND/OR customer.
     * Both filters default to null (= show all).
     */
    val filteredBills: LiveData<List<Bill>> =
        combine(_statusFilter, _customerFilter) { status, customer -> Pair(status, customer) }
            .flatMapLatest { (status, customer) ->
                when {
                    customer != null -> repository.billsForCustomer(customer.id)
                    status   != null -> repository.billsByStatus(status)
                    else             -> repository.allBills
                }
            }
            .asLiveData()

    // ─── Customer search ──────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")

    /**
     * Customers matching [_searchQuery] (name / email / phone).
     * Empty query returns all customers.
     */
    val searchResults: LiveData<List<Customer>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allCustomers
            else repository.searchCustomers(query)
        }
        .asLiveData()

    // ─── Selection state ──────────────────────────────────────────────────────

    private val _selectedBill     = MutableLiveData<Bill?>()
    private val _selectedCustomer = MutableLiveData<Customer?>()

    val selectedBill: LiveData<Bill?>         = _selectedBill
    val selectedCustomer: LiveData<Customer?> = _selectedCustomer

    // ─── One-shot UI events ───────────────────────────────────────────────────

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    // ─── Public actions ───────────────────────────────────────────────────────

    fun selectBill(bill: Bill?)         { _selectedBill.value = bill }
    fun selectCustomer(c: Customer?)    { _selectedCustomer.value = c }

    fun setStatusFilter(status: BillStatus?) { _statusFilter.value = status }

    /**
     * Filter the invoice list to a single customer.
     * Pass null to clear the filter and show all invoices.
     */
    fun setCustomerFilter(customer: Customer?) {
        _customerFilter.value = customer
        _statusFilter.value   = null          // clear status filter when switching to customer view
    }

    fun clearAllFilters() {
        _statusFilter.value   = null
        _customerFilter.value = null
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query.trim() }

    fun onSnackbarMessageConsumed()   { _snackbarMessage.value = null }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    fun saveBill(bill: Bill) = viewModelScope.launch {
        runCatching { repository.saveBill(bill) }
            .onSuccess { _snackbarMessage.postValue("Invoice ${bill.invoiceNumber} saved") }
            .onFailure { _snackbarMessage.postValue("Failed to save invoice: ${it.message}") }
    }

    fun saveCustomer(customer: Customer) = viewModelScope.launch {
        runCatching { repository.saveCustomer(customer) }
            .onSuccess { _snackbarMessage.postValue("${customer.displayName} saved") }
            .onFailure { _snackbarMessage.postValue("Failed to save customer: ${it.message}") }
    }

    fun deleteBill(bill: Bill) = viewModelScope.launch {
        runCatching { repository.deleteBill(bill) }
            .onSuccess {
                if (_selectedBill.value?.id == bill.id) _selectedBill.postValue(null)
                _snackbarMessage.postValue("Invoice ${bill.invoiceNumber} deleted")
            }
            .onFailure { _snackbarMessage.postValue("Failed to delete: ${it.message}") }
    }

    fun deleteCustomer(customer: Customer) = viewModelScope.launch {
        runCatching { repository.deleteCustomer(customer) }
            .onSuccess {
                if (_selectedCustomer.value?.id == customer.id) _selectedCustomer.postValue(null)
                _snackbarMessage.postValue("${customer.displayName} deleted")
            }
            .onFailure { _snackbarMessage.postValue("Failed to delete: ${it.message}") }
    }

    fun markBillPaid(bill: Bill) = viewModelScope.launch {
        runCatching { repository.updateBillStatus(bill.id, BillStatus.PAID) }
            .onSuccess { _snackbarMessage.postValue("Invoice ${bill.invoiceNumber} marked Paid") }
            .onFailure { _snackbarMessage.postValue("Failed to update status: ${it.message}") }
    }

    fun markBillSent(bill: Bill) = viewModelScope.launch {
        runCatching { repository.updateBillStatus(bill.id, BillStatus.SENT) }
            .onSuccess { _snackbarMessage.postValue("Invoice ${bill.invoiceNumber} marked Sent") }
            .onFailure { _snackbarMessage.postValue("Failed to update status: ${it.message}") }
    }

    fun getBillsForCustomer(customerId: String): LiveData<List<Bill>> =
        repository.billsForCustomer(customerId).asLiveData()
}
