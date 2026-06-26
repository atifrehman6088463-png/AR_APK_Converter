package com.example.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId")]
)
data class BillItemEntity(
    @PrimaryKey val id: String,
    val billId: String,
    val name: String,
    val description: String,
    val hsnSac: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val discountAmount: Double,
    val taxRatePercent: Double
)
