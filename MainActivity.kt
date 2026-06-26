package com.example.app

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.app.databinding.ActivityMainBinding
import com.example.app.models.Bill
import com.example.app.models.BillItem
import com.example.app.models.BillStatus
import com.example.app.models.Customer
import com.example.app.ui.AddBillFragment
import com.example.app.ui.AddCustomerFragment
import com.example.app.ui.BillListFragment
import com.example.app.ui.BillViewModel
import com.example.app.ui.CustomerListFragment

/**
 * MainActivity — host activity.
 *
 * Responsibilities:
 * ─ Owns [BillViewModel] (shared across all fragments via activityViewModels)
 * ─ Manages the fragment back stack and bottom navigation bar
 * ─ Shows/hides [BottomNavigationView] based on back-stack depth:
 *     depth == 0  → root tabs visible
 *     depth  > 0  → detail screens (Add Bill / Add Customer) — nav hidden
 * ─ Seeds sample data on first install
 *
 * Navigation contract:
 *   [BillListFragment]     → root tab, no back stack
 *   [CustomerListFragment] → root tab, no back stack
 *   [AddBillFragment]      → pushed with back stack, hides bottom nav
 *   [AddCustomerFragment]  → pushed with back stack, hides bottom nav
 */
class MainActivity : AppCompatActivity() {

    val viewModel: BillViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    // Track the active root tab so we restore it after popping back from a detail screen
    private var activeRootTab = R.id.navInvoices

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()
        setupBackStackListener()

        if (savedInstanceState == null) {
            loadRootFragment(BillListFragment(), R.id.navInvoices)
        }

        seedOnFirstRun()
    }

    // ─── Bottom Navigation ────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navInvoices -> {
                    if (activeRootTab != R.id.navInvoices) {
                        activeRootTab = R.id.navInvoices
                        viewModel.clearAllFilters()
                        loadRootFragment(BillListFragment(), R.id.navInvoices)
                    }
                    true
                }
                R.id.navCustomers -> {
                    if (activeRootTab != R.id.navCustomers) {
                        activeRootTab = R.id.navCustomers
                        loadRootFragment(CustomerListFragment(), R.id.navCustomers)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Listen to back-stack changes to show/hide the bottom nav bar.
     * Root fragments (depth 0) show the nav; detail screens hide it.
     */
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            val depth = supportFragmentManager.backStackEntryCount
            binding.bottomNav.visibility = if (depth == 0) View.VISIBLE else View.GONE
        }
    }

    // ─── Navigation API (called by fragments) ─────────────────────────────────

    /** Replace the fragment container with a root-tab fragment (no back stack). */
    private fun loadRootFragment(fragment: Fragment, tabId: Int) {
        // Clear any detail-screen back stack first
        repeat(supportFragmentManager.backStackEntryCount) {
            supportFragmentManager.popBackStackImmediate()
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        binding.bottomNav.selectedItemId = tabId
    }

    /** Push a detail screen on top of the current tab (adds to back stack). */
    fun navigateTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun openAddBill()                        = navigateTo(AddBillFragment())
    fun openAddCustomer(customer: Customer? = null) =
        navigateTo(if (customer != null) AddCustomerFragment.forEdit(customer) else AddCustomerFragment())

    /** Called from [CustomerListFragment] "View Invoices" dialog action. */
    fun switchToInvoices() {
        activeRootTab = R.id.navInvoices
        loadRootFragment(BillListFragment(), R.id.navInvoices)
        // The customer filter is already set on the ViewModel before this is called
    }

    // ─── Seed data ────────────────────────────────────────────────────────────

    private fun seedOnFirstRun() {
        viewModel.customers.observe(this) { customers ->
            if (customers.isNotEmpty()) return@observe     // already seeded

            val c1 = Customer(
                name = "Riya Sharma", email = "riya.sharma@example.com",
                phone = "+91 98765 43210", address = "B-12, Sector 45",
                city = "Gurugram", state = "Haryana", postalCode = "122003",
                country = "India", gstin = "06AABCU9603R1ZP"
            )
            val c2 = Customer(
                name = "Arjun Mehta", email = "arjun@mehta.co",
                phone = "+91 77001 22334", address = "404, Brigade Road",
                city = "Bengaluru", state = "Karnataka", postalCode = "560025",
                country = "India"
            )
            val c3 = Customer(
                name = "Priya Nair", email = "priya.nair@outlook.com",
                phone = "+91 90000 11223", city = "Kochi",
                state = "Kerala", country = "India"
            )

            viewModel.saveCustomer(c1)
            viewModel.saveCustomer(c2)
            viewModel.saveCustomer(c3)

            viewModel.saveBill(sampleBill(c1, "INV-2026-0001", BillStatus.SENT,
                dueOffset = +15L))
            viewModel.saveBill(sampleBill(c2, "INV-2026-0002", BillStatus.PAID,
                dueOffset = -7L))
            viewModel.saveBill(sampleBill(c1, "INV-2026-0003", BillStatus.DRAFT,
                dueOffset = null))
            viewModel.saveBill(sampleBill(c3, "INV-2026-0004", BillStatus.OVERDUE,
                dueOffset = -3L))
        }
    }

    private fun sampleBill(
        customer: Customer,
        number: String,
        status: BillStatus,
        dueOffset: Long?           // days from now; null = no due date
    ) = Bill(
        invoiceNumber = number,
        customer = customer,
        items = listOf(
            BillItem(name = "Website Development", description = "5-page responsive site",
                quantity = 1.0, unitPrice = 45_000.0, taxRatePercent = 18.0),
            BillItem(name = "Logo Design", description = "Brand identity package",
                quantity = 1.0, unitPrice = 8_500.0, discountAmount = 500.0, taxRatePercent = 18.0),
            BillItem(name = "Hosting (Annual)", quantity = 1.0, unit = "yr",
                unitPrice = 6_000.0, taxRatePercent = 18.0)
        ),
        status = status,
        notes = "Payment due within 15 days via bank transfer or UPI.",
        termsAndConditions = "Late payments attract 2% monthly interest.",
        dueAt = dueOffset?.let { System.currentTimeMillis() + it * 24 * 60 * 60 * 1000 }
    )
}
