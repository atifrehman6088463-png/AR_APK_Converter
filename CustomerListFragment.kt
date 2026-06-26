package com.example.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.MainActivity
import com.example.app.databinding.FragmentCustomerListBinding
import com.example.app.models.Customer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * CustomerListFragment
 *
 * Displays all customers in a [RecyclerView] driven by [BillViewModel.searchResults].
 *
 * Features:
 * ─ Instant search — every keystroke updates [BillViewModel._searchQuery] which
 *   switches the backing Flow via [kotlinx.coroutines.flow.flatMapLatest]; the
 *   adapter receives a DiffUtil-computed update with no perceptible lag.
 * ─ Swipe-left to delete with Undo Snackbar
 * ─ Tap a row → summary dialog (edit / view bills)
 * ─ FAB → open [AddCustomerFragment]
 * ─ Empty state with context-aware message (empty DB vs. no search results)
 */
class CustomerListFragment : Fragment() {

    private var _binding: FragmentCustomerListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillViewModel by activityViewModels()
    private lateinit var adapter: CustomerAdapter

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFab()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear query so the list resets when returning to this fragment
        viewModel.setSearchQuery("")
        _binding = null
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = CustomerAdapter(onItemClick = ::onCustomerTapped)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CustomerListFragment.adapter
            setHasFixedSize(true)
        }

        CustomerSwipeToDeleteCallback(onSwiped = ::onCustomerSwiped)
            .attachTo(binding.recyclerView)
    }

    // ─── Instant search ───────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
                binding.ivClearSearch.visibility =
                    if (s.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        })

        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.setSearchQuery("")
        }
    }

    // ─── FAB ──────────────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabAddCustomer.setOnClickListener {
            (requireActivity() as MainActivity).openAddCustomer()
        }
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { customers ->
            adapter.submitList(customers)
            updateEmptyState(
                isEmpty  = customers.isEmpty(),
                isSearch = binding.etSearch.text?.isNotBlank() == true
            )
        }

        viewModel.snackbarMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.onSnackbarMessageConsumed()
            }
        }
    }

    // ─── Row tap ──────────────────────────────────────────────────────────────

    private fun onCustomerTapped(customer: Customer) {
        viewModel.selectCustomer(customer)

        val contact = buildString {
            if (customer.email.isNotBlank()) appendLine("✉  ${customer.email}")
            if (customer.phone.isNotBlank()) appendLine("📞  ${customer.phone}")
            if (customer.formattedAddress.isNotBlank()) appendLine("\n${customer.formattedAddress}")
        }.trim()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(customer.displayName)
            .setMessage(contact.ifBlank { "No additional info" })
            .setPositiveButton("Edit") { _, _ ->
                (requireActivity() as MainActivity).openAddCustomer(customer)
            }
            .setNeutralButton("View Invoices") { _, _ ->
                // Switch to invoices tab filtered by this customer
                viewModel.setCustomerFilter(customer)
                (requireActivity() as MainActivity).switchToInvoices()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ─── Swipe delete ─────────────────────────────────────────────────────────

    private fun onCustomerSwiped(position: Int) {
        val customer = adapter.getCustomerAt(position)

        val currentList = adapter.currentList.toMutableList()
        currentList.removeAt(position)
        adapter.submitList(currentList)

        Snackbar.make(binding.root, "${customer.displayName} deleted", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                viewModel.searchResults.value?.let { adapter.submitList(it) }
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) viewModel.deleteCustomer(customer)
                }
            })
            .show()
    }

    // ─── Empty state ──────────────────────────────────────────────────────────

    private fun updateEmptyState(isEmpty: Boolean, isSearch: Boolean) {
        binding.recyclerView.visibility  = if (isEmpty) View.GONE  else View.VISIBLE
        binding.layoutEmpty.visibility   = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            binding.tvEmptyTitle.text = if (isSearch) "No results" else "No customers yet"
            binding.tvEmptySubtitle.text =
                if (isSearch) "Try a different name, email, or phone"
                else "Tap + to add your first customer"
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomerSwipeToDeleteCallback
// ─────────────────────────────────────────────────────────────────────────────

private class CustomerSwipeToDeleteCallback(
    private val onSwiped: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val bgPaint = Paint().apply { color = Color.parseColor("#C62828"); isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 36f; isAntiAlias = true
        isFakeBoldText = true; textAlign = Paint.Align.RIGHT
    }

    fun attachTo(rv: RecyclerView) { ItemTouchHelper(this).attachToRecyclerView(rv) }

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                        t: RecyclerView.ViewHolder) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwiped(viewHolder.bindingAdapterPosition)
    }

    override fun onChildDraw(canvas: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                             dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
        val v = vh.itemView
        val r = 16f
        val rect = RectF(v.right + dX - r, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
        canvas.drawRoundRect(rect, r, r, bgPaint)
        if (-dX > 100f) canvas.drawText("Delete",
            v.right.toFloat() - 28f, v.top + v.height / 2f + 12f, textPaint)
        super.onChildDraw(canvas, rv, vh, dX, dY, actionState, isActive)
    }
}
