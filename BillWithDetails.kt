package com.example.app.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.app.data.db.entity.BillEntity
import com.example.app.data.db.entity.BillItemEntity
import com.example.app.data.db.entity.CustomerEntity

/**
 * Room result class that joins a [BillEntity] with its [CustomerEntity]
 * and all associated [BillItemEntity] rows in a single query.
 *
 * Room resolves the @Relation fields automatically via two extra SELECT
 * statements when @Transaction is used on the DAO query.
 */
data class BillWithDetails(
    @Embedded
    val bill: BillEntity,

    @Relation(
        parentColumn = "customerId",
        entityColumn = "id"
    )
    val customer: CustomerEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "billId"
    )
    val items: List<BillItemEntity>
)
