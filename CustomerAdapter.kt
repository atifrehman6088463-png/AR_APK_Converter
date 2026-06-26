package com.example.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app.databinding.ItemCustomerBinding
import com.example.app.models.Customer

/**
 * CustomerAdapter
 *
 * [ListAdapter] backed by [DiffUtil] — only changed rows rebind.
 *
 * Each row shows:
 * ─ Avatar circle with the customer's initial letter
 * ─ Display name + GSTIN badge (if present)
 * ─ Email and phone (when available)
 * ─ Formatted address snippet
 */
class CustomerAdapter(
    private val onItemClick: (Customer) -> Unit
) : ListAdapter<Customer, CustomerAdapter.CustomerViewHolder>(DiffCallback) {

    fun getCustomerAt(position: Int): Customer = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val binding = ItemCustomerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CustomerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) =
        holder.bind(getItem(position))

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    inner class CustomerViewHolder(
        private val b: ItemCustomerBinding
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(customer: Customer) {
            // Avatar: first letter of name, coloured by hash so each customer
            // gets a consistent (but varied) colour across sessions.
            val initial = customer.name.firstOrNull()?.uppercaseChar() ?: '?'
            b.tvAvatar.text = initial.toString()
            b.tvAvatar.backgroundTintList = ColorStateList.valueOf(avatarColor(customer.id))

            b.tvCustomerName.text = customer.displayName

            // GSTIN badge
            if (customer.gstin.isNotBlank()) {
                b.tvGstin.text = customer.gstin
                b.tvGstin.visibility = android.view.View.VISIBLE
            } else {
                b.tvGstin.visibility = android.view.View.GONE
            }

            // Contact line
            val contact = listOfNotNull(
                customer.email.takeIf { it.isNotBlank() },
                customer.phone.takeIf { it.isNotBlank() }
            ).joinToString("  ·  ")
            b.tvContact.text = contact.ifBlank { "No contact info" }

            // Address snippet
            val address = listOfNotNull(
                customer.city.takeIf { it.isNotBlank() },
                customer.state.takeIf { it.isNotBlank() },
                customer.country.takeIf { it.isNotBlank() }
            ).joinToString(", ")
            b.tvAddress.text = address.ifBlank { "" }
            b.tvAddress.visibility =
                if (address.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            b.root.setOnClickListener { onItemClick(customer) }
        }
    }

    // ─── DiffUtil ─────────────────────────────────────────────────────────────

    companion object DiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(old: Customer, new: Customer) = old.id == new.id
        override fun areContentsTheSame(old: Customer, new: Customer) = old == new
    }
}

// ─── Avatar colour helpers ────────────────────────────────────────────────────

private val AVATAR_COLORS = listOf(
    Color.parseColor("#1A237E"), Color.parseColor("#311B92"),
    Color.parseColor("#1B5E20"), Color.parseColor("#B71C1C"),
    Color.parseColor("#E65100"), Color.parseColor("#006064"),
    Color.parseColor("#4A148C"), Color.parseColor("#0D47A1"),
    Color.parseColor("#33691E"), Color.parseColor("#880E4F")
)

private fun avatarColor(id: String): Int =
    AVATAR_COLORS[Math.abs(id.hashCode()) % AVATAR_COLORS.size]
