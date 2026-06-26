package com.example.app.data.db

import com.example.app.data.db.entity.BillEntity
import com.example.app.data.db.entity.BillItemEntity
import com.example.app.data.db.entity.CustomerEntity
import com.example.app.models.Bill
import com.example.app.models.BillItem
import com.example.app.models.BillStatus
import com.example.app.models.Customer

// ─────────────────────────────────────────────────────────────────────────────
// Customer  ↔  CustomerEntity
// ─────────────────────────────────────────────────────────────────────────────

fun CustomerEntity.toDomain(): Customer = Customer(
    id          = id,
    name        = name,
    email       = email,
    phone       = phone,
    address     = address,
    city        = city,
    state       = state,
    postalCode  = postalCode,
    country     = country,
    gstin       = gstin,
    createdAt   = createdAt
)

fun Customer.toEntity(): CustomerEntity = CustomerEntity(
    id          = id,
    name        = name,
    email       = email,
    phone       = phone,
    address     = address,
    city        = city,
    state       = state,
    postalCode  = postalCode,
    country     = country,
    gstin       = gstin,
    createdAt   = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
// BillItem  ↔  BillItemEntity
// ─────────────────────────────────────────────────────────────────────────────

fun BillItemEntity.toDomain(): BillItem = BillItem(
    id             = id,
    name           = name,
    description    = description,
    hsnSac         = hsnSac,
    quantity       = quantity,
    unit           = unit,
    unitPrice      = unitPrice,
    discountAmount = discountAmount,
    taxRatePercent = taxRatePercent
)

fun BillItem.toEntity(billId: String): BillItemEntity = BillItemEntity(
    id             = id,
    billId         = billId,
    name           = name,
    description    = description,
    hsnSac         = hsnSac,
    quantity       = quantity,
    unit           = unit,
    unitPrice      = unitPrice,
    discountAmount = discountAmount,
    taxRatePercent = taxRatePercent
)

// ─────────────────────────────────────────────────────────────────────────────
// Bill  ↔  BillEntity + BillWithDetails
// ─────────────────────────────────────────────────────────────────────────────

fun Bill.toEntity(): BillEntity = BillEntity(
    id                 = id,
    customerId         = customer.id,
    invoiceNumber      = invoiceNumber,
    notes              = notes,
    termsAndConditions = termsAndConditions,
    status             = status.name,
    createdAt          = createdAt,
    dueAt              = dueAt
)

fun BillWithDetails.toDomain(): Bill = Bill(
    id                 = bill.id,
    invoiceNumber      = bill.invoiceNumber,
    customer           = customer.toDomain(),
    items              = items.map { it.toDomain() },
    notes              = bill.notes,
    termsAndConditions = bill.termsAndConditions,
    status             = runCatching { BillStatus.valueOf(bill.status) }.getOrDefault(BillStatus.DRAFT),
    createdAt          = bill.createdAt,
    dueAt              = bill.dueAt
)
