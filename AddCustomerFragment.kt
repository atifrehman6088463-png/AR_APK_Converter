package com.example.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.app.MainActivity
import com.example.app.databinding.FragmentAddCustomerBinding
import com.example.app.models.Customer
import com.google.android.material.snackbar.Snackbar
import java.util.UUID

/**
 * AddCustomerFragment
 *
 * A form for creating OR editing a [Customer].
 *
 * Pass [ARG_CUSTOMER_ID] in the arguments to pre-fill the form for editing.
 * When saving, calls [BillViewModel.saveCustomer] which upserts via Room's
 * REPLACE strategy — existing records are updated atomically.
 *
 * After a successful save the fragment pops itself off the back stack so the
 * user returns to [CustomerListFragment] (or [AddBillFragment] if launched
 * from the quick-add flow).
 */
class AddCustomerFragment : Fragment() {

    private var _binding: FragmentAddCustomerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillViewModel by activityViewModels()

    // When editing, we keep the original id and createdAt timestamp
    private var editingCustomerId: String? = null
    private var originalCreatedAt: Long = System.currentTimeMillis()

    companion object {
        private const val ARG_CUSTOMER_ID = "customer_id"

        /** Create an instance pre-loaded with an existing customer for editing. */
        fun forEdit(customer: Customer) = AddCustomerFragment().apply {
            arguments = bundleOf(ARG_CUSTOMER_ID to customer.id)
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupButtons()
        loadCustomerIfEditing()
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

    // ─── Pre-fill for edit mode ───────────────────────────────────────────────

    private fun loadCustomerIfEditing() {
        val customerId = arguments?.getString(ARG_CUSTOMER_ID) ?: return

        viewModel.customers.observe(viewLifecycleOwner) { customers ->
            val customer = customers.firstOrNull { it.id == customerId } ?: return@observe
            editingCustomerId  = customer.id
            originalCreatedAt  = customer.createdAt

            binding.toolbar.title = "Edit Customer"
            binding.etName.setText(customer.name)
            binding.etEmail.setText(customer.email)
            binding.etPhone.setText(customer.phone)
            binding.etAddress.setText(customer.address)
            binding.etCity.setText(customer.city)
            binding.etState.setText(customer.state)
            binding.etPostal.setText(customer.postalCode)
            binding.etCountry.setText(customer.country)
            binding.etGstin.setText(customer.gstin)
        }
    }

    // ─── Buttons ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { validateAndSave() }
        binding.btnCancel.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // ─── Validation + Save ────────────────────────────────────────────────────

    private fun validateAndSave() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()

        if (name.isBlank()) {
            binding.tilName.error = "Name is required"
            binding.etName.requestFocus()
            return
        }
        binding.tilName.error = null

        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Enter a valid email address"
            return
        }
        binding.tilEmail.error = null

        val customer = Customer(
            id          = editingCustomerId ?: UUID.randomUUID().toString(),
            name        = name,
            email       = email,
            phone       = binding.etPhone.text?.toString()?.trim().orEmpty(),
            address     = binding.etAddress.text?.toString()?.trim().orEmpty(),
            city        = binding.etCity.text?.toString()?.trim().orEmpty(),
            state       = binding.etState.text?.toString()?.trim().orEmpty(),
            postalCode  = binding.etPostal.text?.toString()?.trim().orEmpty(),
            country     = binding.etCountry.text?.toString()?.trim().orEmpty(),
            gstin       = binding.etGstin.text?.toString()?.trim().orEmpty(),
            createdAt   = originalCreatedAt
        )

        viewModel.saveCustomer(customer)

        // Navigate back — ViewModel emits a snackbar message which BillListFragment/
        // CustomerListFragment will pick up and show after we return.
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }
}
