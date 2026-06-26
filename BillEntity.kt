package com.example.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId")]
)
data class BillEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val invoiceNumber: String,
    val notes: String,
    val termsAndConditions: String,
    val status: String,
    val createdAt: Long,
    val dueAt: Long?
)
