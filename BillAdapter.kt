package com.example.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app.databinding.ItemBillBinding
import com.example.app.models.Bill
import com.example.app.models.BillStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BillAdapter
 *
 * A [ListAdapter] backed by [DiffUtil] so only changed rows are rebound
 * when the LiveData list updates — no full `notifyDataSetChanged()` calls.
 *
 * @param onPrintClick   Fired when the user taps the print FAB on an item.
 * @param onItemClick    Fired when the user taps anywhere else on the row
 *                       (e.g. to open a detail view).
 */
class BillAdapter(
    private val onItemClick: (Bill) -> Unit,
    private val onPrintClick: (Bill) -> Unit
) : ListAdapter<Bill, BillAdapter.BillViewHolder>(DiffCallback) {

    // Exposed for the swipe handler — lets it retrieve the item being swiped
    fun getBillAt(position: Int): Bill = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val binding = ItemBillBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    inner class BillViewHolder(
        private val binding: ItemBillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(bill: Bill) {
            binding.apply {
                tvInvoiceNumber.text  = "# ${bill.invoiceNumber}"
                tvCustomerName.text   = bill.customer.displayName
                tvGrandTotal.text     = "₹ ${bill.formattedGrandTotal}"
                tvItemCount.text      = "${bill.items.size} item(s)"

                tvDueDate.text = bill.dueAt
                    ?.let { "Due: ${dateFormatter.format(Date(it))}" }
                    ?: "No due date"

                tvCreatedAt.text = dateFormatter.format(Date(bill.createdAt))

                // Status chip
                chipStatus.text = bill.status.displayLabel()
                val (chipBg, chipText) = bill.status.chipColors()
                chipStatus.chipBackgroundColor = ColorStateList.valueOf(chipBg)
                chipStatus.setTextColor(chipText)

                // Overdue indicator
                tvOverdue.visibility = if (bill.isOverdue) android.view.View.VISIBLE
                                       else android.view.View.GONE

                // Tap entire card → detail / selection
                root.setOnClickListener { onItemClick(bill) }

                // Tap print button → print
                btnPrint.setOnClickListener { onPrintClick(bill) }
            }
        }
    }

    // ─── DiffUtil ─────────────────────────────────────────────────────────────

    companion object DiffCallback : DiffUtil.ItemCallback<Bill>() {
        override fun areItemsTheSame(old: Bill, new: Bill) = old.id == new.id
        override fun areContentsTheSame(old: Bill, new: Bill) = old == new
    }
}

// ─── Status display helpers ───────────────────────────────────────────────────

private fun BillStatus.displayLabel(): String = when (this) {
    BillStatus.DRAFT     -> "Draft"
    BillStatus.SENT      -> "Sent"
    BillStatus.PAID      -> "Paid"
    BillStatus.OVERDUE   -> "Overdue"
    BillStatus.CANCELLED -> "Cancelled"
}

/** Returns Pair(backgroundColour, textColour) for the status chip. */
private fun BillStatus.chipColors(): Pair<Int, Int> = when (this) {
    BillStatus.DRAFT     -> Color.parseColor("#E0E0E0") to Color.parseColor("#424242")
    BillStatus.SENT      -> Color.parseColor("#E3F2FD") to Color.parseColor("#1565C0")
    BillStatus.PAID      -> Color.parseColor("#E8F5E9") to Color.parseColor("#2E7D32")
    BillStatus.OVERDUE   -> Color.parseColor("#FFEBEE") to Color.parseColor("#C62828")
    BillStatus.CANCELLED -> Color.parseColor("#FFF8E1") to Color.parseColor("#F57F17")
}
