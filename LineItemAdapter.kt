package com.example.app.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.app.databinding.ItemLineItemFormBinding
import java.util.UUID

// ─── Form-layer data holder (not a domain model) ─────────────────────────────

/**
 * Mutable row state held while the user fills in the Add Bill form.
 * Converted to a real [com.example.app.models.BillItem] on Save.
 */
data class LineItemRow(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String = "",
    var quantity: Double = 1.0,
    var unit: String = "pcs",
    var unitPrice: Double = 0.0,
    var discountAmount: Double = 0.0,
    var taxRatePercent: Double = 18.0
) {
    val taxableAmount: Double get() = ((quantity * unitPrice) - discountAmount).coerceAtLeast(0.0)
    val taxAmount: Double     get() = taxableAmount * (taxRatePercent / 100.0)
    val lineTotal: Double     get() = taxableAmount + taxAmount

    fun isValid(): Boolean = name.isNotBlank() && quantity > 0 && unitPrice >= 0
}

// ─── Adapter ─────────────────────────────────────────────────────────────────

/**
 * LineItemAdapter
 *
 * Drives the editable line-item rows inside [AddBillFragment].
 * Each field has a [TextWatcher] that updates the [LineItemRow] in-place and
 * fires [onTotalsChanged] so the fragment can refresh the summary card live.
 */
class LineItemAdapter(
    private val onTotalsChanged: () -> Unit,
    private val onRowRemoved: () -> Unit
) : RecyclerView.Adapter<LineItemAdapter.LineItemViewHolder>() {

    private val rows: MutableList<LineItemRow> = mutableListOf()

    // ─── Public API ───────────────────────────────────────────────────────────

    fun addRow(row: LineItemRow = LineItemRow()) {
        rows.add(row)
        notifyItemInserted(rows.lastIndex)
        onTotalsChanged()
    }

    fun removeRow(position: Int) {
        if (position in rows.indices) {
            rows.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, rows.size)
            onTotalsChanged()
            onRowRemoved()
        }
    }

    fun getRows(): List<LineItemRow> = rows.toList()

    fun grandTotal(): Double    = rows.sumOf { it.lineTotal }
    fun totalTax(): Double      = rows.sumOf { it.taxAmount }
    fun subtotal(): Double      = rows.sumOf { it.quantity * it.unitPrice }
    fun totalDiscount(): Double = rows.sumOf { it.discountAmount }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineItemViewHolder {
        val binding = ItemLineItemFormBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LineItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LineItemViewHolder, position: Int) {
        holder.bind(rows[position], position)
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    inner class LineItemViewHolder(
        private val b: ItemLineItemFormBinding
    ) : RecyclerView.ViewHolder(b.root) {

        // Keep references so we can remove watchers before re-binding
        private var nameWatcher: TextWatcher? = null
        private var descWatcher: TextWatcher? = null
        private var qtyWatcher: TextWatcher? = null
        private var unitWatcher: TextWatcher? = null
        private var priceWatcher: TextWatcher? = null
        private var discountWatcher: TextWatcher? = null
        private var taxWatcher: TextWatcher? = null

        fun bind(row: LineItemRow, position: Int) {
            // ── Remove stale watchers before setting text to avoid feedback loops
            b.etItemName.removeTextChangedListener(nameWatcher)
            b.etItemDesc.removeTextChangedListener(descWatcher)
            b.etQty.removeTextChangedListener(qtyWatcher)
            b.etUnit.removeTextChangedListener(unitWatcher)
            b.etUnitPrice.removeTextChangedListener(priceWatcher)
            b.etDiscount.removeTextChangedListener(discountWatcher)
            b.etTaxRate.removeTextChangedListener(taxWatcher)

            // ── Populate fields
            b.etItemName.setText(row.name)
            b.etItemDesc.setText(row.description)
            b.etQty.setText(row.formattedQty())
            b.etUnit.setText(row.unit)
            b.etUnitPrice.setText(row.unitPrice.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "")
            b.etDiscount.setText(row.discountAmount.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "")
            b.etTaxRate.setText(row.taxRatePercent.let { "%.0f".format(it) })
            b.tvLineTotal.text = "₹ ${"%.2f".format(row.lineTotal)}"
            b.tvRowNumber.text = "${position + 1}"

            // ── Attach fresh watchers
            nameWatcher     = row.watcher { row.name = it }
            descWatcher     = row.watcher { row.description = it }
            qtyWatcher      = row.watcher { row.quantity = it.toDoubleOrNull() ?: row.quantity }
            unitWatcher     = row.watcher { row.unit = it.ifBlank { "pcs" } }
            priceWatcher    = row.watcher { row.unitPrice = it.toDoubleOrNull() ?: 0.0 }
            discountWatcher = row.watcher { row.discountAmount = it.toDoubleOrNull() ?: 0.0 }
            taxWatcher      = row.watcher { row.taxRatePercent = it.toDoubleOrNull() ?: 0.0 }

            b.etItemName.addTextChangedListener(nameWatcher)
            b.etItemDesc.addTextChangedListener(descWatcher)
            b.etQty.addTextChangedListener(qtyWatcher)
            b.etUnit.addTextChangedListener(unitWatcher)
            b.etUnitPrice.addTextChangedListener(priceWatcher)
            b.etDiscount.addTextChangedListener(discountWatcher)
            b.etTaxRate.addTextChangedListener(taxWatcher)

            b.btnDeleteItem.setOnClickListener { removeRow(bindingAdapterPosition) }
        }

        /** Builds a TextWatcher that runs [update] and then refreshes the line total and summary. */
        private fun LineItemRow.watcher(update: (String) -> Unit): TextWatcher =
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    update(s?.toString()?.trim() ?: "")
                    this@LineItemViewHolder.b.tvLineTotal.text =
                        "₹ ${"%.2f".format(this@watcher.lineTotal)}"
                    onTotalsChanged()
                }
            }

        private fun LineItemRow.formattedQty(): String =
            if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString()
            else "%.2f".format(quantity)
    }
}
