package com.example.app.models

import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Customer
// ─────────────────────────────────────────────────────────────────────────────

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "",
    val gstin: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Display-safe name — falls back to "Unknown Customer" when blank. */
    val displayName: String
        get() = name.trim().ifEmpty { "Unknown Customer" }

    /** True when at least one contact field is populated. */
    val hasContactInfo: Boolean
        get() = email.isNotBlank() || phone.isNotBlank()

    /**
     * Multi-line address block suitable for an invoice header.
     * Blank lines are omitted automatically.
     */
    val formattedAddress: String
        get() = listOfNotNull(
            address.takeIf { it.isNotBlank() },
            buildString {
                if (city.isNotBlank()) append(city)
                if (state.isNotBlank()) { if (isNotEmpty()) append(", "); append(state) }
                if (postalCode.isNotBlank()) { if (isNotEmpty()) append(" "); append(postalCode) }
            }.takeIf { it.isNotBlank() },
            country.takeIf { it.isNotBlank() },
            if (gstin.isNotBlank()) "GSTIN: $gstin" else null
        ).joinToString("\n")
}

// ─────────────────────────────────────────────────────────────────────────────
// BillItem
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single line item on an invoice.
 *
 * @param hsnSac   HSN/SAC code for tax classification (optional).
 * @param discount Flat discount amount applied before tax (default 0).
 */
data class BillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val hsnSac: String = "",
    val quantity: Double,
    val unit: String = "pcs",
    val unitPrice: Double,
    val discountAmount: Double = 0.0,
    val taxRatePercent: Double = 0.0
) {
    init {
        require(quantity >= 0) { "Quantity must be non-negative" }
        require(unitPrice >= 0.0) { "Unit price must be non-negative" }
        require(discountAmount >= 0.0) { "Discount must be non-negative" }
        require(taxRatePercent in 0.0..100.0) { "Tax rate must be between 0 and 100" }
    }

    val grossAmount: Double
        get() = quantity * unitPrice

    val taxableAmount: Double
        get() = (grossAmount - discountAmount).coerceAtLeast(0.0)

    val taxAmount: Double
        get() = taxableAmount * (taxRatePercent / 100.0)

    val lineTotal: Double
        get() = taxableAmount + taxAmount

    val formattedUnitPrice: String get() = "%.2f".format(unitPrice)
    val formattedLineTotal: String get() = "%.2f".format(lineTotal)
    val formattedTaxAmount: String get() = "%.2f".format(taxAmount)

    /** Quantity formatted without trailing zeros for whole numbers. */
    val formattedQuantity: String
        get() = if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString()
                else "%.2f".format(quantity)
}

// ─────────────────────────────────────────────────────────────────────────────
// Bill  (invoice header + line items)
// ─────────────────────────────────────────────────────────────────────────────

enum class BillStatus { DRAFT, SENT, PAID, OVERDUE, CANCELLED }

data class Bill(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val customer: Customer,
    val items: List<BillItem>,
    val notes: String = "",
    val termsAndConditions: String = "",
    val status: BillStatus = BillStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null
) {
    val subtotal: Double
        get() = items.sumOf { it.grossAmount }

    val totalDiscount: Double
        get() = items.sumOf { it.discountAmount }

    val totalTaxableAmount: Double
        get() = items.sumOf { it.taxableAmount }

    val totalTax: Double
        get() = items.sumOf { it.taxAmount }

    val grandTotal: Double
        get() = items.sumOf { it.lineTotal }

    val formattedGrandTotal: String get() = "%.2f".format(grandTotal)
    val formattedTotalTax: String get() = "%.2f".format(totalTax)
    val formattedSubtotal: String get() = "%.2f".format(subtotal)

    val isEmpty: Boolean
        get() = items.isEmpty()

    val isPaid: Boolean
        get() = status == BillStatus.PAID

    val isOverdue: Boolean
        get() = dueAt != null && System.currentTimeMillis() > dueAt && !isPaid
}
