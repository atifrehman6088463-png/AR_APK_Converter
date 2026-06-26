package com.example.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app.MainActivity
import com.example.app.R
import com.example.app.databinding.FragmentAddBillBinding
import com.example.app.models.Bill
import com.example.app.models.BillItem
import com.example.app.models.BillStatus
import com.example.app.models.Customer
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * AddBillFragment
 *
 * A full-screen form for creating a new invoice.
 *
 * ─ Customer picker — searchable dropdown backed by [BillViewModel.customers]
 * ─ Invoice number — auto-generated, editable
 * ─ Due date — Material date picker (future dates only)
 * ─ Status selector — exposed dropdown (Draft / Sent)
 * ─ Dynamic line items — [LineItemAdapter] inside a non-scrolling RecyclerView
 * ─ Live totals card — updates on every keystroke
 * ─ Notes + Terms fields
 * ─ Save → validates, builds domain objects, calls [BillViewModel.saveBill]
 * ─ Cancel → pops back stack
 */
class AddBillFragment : Fragment() {

    private var _binding: FragmentAddBillBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillViewModel by activityViewModels()

    private lateinit var lineItemAdapter: LineItemAdapter
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // State
    private var customerList: List<Customer> = emptyList()
    private var selectedCustomer: Customer? = null
    private var selectedDueAt: Long? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddBillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupLineItemRecyclerView()
        setupCustomerPicker()
        setupStatusPicker()
        setupDatePicker()
        setupButtons()
        autoFillInvoiceNumber()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Toolbar ──────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // ─── Line items ───────────────────────────────────────────────────────────

    private fun setupLineItemRecyclerView() {
        lineItemAdapter = LineItemAdapter(
            onTotalsChanged = ::refreshTotalsCard,
            onRowRemoved    = ::refreshTotalsCard
        )

        binding.rvLineItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = lineItemAdapter
            isNestedScrollingEnabled = false   // outer NestedScrollView handles scrolling
        }

        // Start with one empty row
        lineItemAdapter.addRow()

        binding.btnAddItem.setOnClickListener {
            lineItemAdapter.addRow()
            // Scroll to the bottom so the new row is visible
            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, binding.scrollView.getChildAt(0).bottom)
            }
        }
    }

    // ─── Customer picker ──────────────────────────────────────────────────────

    private fun setupCustomerPicker() {
        binding.actvCustomer.setOnItemClickListener { _, _, position, _ ->
            selectedCustomer = customerList.getOrNull(position)
            binding.tilCustomer.error = null
        }
    }

    private fun observeViewModel() {
        viewModel.customers.observe(viewLifecycleOwner) { customers ->
            customerList = customers
            val names = customers.map { it.displayName }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                names
            )
            binding.actvCustomer.setAdapter(adapter)
        }

        // Observe save success via snackbar message then pop back
        viewModel.snackbarMessage.observe(viewLifecycleOwner) { message ->
            if (message != null && message.startsWith("Invoice")) {
                viewModel.onSnackbarMessageConsumed()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    // ─── Status picker ────────────────────────────────────────────────────────

    private fun setupStatusPicker() {
        val statuses = listOf("Draft", "Sent")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            statuses
        )
        binding.actvStatus.setAdapter(adapter)
        binding.actvStatus.setText("Draft", false)
    }

    // ─── Date picker ─────────────────────────────────────────────────────────

    private fun setupDatePicker() {
        binding.etDueDate.setOnClickListener { showDatePicker() }
        binding.tilDueDate.setEndIconOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select due date")
            .setCalendarConstraints(constraints)
            .setSelection(selectedDueAt ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            selectedDueAt = millis
            binding.etDueDate.setText(dateFormatter.format(Date(millis)))
        }

        picker.show(parentFragmentManager, "DUE_DATE_PICKER")
    }

    // ─── Invoice number ───────────────────────────────────────────────────────

    private fun autoFillInvoiceNumber() {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val suffix = (1000..9999).random()
        binding.etInvoiceNumber.setText("INV-$year-$suffix")
    }

    // ─── Live totals ──────────────────────────────────────────────────────────

    private fun refreshTotalsCard() {
        val subtotal  = lineItemAdapter.subtotal()
        val discount  = lineItemAdapter.totalDiscount()
        val tax       = lineItemAdapter.totalTax()
        val grand     = lineItemAdapter.grandTotal()

        binding.tvSubtotal.text    = "₹ ${"%.2f".format(subtotal)}"
        binding.tvDiscount.text    = "₹ ${"%.2f".format(discount)}"
        binding.tvTax.text         = "₹ ${"%.2f".format(tax)}"
        binding.tvGrandTotal.text  = "₹ ${"%.2f".format(grand)}"

        binding.rowDiscount.visibility =
            if (discount > 0) View.VISIBLE else View.GONE
    }

    // ─── Save / Cancel ────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { validateAndSave() }
        binding.btnCancel.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun validateAndSave() {
        var valid = true

        // Invoice number
        val invoiceNumber = binding.etInvoiceNumber.text?.toString()?.trim().orEmpty()
        if (invoiceNumber.isBlank()) {
            binding.tilInvoiceNumber.error = "Invoice number required"
            valid = false
        } else {
            binding.tilInvoiceNumber.error = null
        }

        // Customer
        if (selectedCustomer == null) {
            binding.tilCustomer.error = "Please select a customer"
            valid = false
        } else {
            binding.tilCustomer.error = null
        }

        // Line items — need at least one valid row
        val validRows = lineItemAdapter.getRows().filter { it.isValid() }
        if (validRows.isEmpty()) {
            Snackbar.make(binding.root, "Add at least one valid line item", Snackbar.LENGTH_SHORT).show()
            valid = false
        }

        if (!valid) return

        // Map form rows → domain BillItem
        val billItems = validRows.map { row ->
            BillItem(
                id             = row.id,
                name           = row.name,
                description    = row.description,
                quantity       = row.quantity,
                unit           = row.unit,
                unitPrice      = row.unitPrice,
                discountAmount = row.discountAmount,
                taxRatePercent = row.taxRatePercent
            )
        }

        // Map status string → BillStatus
        val status = when (binding.actvStatus.text.toString()) {
            "Sent" -> BillStatus.SENT
            else   -> BillStatus.DRAFT
        }

        val bill = Bill(
            id                 = UUID.randomUUID().toString(),
            invoiceNumber      = invoiceNumber,
            customer           = selectedCustomer!!,
            items              = billItems,
            status             = status,
            notes              = binding.etNotes.text?.toString()?.trim().orEmpty(),
            termsAndConditions = binding.etTerms.text?.toString()?.trim().orEmpty(),
            dueAt              = selectedDueAt,
            createdAt          = System.currentTimeMillis()
        )

        viewModel.saveBill(bill)
        // Navigation back happens in the snackbar observer above
    }
}
