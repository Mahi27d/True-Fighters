package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFeesTab(
    invoices: List<FeeInvoice>,
    payments: List<Payment>,
    feePlans: List<FeePlan>,
    branches: List<Branch>,
    programs: List<Program>,
    students: List<Student>,
    onCreateFeePlan: (FeePlan) -> Unit,
    onUpdateFeePlan: (FeePlan) -> Unit,
    onToggleFeePlanStatus: (String, Boolean) -> Unit,
    onGenerateInvoice: (FeeInvoice) -> Unit,
    onRecordPayment: (Payment) -> Unit,
    onRefundPayment: (String, String) -> Unit
) {
    var feeSubTab by remember { mutableStateOf(0) } // 0: Invoices, 1: Fee Plans, 2: Collections, 3: Overdue
    var searchQuery by remember { mutableStateOf("") }
    var branchFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") }

    // Dialog state
    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var planToEdit by remember { mutableStateOf<FeePlan?>(null) }
    var showCreateInvoiceDialog by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var selectedInvoiceForPayment by remember { mutableStateOf<FeeInvoice?>(null) }
    var paymentToRefund by remember { mutableStateOf<Payment?>(null) }
    var planToToggleStatus by remember { mutableStateOf<FeePlan?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    // Aggregations
    val totalCollectionsPaise = payments.filter { it.status != "refunded" }.sumOf { it.amountPaise }
    val totalOutstandingPaise = invoices.sumOf { it.balancePaise }
    val overdueInvoicesCount = invoices.count { it.isOverdue || (it.status != "paid" && it.dueDate.isNotBlank() && it.dueDate < "2026-10-24") }

    val filteredInvoices = invoices.filter { inv ->
        (branchFilter == "ALL" || inv.branchId == branchFilter) &&
        (statusFilter == "ALL" || inv.effectiveStatus.equals(statusFilter, ignoreCase = true)) &&
        (searchQuery.isBlank() || inv.studentName.contains(searchQuery, ignoreCase = true) || inv.period.contains(searchQuery, ignoreCase = true) || inv.invoiceNumber.contains(searchQuery, ignoreCase = true))
    }

    val filteredPlans = feePlans.filter { plan ->
        (branchFilter == "ALL" || plan.branchId == branchFilter || plan.branchIds.contains(branchFilter) || plan.branchId.isBlank()) &&
        (searchQuery.isBlank() || plan.effectiveName.contains(searchQuery, ignoreCase = true) || plan.programName.contains(searchQuery, ignoreCase = true))
    }

    val filteredPayments = payments.filter { pay ->
        (branchFilter == "ALL" || pay.branchId == branchFilter) &&
        (searchQuery.isBlank() || pay.studentName.contains(searchQuery, ignoreCase = true) || pay.providerReference.contains(searchQuery, ignoreCase = true) || pay.paymentNumber.contains(searchQuery, ignoreCase = true))
    }

    val overdueInvoices = invoices.filter { it.isOverdue || (it.status != "paid" && it.balancePaise > 0L) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Fees, Ledger & Invoicing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = "Manage plans, issue invoices, and record verified collections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { showRecordPaymentDialog = true },
                        modifier = Modifier.testTag("admin_record_fee_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Text("Record Fee", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { showCreateInvoiceDialog = true },
                        modifier = Modifier.testTag("admin_create_invoice_button")
                    ) {
                        Text("+ Invoice", fontSize = 11.sp)
                    }
                }
            }
        }

        // Stats summary row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    title = "Total Collections",
                    value = "₹${"%,.0f".format(totalCollectionsPaise / 100.0)}",
                    icon = Icons.Default.CurrencyRupee,
                    iconTint = SuccessGreen,
                    subtitle = "${payments.count { it.status != "refunded" }} receipts",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Outstanding Dues",
                    value = "₹${"%,.0f".format(totalOutstandingPaise / 100.0)}",
                    icon = Icons.Default.ReceiptLong,
                    iconTint = RedPrimary,
                    subtitle = "$overdueInvoicesCount overdue",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Fee Plans",
                    value = "${feePlans.size}",
                    icon = Icons.Default.Tune,
                    iconTint = GoldBelt,
                    subtitle = "${feePlans.count { it.isPlanActive }} active",
                    modifier = Modifier.weight(0.9f)
                )
            }
        }

        // Notification banner
        actionMessage?.let { msg ->
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = msg, color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        IconButton(onClick = { actionMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = SuccessGreen)
                        }
                    }
                }
            }
        }

        // Sub Tabs
        item {
            TabRow(
                selectedTabIndex = feeSubTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RedPrimary
            ) {
                Tab(selected = feeSubTab == 0, onClick = { feeSubTab = 0 }, text = { Text("Invoices (${filteredInvoices.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = feeSubTab == 1, onClick = { feeSubTab = 1 }, text = { Text("Fee Plans (${filteredPlans.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = feeSubTab == 2, onClick = { feeSubTab = 2 }, text = { Text("Ledger (${filteredPayments.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = feeSubTab == 3, onClick = { feeSubTab = 3 }, text = { Text("Overdue (${overdueInvoices.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
            }
        }

        // Search & Branch Filter Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by student, ref, period...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    singleLine = true
                )

                // Branch filter
                var branchExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { branchExpanded = true },
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(
                            text = if (branchFilter == "ALL") "All Branches" else branches.firstOrNull { it.effectiveId == branchFilter }?.effectiveName?.take(12) ?: branchFilter,
                            fontSize = 11.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All Branches") },
                            onClick = { branchFilter = "ALL"; branchExpanded = false }
                        )
                        branches.forEach { b ->
                            DropdownMenuItem(
                                text = { Text("${b.effectiveName} (${b.effectiveCode})") },
                                onClick = { branchFilter = b.effectiveId; branchExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        // TAB 0: INVOICES
        if (feeSubTab == 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Student Invoices (${filteredInvoices.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { showCreateInvoiceDialog = true }) {
                        Text("+ Generate Invoice", fontSize = 11.sp)
                    }
                }
            }

            if (filteredInvoices.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No invoices matching the current filter criteria.", color = TextMuted)
                        }
                    }
                }
            }

            items(filteredInvoices) { inv ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("invoice_card_${inv.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = inv.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(text = "Invoice: ${inv.invoiceNumber.ifBlank { inv.id }} • ${inv.period}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "Branch: ${inv.branchName.ifBlank { inv.branchId }}", fontSize = 11.sp, color = GoldBelt)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "₹${"%,.2f".format(inv.amountPaise / 100.0)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                StatusBadge(status = inv.status)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Paid: ₹${"%,.2f".format(inv.paidPaise / 100.0)}", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                                Text(text = "Balance: ₹${"%,.2f".format(inv.balancePaise / 100.0)}", fontSize = 12.sp, color = if (inv.balancePaise > 0) RedPrimary else TextMuted, fontWeight = FontWeight.Bold)
                                Text(text = "Due Date: ${inv.dueDate.ifBlank { "Not specified" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (inv.balancePaise > 0L) {
                                Button(
                                    onClick = {
                                        selectedInvoiceForPayment = inv
                                        showRecordPaymentDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    modifier = Modifier.testTag("record_payment_for_${inv.id}")
                                ) {
                                    Text("Record Payment", fontSize = 11.sp)
                                }
                            }
                        }

                        if (inv.itemizedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Itemized Breakdown:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inv.itemizedItems.forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "• ${item.title}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "₹${"%,.2f".format(item.amountPaise / 100.0)}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB 1: FEE PLANS
        if (feeSubTab == 1) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Academy Fee Plans (${filteredPlans.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { showCreatePlanDialog = true },
                        modifier = Modifier.testTag("admin_add_fee_plan_button")
                    ) {
                        Text("+ Create Plan", fontSize = 11.sp)
                    }
                }
            }

            if (filteredPlans.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No fee plans defined. Click '+ Create Plan' to add one.", color = TextMuted)
                        }
                    }
                }
            }

            items(filteredPlans) { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("fee_plan_card_${plan.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = plan.effectiveName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (plan.programName.isNotBlank()) {
                                    Text(text = "Program: ${plan.programName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(text = "Branch: ${plan.branchName.ifBlank { "All Dojo Locations" }}", fontSize = 11.sp, color = GoldBelt)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${"%,.2f".format(plan.amountPaise / 100.0)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(text = "/ ${plan.effectiveFrequency}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                StatusBadge(status = if (plan.isPlanActive) "active" else "inactive")
                            }
                        }

                        if (plan.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = plan.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text(text = "Due Day: ${plan.dueDay}th of billing period", fontSize = 11.sp, color = TextMuted)

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { planToEdit = plan },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Edit Plan", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { planToToggleStatus = plan },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (plan.isPlanActive) MaterialTheme.colorScheme.error else SuccessGreen
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (plan.isPlanActive) "Deactivate" else "Activate", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // TAB 2: COLLECTIONS LEDGER
        if (feeSubTab == 2) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Verified Payment Collections (${filteredPayments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { showRecordPaymentDialog = true }) {
                        Text("+ Record Payment", fontSize = 11.sp)
                    }
                }
            }

            if (filteredPayments.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No payment receipts recorded yet.", color = TextMuted)
                        }
                    }
                }
            }

            items(filteredPayments) { pay ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("payment_card_${pay.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = pay.studentName.ifBlank { "Student Payment" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(text = "Receipt: ${pay.paymentNumber.ifBlank { pay.id }} • Method: ${pay.method.uppercase()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (pay.providerReference.isNotBlank()) {
                                    Text(text = "Ref / UTR: ${pay.providerReference}", fontSize = 11.sp, color = GoldBelt)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${"%,.2f".format(pay.amountPaise / 100.0)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (pay.status == "refunded") TextMuted else SuccessGreen
                                )
                                StatusBadge(status = pay.status)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Recorded by: ${pay.recordedBy} • Date: ${pay.effectiveDate}", fontSize = 11.sp, color = TextMuted)
                            if (pay.status != "refunded") {
                                TextButton(
                                    onClick = { paymentToRefund = pay },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Refund", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB 3: OVERDUE ACCOUNTS
        if (feeSubTab == 3) {
            item {
                Text(text = "Overdue Accounts & Dues (${overdueInvoices.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (overdueInvoices.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("All student accounts are currently paid up to date!", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            items(overdueInvoices) { inv ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RedPrimary.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = inv.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "Period: ${inv.period} • Due Date: ${inv.dueDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            Text(text = "Outstanding: ₹${"%,.2f".format(inv.balancePaise / 100.0)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RedPrimary)
                        }
                        Button(
                            onClick = {
                                selectedInvoiceForPayment = inv
                                showRecordPaymentDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                        ) {
                            Text("Collect Fee", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // DIALOG: CREATE OR EDIT FEE PLAN
    if (showCreatePlanDialog || planToEdit != null) {
        val editing = planToEdit != null
        val target = planToEdit
        var planName by remember(target) { mutableStateOf(target?.effectiveName ?: "") }
        var planAmount by remember(target) { mutableStateOf(if (target != null) (target.amountPaise / 100.0).toString() else "2500") }
        var billingFrequency by remember(target) { mutableStateOf(target?.effectiveFrequency ?: "Monthly") }
        var selectedBranchId by remember(target) { mutableStateOf(target?.branchId ?: branches.firstOrNull()?.effectiveId ?: "") }
        var selectedProgramId by remember(target) { mutableStateOf(target?.programId ?: programs.firstOrNull()?.effectiveId ?: "") }
        var dueDay by remember(target) { mutableStateOf(target?.dueDay?.toString() ?: "10") }
        var planDesc by remember(target) { mutableStateOf(target?.description ?: "") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showCreatePlanDialog = false
                planToEdit = null
            },
            title = { Text(if (editing) "Edit Fee Plan" else "Create New Fee Plan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = planName,
                        onValueChange = { planName = it; errorMsg = null },
                        label = { Text("Plan Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("plan_name_input")
                    )
                    OutlinedTextField(
                        value = planAmount,
                        onValueChange = { planAmount = it; errorMsg = null },
                        label = { Text("Amount (₹) *") },
                        modifier = Modifier.fillMaxWidth().testTag("plan_amount_input"),
                        prefix = { Text("₹ ") }
                    )
                    // Frequency selector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Monthly", "Quarterly", "Annual").forEach { freq ->
                            FilterChip(
                                selected = billingFrequency == freq,
                                onClick = { billingFrequency = freq },
                                label = { Text(freq, fontSize = 11.sp) }
                            )
                        }
                    }
                    // Branch selector
                    var branchMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { branchMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Branch: ${branches.firstOrNull { it.effectiveId == selectedBranchId }?.effectiveName ?: "Select Branch"}")
                        }
                        DropdownMenu(expanded = branchMenuExpanded, onDismissRequest = { branchMenuExpanded = false }) {
                            branches.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b.effectiveName) },
                                    onClick = { selectedBranchId = b.effectiveId; branchMenuExpanded = false }
                                )
                            }
                        }
                    }
                    // Program selector
                    var progMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { progMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Program: ${programs.firstOrNull { it.effectiveId == selectedProgramId }?.effectiveName ?: "All Programs"}")
                        }
                        DropdownMenu(expanded = progMenuExpanded, onDismissRequest = { progMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("All Programs") }, onClick = { selectedProgramId = ""; progMenuExpanded = false })
                            programs.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.effectiveName) },
                                    onClick = { selectedProgramId = p.effectiveId; progMenuExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it },
                        label = { Text("Due Day of Month (e.g. 10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = planDesc,
                        onValueChange = { planDesc = it },
                        label = { Text("Description / Inclusions") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMsg?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountDouble = planAmount.toDoubleOrNull()
                        val dayInt = dueDay.toIntOrNull() ?: 10
                        when {
                            planName.isBlank() -> errorMsg = "Plan name is required."
                            amountDouble == null || amountDouble <= 0.0 -> errorMsg = "Please enter a valid amount greater than zero."
                            else -> {
                                val amountPaise = (amountDouble * 100).toLong()
                                val branchObj = branches.firstOrNull { it.effectiveId == selectedBranchId }
                                val progObj = programs.firstOrNull { it.effectiveId == selectedProgramId }
                                val plan = FeePlan(
                                    id = target?.id ?: "fee_${UUID.randomUUID().toString().take(8)}",
                                    planId = target?.planId ?: "fee_${UUID.randomUUID().toString().take(8)}",
                                    name = planName.trim(),
                                    planName = planName.trim(),
                                    branchId = selectedBranchId,
                                    branchName = branchObj?.effectiveName ?: "",
                                    branchIds = if (selectedBranchId.isNotBlank()) listOf(selectedBranchId) else emptyList(),
                                    programId = selectedProgramId,
                                    programName = progObj?.effectiveName ?: "",
                                    description = planDesc.trim(),
                                    amountPaise = amountPaise,
                                    billingPeriod = billingFrequency,
                                    billingFrequency = billingFrequency,
                                    dueDay = dayInt,
                                    status = target?.status ?: "active",
                                    active = target?.active ?: true
                                )
                                if (editing) onUpdateFeePlan(plan) else onCreateFeePlan(plan)
                                actionMessage = "Fee plan '$planName' saved successfully."
                                showCreatePlanDialog = false
                                planToEdit = null
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_fee_plan_button")
                ) {
                    Text("Save Plan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlanDialog = false
                    planToEdit = null
                }) { Text("Cancel") }
            }
        )
    }

    // DIALOG: GENERATE INVOICE
    if (showCreateInvoiceDialog) {
        var selectedStudentId by remember { mutableStateOf(students.firstOrNull()?.id ?: "") }
        var period by remember { mutableStateOf("November 2026") }
        var amountRupees by remember { mutableStateOf("2500") }
        var dueDate by remember { mutableStateOf("2026-11-10") }
        var selectedPlanId by remember { mutableStateOf(feePlans.firstOrNull()?.id ?: "") }
        var notes by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val studentObj = students.firstOrNull { it.id == selectedStudentId }

        AlertDialog(
            onDismissRequest = { showCreateInvoiceDialog = false },
            title = { Text("Generate Student Fee Invoice", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Student Selector
                    var studentMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { studentMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Student: ${studentObj?.fullName ?: "Select Student"}")
                        }
                        DropdownMenu(expanded = studentMenuExpanded, onDismissRequest = { studentMenuExpanded = false }) {
                            students.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.fullName} (${s.branchName})") },
                                    onClick = {
                                        selectedStudentId = s.id
                                        studentMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Quick Fee Plan Selector
                    var planMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { planMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            val p = feePlans.firstOrNull { it.effectiveId == selectedPlanId }
                            Text("Fee Plan: ${p?.effectiveName ?: "Custom Fee"}")
                        }
                        DropdownMenu(expanded = planMenuExpanded, onDismissRequest = { planMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Custom Amount") }, onClick = { selectedPlanId = ""; planMenuExpanded = false })
                            feePlans.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.effectiveName} (₹${p.amountPaise / 100.0})") },
                                    onClick = {
                                        selectedPlanId = p.effectiveId
                                        amountRupees = (p.amountPaise / 100.0).toString()
                                        planMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = period,
                        onValueChange = { period = it; errorMsg = null },
                        label = { Text("Billing Period (e.g. November 2026) *") },
                        modifier = Modifier.fillMaxWidth().testTag("invoice_period_input")
                    )

                    OutlinedTextField(
                        value = amountRupees,
                        onValueChange = { amountRupees = it; errorMsg = null },
                        label = { Text("Total Amount (₹) *") },
                        modifier = Modifier.fillMaxWidth().testTag("invoice_amount_input"),
                        prefix = { Text("₹ ") }
                    )

                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMsg?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountDouble = amountRupees.toDoubleOrNull()
                        val s = students.firstOrNull { it.id == selectedStudentId }
                        when {
                            s == null -> errorMsg = "Please select an enrolled student."
                            period.isBlank() -> errorMsg = "Billing period is required."
                            amountDouble == null || amountDouble <= 0.0 -> errorMsg = "Enter a valid positive fee amount."
                            else -> {
                                val amountPaise = (amountDouble * 100).toLong()
                                val planObj = feePlans.firstOrNull { it.effectiveId == selectedPlanId }
                                val invoice = FeeInvoice(
                                    id = "inv_${s.id}_${period.replace(" ", "_").lowercase()}",
                                    invoiceNumber = "TF-INV-${System.currentTimeMillis() % 100000}",
                                    branchId = s.branchId,
                                    branchName = s.branchName,
                                    studentId = s.id,
                                    studentUserId = s.userId,
                                    studentName = s.fullName,
                                    feePlanId = selectedPlanId,
                                    feePlanName = planObj?.effectiveName ?: "Tuition Fee",
                                    period = period.trim(),
                                    amountPaise = amountPaise,
                                    paidPaise = 0L,
                                    dueDate = dueDate.trim(),
                                    status = "pending",
                                    notes = notes.trim(),
                                    itemizedItems = listOf(InvoiceItem(title = planObj?.effectiveName ?: "Tuition Fee", amountPaise = amountPaise))
                                )
                                onGenerateInvoice(invoice)
                                actionMessage = "Invoice for ${s.fullName} ($period) generated."
                                showCreateInvoiceDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_invoice_button")
                ) {
                    Text("Generate Invoice")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateInvoiceDialog = false }) { Text("Cancel") }
            }
        )
    }

    // DIALOG: RECORD PAYMENT
    if (showRecordPaymentDialog) {
        var selectedInvoiceId by remember { mutableStateOf(selectedInvoiceForPayment?.id ?: invoices.firstOrNull { it.balancePaise > 0L }?.id ?: "") }
        var paymentMethod by remember { mutableStateOf("cash") } // cash, bank_transfer, upi, online
        var providerRef by remember { mutableStateOf("UTR-${System.currentTimeMillis() % 1000000}") }
        var paymentDate by remember { mutableStateOf("2026-11-01") }
        var notes by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val activeInv = invoices.firstOrNull { it.id == selectedInvoiceId } ?: selectedInvoiceForPayment
        var amountRupees by remember(activeInv) { mutableStateOf(if (activeInv != null) (activeInv.balancePaise / 100.0).toString() else "2500") }

        AlertDialog(
            onDismissRequest = {
                showRecordPaymentDialog = false
                selectedInvoiceForPayment = null
            },
            title = { Text("Record Verified Fee Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Invoice selector
                    var invMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { invMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (activeInv != null) "${activeInv.studentName} — ${activeInv.period} (Due: ₹${activeInv.balancePaise / 100.0})" else "Select Invoice")
                        }
                        DropdownMenu(expanded = invMenuExpanded, onDismissRequest = { invMenuExpanded = false }) {
                            invoices.forEach { inv ->
                                DropdownMenuItem(
                                    text = { Text("${inv.studentName} — ${inv.period} (Bal: ₹${inv.balancePaise / 100.0})") },
                                    onClick = {
                                        selectedInvoiceId = inv.id
                                        selectedInvoiceForPayment = inv
                                        amountRupees = (inv.balancePaise / 100.0).toString()
                                        invMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Payment Method selector
                    Text("Payment Method:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cash", "upi", "bank_transfer", "online").forEach { m ->
                            FilterChip(
                                selected = paymentMethod == m,
                                onClick = { paymentMethod = m },
                                label = { Text(m.replace("_", " ").uppercase(), fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountRupees,
                        onValueChange = { amountRupees = it; errorMsg = null },
                        label = { Text("Payment Amount (₹) *") },
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
                        prefix = { Text("₹ ") }
                    )

                    OutlinedTextField(
                        value = providerRef,
                        onValueChange = { providerRef = it; errorMsg = null },
                        label = { Text("Reference / UTR / Receipt # *") },
                        modifier = Modifier.fillMaxWidth().testTag("payment_reference_input")
                    )

                    OutlinedTextField(
                        value = paymentDate,
                        onValueChange = { paymentDate = it },
                        label = { Text("Payment Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Staff Verification Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMsg?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountDouble = amountRupees.toDoubleOrNull()
                        val inv = invoices.firstOrNull { it.id == selectedInvoiceId } ?: selectedInvoiceForPayment
                        when {
                            amountDouble == null || amountDouble <= 0.0 -> errorMsg = "Enter a valid payment amount greater than zero."
                            providerRef.isBlank() -> errorMsg = "Reference or UTR number is required."
                            inv == null -> errorMsg = "Please associate with an invoice."
                            else -> {
                                val amountPaise = (amountDouble * 100).toLong()
                                val payment = Payment(
                                    id = "pay_${UUID.randomUUID().toString().take(8)}",
                                    paymentNumber = "TF-REC-${System.currentTimeMillis() % 100000}",
                                    branchId = inv.branchId,
                                    branchName = inv.branchName,
                                    invoiceId = inv.id,
                                    studentId = inv.studentId,
                                    studentUserId = inv.studentUserId,
                                    studentName = inv.studentName,
                                    amountPaise = amountPaise,
                                    paymentDate = paymentDate.trim(),
                                    method = paymentMethod,
                                    providerReference = providerRef.trim(),
                                    status = "verified",
                                    notes = notes.trim()
                                )
                                onRecordPayment(payment)
                                actionMessage = "Recorded ${paymentMethod.uppercase()} payment of ₹$amountRupees for ${inv.studentName}."
                                showRecordPaymentDialog = false
                                selectedInvoiceForPayment = null
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_record_payment_button")
                ) {
                    Text("Record Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRecordPaymentDialog = false
                    selectedInvoiceForPayment = null
                }) { Text("Cancel") }
            }
        )
    }

    // DIALOG: REFUND PAYMENT
    paymentToRefund?.let { pay ->
        var refundReason by remember { mutableStateOf("Student request / billing correction") }

        AlertDialog(
            onDismissRequest = { paymentToRefund = null },
            title = { Text("Refund Payment?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Refund ₹${"%,.2f".format(pay.amountPaise / 100.0)} for ${pay.studentName}?")
                    Text("Receipt: ${pay.paymentNumber.ifBlank { pay.id }} (Ref: ${pay.providerReference})", fontSize = 12.sp, color = TextMuted)
                    Text("This will deduct the paid amount from the invoice and record an audit log.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = refundReason,
                        onValueChange = { refundReason = it },
                        label = { Text("Reason for Refund") },
                        modifier = Modifier.fillMaxWidth().testTag("refund_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRefundPayment(pay.id, refundReason)
                        actionMessage = "Payment refunded successfully."
                        paymentToRefund = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_refund_button")
                ) {
                    Text("Process Refund")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToRefund = null }) { Text("Cancel") }
            }
        )
    }

    // DIALOG: TOGGLE PLAN STATUS
    planToToggleStatus?.let { plan ->
        val isActive = plan.isPlanActive
        val nextAction = if (isActive) "Deactivate" else "Activate"

        AlertDialog(
            onDismissRequest = { planToToggleStatus = null },
            title = { Text("$nextAction Fee Plan?") },
            text = {
                Text(
                    if (isActive)
                        "Deactivate '${plan.effectiveName}'? It will no longer be available for new student enrollments."
                    else
                        "Activate '${plan.effectiveName}' for new invoices and student fee plans?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleFeePlanStatus(plan.effectiveId, isActive)
                        actionMessage = "Fee plan '${plan.effectiveName}' ${nextAction.lowercase()}d."
                        planToToggleStatus = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.error else SuccessGreen
                    )
                ) {
                    Text("Confirm $nextAction")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToToggleStatus = null }) { Text("Cancel") }
            }
        )
    }
}
