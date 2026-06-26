package com.example.app.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.MainActivity
import com.example.app.R
import com.example.app.databinding.FragmentBillListBinding
import com.example.app.models.Bill
import com.example.app.models.BillStatus
import com.example.app.utils.KhataPrintManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * BillListFragment
 *
 * Displays all invoices in a [RecyclerView] backed by [BillAdapter].
 *
 * Features:
 * ─ Live updates via [BillViewModel.bills] (LiveData → DiffUtil, no flicker)
 * ─ Status filter chips at the top
 * ─ Swipe-left to delete with an Undo Snackbar
 * ─ Tap any row → select + confirm print dialog
 * ─ Tap the print icon button on a row → immediate print
 * ─ FAB → share selected (or latest) invoice as PDF
 * ─ Empty state view when no invoices exist
 */
class BillListFragment : Fragment() {

    private var _binding: FragmentBillListBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel — same instance as MainActivity
    private val viewModel: BillViewModel by activityViewModels()

    private lateinit var adapter: BillAdapter
    private lateinit var printManager: KhataPrintManager

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        printManager = KhataPrintManager(requireContext())

        setupRecyclerView()
        setupFilterChips()
        setupFab()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── RecyclerView setup ───────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = BillAdapter(
            onItemClick  = ::onBillTapped,
            onPrintClick = ::printBill
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BillListFragment.adapter
            setHasFixedSize(true)
        }

        // Attach swipe-to-delete
        SwipeToDeleteCallback(
            onSwiped = ::onBillSwiped
        ).attachTo(binding.recyclerView)
    }

    // ─── Filter chips ─────────────────────────────────────────────────────────

    private fun setupFilterChips() {
        val filters: List<Pair<String, BillStatus?>> = listOf(
            "All"       to null,
            "Draft"     to BillStatus.DRAFT,
            "Sent"      to BillStatus.SENT,
            "Paid"      to BillStatus.PAID,
            "Overdue"   to BillStatus.OVERDUE,
            "Cancelled" to BillStatus.CANCELLED
        )

        filters.forEach { (label, status) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = (status == null)   // "All" checked by default
                setOnCheckedChangeListener { _, checked ->
                    if (checked) viewModel.setStatusFilter(status)
                }
            }
            binding.chipGroupFilters.addView(chip)
        }
    }

    // ─── FABs ─────────────────────────────────────────────────────────────────

    private fun setupFab() {
        // Primary: open the Add Bill form
        binding.fabNewInvoice.setOnClickListener {
            (requireActivity() as MainActivity).openAddBill()
        }

        // Secondary: share the selected (or latest) invoice
        binding.fabShare.setOnClickListener {
            val bill = viewModel.selectedBill.value
                ?: viewModel.bills.value?.firstOrNull()
                ?: run {
                    showSnackbar("No invoice to share")
                    return@setOnClickListener
                }

            val intent = printManager.buildShareIntent(
                bill      = bill,
                authority = "${requireContext().packageName}.fileprovider"
            ) ?: run {
                showSnackbar("Could not generate PDF")
                return@setOnClickListener
            }
            startActivity(Intent.createChooser(intent, "Share Invoice"))
        }
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.filteredBills.observe(viewLifecycleOwner) { bills ->
            adapter.submitList(bills)
            updateEmptyState(bills.isEmpty())
        }

        viewModel.snackbarMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                showSnackbar(message)
                viewModel.onSnackbarMessageConsumed()
            }
        }

        viewModel.selectedBill.observe(viewLifecycleOwner) { bill ->
            binding.tvSelectedInvoice.text = bill?.let { "Selected: # ${it.invoiceNumber}" } ?: ""
            binding.tvSelectedInvoice.visibility =
                if (bill != null) View.VISIBLE else View.GONE
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun onBillTapped(bill: Bill) {
        viewModel.selectBill(bill)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Invoice # ${bill.invoiceNumber}")
            .setMessage(
                "${bill.customer.displayName}\n" +
                "Total: ₹${bill.formattedGrandTotal}\n" +
                "Status: ${bill.status.name}\n\n" +
                "What would you like to do?"
            )
            .setPositiveButton("Print") { _, _ -> printBill(bill) }
            .setNeutralButton("Save PDF") { _, _ -> savePdf(bill) }
            .setNegativeButton("Mark Paid") { _, _ ->
                viewModel.markBillPaid(bill)
            }
            .show()
    }

    private fun onBillSwiped(position: Int) {
        val bill = adapter.getBillAt(position)

        // Optimistically remove from UI immediately
        val currentList = adapter.currentList.toMutableList()
        currentList.removeAt(position)
        adapter.submitList(currentList)

        // Give the user a chance to undo before the DB write
        Snackbar.make(binding.root, "Invoice ${bill.invoiceNumber} deleted", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                // Restore list — the LiveData will re-emit the correct list anyway
                viewModel.bills.value?.let { adapter.submitList(it) }
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        // No undo pressed — commit the delete
                        viewModel.deleteBill(bill)
                    }
                }
            })
            .show()
    }

    private fun printBill(bill: Bill) {
        viewModel.selectBill(bill)
        printManager.printBill(bill, jobName = "Invoice ${bill.invoiceNumber}")
    }

    private fun savePdf(bill: Bill) {
        val file = printManager.savePdf(bill)
        val msg = if (file != null) "Saved: ${file.name}" else "Failed to save PDF"
        showSnackbar(msg)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE   else View.VISIBLE
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SwipeToDeleteCallback
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a red background with a trash icon behind the swiped row,
 * then fires [onSwiped] with the adapter position.
 */
private class SwipeToDeleteCallback(
    private val onSwiped: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(
    0,                          // no drag directions
    ItemTouchHelper.LEFT        // swipe left only
) {
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#C62828")   // deep red
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.RIGHT
    }
    private val iconPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }

    fun attachTo(recyclerView: RecyclerView) {
        ItemTouchHelper(this).attachToRecyclerView(recyclerView)
    }

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwiped(viewHolder.bindingAdapterPosition)
    }

    override fun onChildDraw(
        canvas: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val cornerRadius = 16f

        // Background sweeps in from the right as the user swipes
        val bgRect = RectF(
            itemView.right + dX - cornerRadius,
            itemView.top.toFloat(),
            itemView.right.toFloat(),
            itemView.bottom.toFloat()
        )
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        // "Delete" label
        val textX = itemView.right.toFloat() - 32f
        val textY = itemView.top + (itemView.height / 2f) + 12f
        if (-dX > 120f) {
            canvas.drawText("Delete", textX, textY, textPaint)
        }

        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
