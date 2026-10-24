package com.example.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.BeltBadge
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsAndAnalyticsTab(
    branches: List<Branch>,
    students: List<Student>,
    programs: List<Program>,
    batches: List<Batch>,
    classSessions: List<ClassSession>,
    attendanceList: List<Attendance>,
    invoices: List<FeeInvoice>,
    payments: List<Payment>,
    gradingAssessments: List<GradingAssessment>,
    certificates: List<Certificate>,
    admissionRequests: List<AdmissionRequest>,
    coaches: List<UserProfile>,
    selectedBranchId: String,
    onBranchSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedReportType by remember { mutableStateOf(0) } // 0: Enrollment, 1: Attendance, 2: Financial, 3: Batches, 4: Gradings, 5: Admissions
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateRange by remember { mutableStateOf("All") } // "All", "Today", "Last 7 Days", "This Month"
    var showExportDialog by remember { mutableStateOf(false) }
    var generatedCsvContent by remember { mutableStateOf("") }
    var generatedCsvTitle by remember { mutableStateOf("") }

    val reportTitles = listOf(
        "Student Enrollment",
        "Attendance Analytics",
        "Fee Collections & Dues",
        "Batch Occupancy",
        "Gradings & Belts",
        "Admissions Pipeline"
    )

    // Current Date strings
    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
    val thisMonthPrefix = remember { todayStr.take(7) } // "YYYY-MM"

    // Branch filter applied to all models
    val filteredStudents = remember(students, selectedBranchId, searchQuery) {
        students.filter { std ->
            (selectedBranchId == "all" || std.branchId == selectedBranchId) &&
            (searchQuery.isBlank() || std.fullName.contains(searchQuery, ignoreCase = true) || std.phoneNumber.contains(searchQuery) || std.currentBelt.contains(searchQuery, ignoreCase = true))
        }
    }

    val filteredAttendance = remember(attendanceList, selectedBranchId, searchQuery, selectedDateRange) {
        attendanceList.filter { att ->
            val branchMatches = selectedBranchId == "all" || att.branchId == selectedBranchId
            val queryMatches = searchQuery.isBlank() || att.studentName.contains(searchQuery, ignoreCase = true) || att.date.contains(searchQuery)
            val dateMatches = when (selectedDateRange) {
                "Today" -> att.date == todayStr
                "This Month" -> att.date.startsWith(thisMonthPrefix)
                else -> true
            }
            branchMatches && queryMatches && dateMatches
        }.sortedByDescending { it.date }
    }

    val filteredInvoices = remember(invoices, selectedBranchId, searchQuery, selectedDateRange) {
        invoices.filter { inv ->
            val branchMatches = selectedBranchId == "all" || inv.branchId == selectedBranchId
            val queryMatches = searchQuery.isBlank() || inv.studentName.contains(searchQuery, ignoreCase = true) || inv.period.contains(searchQuery, ignoreCase = true) || inv.effectiveId.contains(searchQuery, ignoreCase = true)
            branchMatches && queryMatches
        }
    }

    val filteredPayments = remember(payments, selectedBranchId, searchQuery) {
        payments.filter { pmt ->
            val branchMatches = selectedBranchId == "all" || pmt.branchId == selectedBranchId
            val queryMatches = searchQuery.isBlank() || pmt.studentName.contains(searchQuery, ignoreCase = true) || pmt.providerReference.contains(searchQuery, ignoreCase = true) || pmt.method.contains(searchQuery, ignoreCase = true)
            branchMatches && queryMatches
        }.sortedByDescending { it.paymentDate }
    }

    val filteredBatches = remember(batches, selectedBranchId, searchQuery) {
        batches.filter { b ->
            val branchMatches = selectedBranchId == "all" || b.branchId == selectedBranchId
            val queryMatches = searchQuery.isBlank() || b.effectiveName.contains(searchQuery, ignoreCase = true) || b.programName.contains(searchQuery, ignoreCase = true)
            branchMatches && queryMatches
        }
    }

    val filteredGradings = remember(gradingAssessments, selectedBranchId, searchQuery) {
        gradingAssessments.filter { g ->
            val branchMatches = selectedBranchId == "all" || g.branchId == selectedBranchId
            val queryMatches = searchQuery.isBlank() || g.studentName.contains(searchQuery, ignoreCase = true) || g.proposedBeltName.contains(searchQuery, ignoreCase = true) || g.coachName.contains(searchQuery, ignoreCase = true)
            branchMatches && queryMatches
        }.sortedByDescending { it.assessmentDate }
    }

    val filteredAdmissions = remember(admissionRequests, selectedBranchId, searchQuery) {
        admissionRequests.filter { adm ->
            val branchMatches = selectedBranchId == "all" || adm.preferredBranchId == selectedBranchId
            val queryMatches = searchQuery.isBlank() || adm.fullName.contains(searchQuery, ignoreCase = true) || adm.phoneNumber.contains(searchQuery) || adm.email.contains(searchQuery, ignoreCase = true)
            branchMatches && queryMatches
        }.sortedByDescending { it.status }
    }

    // Helper function to prepare CSV export
    fun prepareCsvExport() {
        val branchLabel = if (selectedBranchId == "all") "All_Branches" else branches.firstOrNull { it.effectiveId == selectedBranchId }?.name?.replace(" ", "_") ?: selectedBranchId
        when (selectedReportType) {
            0 -> {
                generatedCsvTitle = "Students_Enrollment_${branchLabel}_$todayStr.csv"
                val sb = StringBuilder()
                sb.append("Student ID,Full Name,Phone,Email,Branch,Program,Current Belt,Status,Admission Date\n")
                filteredStudents.forEach { std ->
                    sb.append("${escapeCsv(std.effectiveId)},${escapeCsv(std.fullName)},${escapeCsv(std.phoneNumber)},${escapeCsv(std.email)},${escapeCsv(std.branchName)},${escapeCsv(std.programName)},${escapeCsv(std.currentBelt)},${escapeCsv(std.status)},${escapeCsv(std.admissionDate)}\n")
                }
                generatedCsvContent = sb.toString()
            }
            1 -> {
                generatedCsvTitle = "Attendance_Report_${branchLabel}_$todayStr.csv"
                val sb = StringBuilder()
                sb.append("Date,Session ID,Batch ID,Student ID,Student Name,Status,Marked By,Branch ID\n")
                filteredAttendance.forEach { att ->
                    sb.append("${escapeCsv(att.date)},${escapeCsv(att.sessionId)},${escapeCsv(att.batchId)},${escapeCsv(att.studentId)},${escapeCsv(att.studentName)},${escapeCsv(att.status)},${escapeCsv(att.markedBy)},${escapeCsv(att.branchId)}\n")
                }
                generatedCsvContent = sb.toString()
            }
            2 -> {
                generatedCsvTitle = "Financial_Invoices_${branchLabel}_$todayStr.csv"
                val sb = StringBuilder()
                sb.append("Invoice ID,Student Name,Period,Amount (INR),Paid (INR),Balance (INR),Status,Due Date,Branch ID\n")
                filteredInvoices.forEach { inv ->
                    val amt = inv.amountPaise / 100.0
                    val paid = inv.paidPaise / 100.0
                    val bal = (inv.amountPaise - inv.paidPaise) / 100.0
                    sb.append("${escapeCsv(inv.effectiveId)},${escapeCsv(inv.studentName)},${escapeCsv(inv.period)},$amt,$paid,$bal,${escapeCsv(inv.status)},${escapeCsv(inv.dueDate)},${escapeCsv(inv.branchId)}\n")
                }
                generatedCsvContent = sb.toString()
            }
            3 -> {
                generatedCsvTitle = "Batch_Occupancy_${branchLabel}_$todayStr.csv"
                val sb = StringBuilder()
                sb.append("Batch ID,Batch Name,Branch,Program,Capacity,Enrolled,Occupancy %,Coach,Schedule,Status\n")
                filteredBatches.forEach { b ->
                    val enrolled = b.studentIds.size
                    val occ = if (b.capacity > 0) ((enrolled * 100) / b.capacity).toInt() else 0
                    sb.append("${escapeCsv(b.effectiveId)},${escapeCsv(b.effectiveName)},${escapeCsv(b.branchName)},${escapeCsv(b.programName)},${b.capacity},$enrolled,$occ%,${escapeCsv(b.coachName)},${escapeCsv(b.schedule)},${escapeCsv(b.status)}\n")
                }
                generatedCsvContent = sb.toString()
            }
            4 -> {
                generatedCsvTitle = "Gradings_Promotions_${branchLabel}_$todayStr.csv"
                val sb = StringBuilder()
                sb.append("Assessment ID,Student Name,Program,Current Belt,Proposed Belt,Score,Grade,Status,Coach,Admin Notes,Certificate ID\n")
                filteredGradings.forEach { g ->
                    sb.append("${escapeCsv(g.effectiveId)},${escapeCsv(g.studentName)},${escapeCsv(g.programName)},${escapeCsv(g.currentBeltName)},${escapeCsv(g.proposedBeltName)},${g.score},${escapeCsv(g.techniqueGrade)},${escapeCsv(g.status)},${escapeCsv(g.coachName)},${escapeCsv(g.adminNotes)},${escapeCsv(g.certificateId)}\n")
                }
                generatedCsvContent = sb.toString()
            }
            5 -> {
                generatedCsvTitle = "Admissions_Pipeline_${branchLabel}_$todayStr.csv"
                val sb = StringBuilder()
                sb.append("Request ID,Full Name,Phone,Email,Preferred Branch,Program,Status,Guardian Name,Guardian Phone,Consent\n")
                filteredAdmissions.forEach { a ->
                    sb.append("${escapeCsv(a.effectiveId)},${escapeCsv(a.fullName)},${escapeCsv(a.phoneNumber)},${escapeCsv(a.email)},${escapeCsv(a.preferredBranchId)},${escapeCsv(a.programId)},${escapeCsv(a.status)},${escapeCsv(a.guardianName)},${escapeCsv(a.guardianPhone)},${a.guardianConsent}\n")
                }
                generatedCsvContent = sb.toString()
            }
        }
        showExportDialog = true
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Academy Analytics & Reports",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time institutional telemetry, audit trails & exportable reports",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { prepareCsvExport() },
                    modifier = Modifier.testTag("export_report_csv_button")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Branch Selection Filter Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Domain, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Branch Filter:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedBranchId == "all",
                            onClick = { onBranchSelected("all") },
                            label = { Text("All Branches (${branches.size})", fontSize = 11.sp) },
                            modifier = Modifier.testTag("report_branch_chip_all")
                        )
                        branches.forEach { b ->
                            FilterChip(
                                selected = selectedBranchId == b.effectiveId,
                                onClick = { onBranchSelected(b.effectiveId) },
                                label = { Text(b.name, fontSize = 11.sp) },
                                modifier = Modifier.testTag("report_branch_chip_${b.effectiveId}")
                            )
                        }
                    }
                }
            }
        }

        // Report Type Selector Tabs
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedReportType,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RedPrimary,
                edgePadding = 0.dp
            ) {
                reportTitles.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedReportType == idx,
                        onClick = { selectedReportType = idx; searchQuery = "" },
                        text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedReportType == idx) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("report_tab_$idx")
                    )
                }
            }
        }

        // Search & Secondary Filter Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search records...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("report_search_input")
                )

                if (selectedReportType == 1) { // Attendance Date filter
                    var dateMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { dateMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(selectedDateRange, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = dateMenuExpanded, onDismissRequest = { dateMenuExpanded = false }) {
                            listOf("All", "Today", "This Month").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { selectedDateRange = opt; dateMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Report Content
        when (selectedReportType) {
            0 -> {
                // 1. Student Enrollment & Branch Distribution
                val activeCount = filteredStudents.count { it.status.lowercase() == "active" }
                val suspendedCount = filteredStudents.count { it.status.lowercase() == "suspended" }
                val inactiveCount = filteredStudents.count { it.status.lowercase() == "inactive" }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(title = "Total Enrolled", value = "${filteredStudents.size}", icon = Icons.Default.Groups, modifier = Modifier.weight(1f))
                        StatCard(title = "Active Students", value = "$activeCount", icon = Icons.Default.CheckCircle, iconTint = SuccessGreen, modifier = Modifier.weight(1f))
                        StatCard(title = "Suspended / Inactive", value = "${suspendedCount + inactiveCount}", icon = Icons.Default.PauseCircle, iconTint = RedPrimary, modifier = Modifier.weight(1f))
                    }
                }

                // Program Breakdown Bar
                item {
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Program Distribution", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            val programGroups = filteredStudents.groupBy { it.programName.ifBlank { "Unassigned" } }
                            programGroups.forEach { (progName, stdList) ->
                                val pct = if (filteredStudents.isNotEmpty()) (stdList.size * 100) / filteredStudents.size else 0
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(progName, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text("${stdList.size} ($pct%)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = RedPrimary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                    }
                }

                if (filteredStudents.isEmpty()) {
                    item { EmptyReportState("No students found matching current filters.") }
                } else {
                    items(filteredStudents) { std ->
                        StudentReportCard(std)
                    }
                }
            }

            1 -> {
                // 2. Attendance Analytics
                val total = filteredAttendance.size
                val present = filteredAttendance.count { it.status.lowercase() == "present" }
                val late = filteredAttendance.count { it.status.lowercase() == "late" }
                val absent = filteredAttendance.count { it.status.lowercase() == "absent" }
                val excused = filteredAttendance.count { it.status.lowercase() == "excused" }
                val presentRate = if (total > 0) ((present + late) * 100) / total else 0

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(title = "Present Rate", value = "$presentRate%", icon = Icons.Default.Percent, iconTint = SuccessGreen, modifier = Modifier.weight(1f))
                        StatCard(title = "Present Logs", value = "$present", icon = Icons.Default.Check, modifier = Modifier.weight(1f))
                        StatCard(title = "Late / Excused", value = "${late + excused}", icon = Icons.Default.Schedule, iconTint = GoldBelt, modifier = Modifier.weight(1f))
                        StatCard(title = "Absent", value = "$absent", icon = Icons.Default.Close, iconTint = RedPrimary, modifier = Modifier.weight(1f))
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Attendance Ratio Breakdown", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { presentRate / 100f },
                                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                                color = SuccessGreen,
                                trackColor = RedPrimary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Present/Late: $presentRate%", fontSize = 11.sp, color = SuccessGreen)
                                Text("Absent/Excused: ${100 - presentRate}%", fontSize = 11.sp, color = RedPrimary)
                            }
                        }
                    }
                }

                if (filteredAttendance.isEmpty()) {
                    item { EmptyReportState("No attendance logs found matching current date and filters.") }
                } else {
                    items(filteredAttendance) { att ->
                        AttendanceReportCard(att)
                    }
                }
            }

            2 -> {
                // 3. Financial & Fee Collections
                val totalCollected = filteredPayments.filter { it.status.lowercase() != "refunded" }.sumOf { it.amountPaise }
                val totalRefunded = filteredPayments.filter { it.status.lowercase() == "refunded" }.sumOf { it.amountPaise }
                val totalOutstanding = filteredInvoices.filter { it.status.lowercase() != "paid" }.sumOf { (it.amountPaise - it.paidPaise).coerceAtLeast(0L) }
                val overdueInvoices = filteredInvoices.filter { it.status.lowercase() == "overdue" }
                val overdueAmount = overdueInvoices.sumOf { (it.amountPaise - it.paidPaise).coerceAtLeast(0L) }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(title = "Total Collections", value = "₹${"%,.0f".format(totalCollected / 100.0)}", icon = Icons.Default.CurrencyRupee, iconTint = SuccessGreen, modifier = Modifier.weight(1f))
                        StatCard(title = "Outstanding Dues", value = "₹${"%,.0f".format(totalOutstanding / 100.0)}", icon = Icons.Default.ReceiptLong, iconTint = RedPrimary, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(title = "Overdue Invoices", value = "${overdueInvoices.size} (₹${"%,.0f".format(overdueAmount / 100.0)})", icon = Icons.Default.Warning, iconTint = RedDark, modifier = Modifier.weight(1f))
                        StatCard(title = "Total Refunds", value = "₹${"%,.0f".format(totalRefunded / 100.0)}", icon = Icons.Default.AssignmentReturn, iconTint = TextMuted, modifier = Modifier.weight(1f))
                    }
                }

                item {
                    Text(text = "Itemized Invoices & Payments Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (filteredInvoices.isEmpty() && filteredPayments.isEmpty()) {
                    item { EmptyReportState("No financial records found.") }
                } else {
                    items(filteredInvoices) { inv ->
                        InvoiceReportCard(inv)
                    }
                }
            }

            3 -> {
                // 4. Batch Occupancy & Schedules
                val totalCapacity = filteredBatches.sumOf { it.capacity }
                val totalEnrolled = filteredBatches.sumOf { it.studentIds.size.toLong() }
                val avgOccupancy = if (totalCapacity > 0) ((totalEnrolled * 100) / totalCapacity).toInt() else 0

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(title = "Total Batches", value = "${filteredBatches.size}", icon = Icons.Default.Class, modifier = Modifier.weight(1f))
                        StatCard(title = "Academy Occupancy", value = "$avgOccupancy%", icon = Icons.Default.PieChart, iconTint = if (avgOccupancy > 80) GoldBelt else SuccessGreen, modifier = Modifier.weight(1f))
                        StatCard(title = "Total Capacity", value = "$totalEnrolled / $totalCapacity", icon = Icons.Default.People, modifier = Modifier.weight(1f))
                    }
                }

                if (filteredBatches.isEmpty()) {
                    item { EmptyReportState("No batches found for this branch.") }
                } else {
                    items(filteredBatches) { batch ->
                        BatchReportCard(batch)
                    }
                }
            }

            4 -> {
                // 5. Belt Promotions & Grading Results
                val pendingCount = filteredGradings.count { it.isPending }
                val approvedCount = filteredGradings.count { it.isApproved }
                val certCount = certificates.count { selectedBranchId == "all" || it.branchId == selectedBranchId }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(title = "Promotions Awarded", value = "$approvedCount", icon = Icons.Default.MilitaryTech, iconTint = SuccessGreen, modifier = Modifier.weight(1f))
                        StatCard(title = "Pending Assessments", value = "$pendingCount", icon = Icons.Default.HourglassTop, iconTint = RedPrimary, modifier = Modifier.weight(1f))
                        StatCard(title = "Certificates Issued", value = "$certCount", icon = Icons.Default.WorkspacePremium, iconTint = GoldBelt, modifier = Modifier.weight(1f))
                    }
                }

                if (filteredGradings.isEmpty()) {
                    item { EmptyReportState("No grading assessments recorded.") }
                } else {
                    items(filteredGradings) { grading ->
                        GradingReportCard(grading)
                    }
                }
            }

            5 -> {
                // 6. Admissions Pipeline & Conversion
                val pending = filteredAdmissions.count { it.status.lowercase() == "pending" }
                val approved = filteredAdmissions.count { it.status.lowercase() == "approved" }
                val rejected = filteredAdmissions.count { it.status.lowercase() == "rejected" }
                val conversionRate = if (filteredAdmissions.isNotEmpty()) ((approved * 100) / filteredAdmissions.size) else 0

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(title = "Total Applications", value = "${filteredAdmissions.size}", icon = Icons.Default.PersonAdd, modifier = Modifier.weight(1f))
                        StatCard(title = "Approved", value = "$approved ($conversionRate%)", icon = Icons.Default.CheckCircle, iconTint = SuccessGreen, modifier = Modifier.weight(1f))
                        StatCard(title = "Pending Review", value = "$pending", icon = Icons.Default.PendingActions, iconTint = RedPrimary, modifier = Modifier.weight(1f))
                    }
                }

                if (filteredAdmissions.isEmpty()) {
                    item { EmptyReportState("No admission applications found.") }
                } else {
                    items(filteredAdmissions) { adm ->
                        AdmissionReportCard(adm)
                    }
                }
            }
        }
    }

    // CSV Export & Sharing Dialog
    if (showExportDialog) {
        val linesCount = remember(generatedCsvContent) { generatedCsvContent.lines().filter { it.isNotBlank() }.size }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Default.Download, contentDescription = null, tint = RedPrimary) },
            title = { Text("Export $generatedCsvTitle", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "Summary: $linesCount rows generated", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "Data protected by branch authorization rules.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text("CSV Preview (First 4 rows):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = generatedCsvContent.lines().take(4).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, generatedCsvTitle)
                            putExtra(Intent.EXTRA_TEXT, generatedCsvContent)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Report CSV via"))
                        showExportDialog = false
                    },
                    modifier = Modifier.testTag("share_csv_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share CSV")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(generatedCsvTitle, generatedCsvContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "CSV copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    modifier = Modifier.testTag("copy_csv_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }
        )
    }
}

@Composable
private fun StudentReportCard(std: Student) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(std.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    BeltBadge(rank = std.currentBelt)
                }
                Text("${std.programName} • ${std.branchName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Phone: ${std.phoneNumber} • Roll: ${std.effectiveId.takeLast(6).uppercase()}", fontSize = 11.sp, color = TextMuted)
            }
            StatusBadge(status = std.status)
        }
    }
}

@Composable
private fun AttendanceReportCard(att: Attendance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(att.studentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Date: ${att.date} • Batch: ${att.batchId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Marked by: ${att.markedBy}", fontSize = 10.sp, color = TextMuted)
            }
            StatusBadge(status = att.status)
        }
    }
}

@Composable
private fun InvoiceReportCard(inv: FeeInvoice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(inv.studentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Period: ${inv.period} • Invoice: ${inv.effectiveId.takeLast(8).uppercase()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Due: ${inv.dueDate}", fontSize = 10.sp, color = TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${"%,.0f".format(inv.amountPaise / 100.0)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RedPrimary)
                StatusBadge(status = inv.status)
            }
        }
    }
}

@Composable
private fun BatchReportCard(b: Batch) {
    val enrolled = b.studentIds.size
    val occ = if (b.capacity > 0) ((enrolled * 100) / b.capacity).toInt() else 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(b.effectiveName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                StatusBadge(status = if (b.isBatchActive) "active" else "inactive")
            }
            Text("${b.programName} • ${b.branchName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Coach: ${b.coachName.ifBlank { "Assigned Coach" }} • ${b.schedule}", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Occupancy: $occ%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("$enrolled / ${b.capacity} enrolled", fontSize = 11.sp)
            }
            LinearProgressIndicator(
                progress = { (occ / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (occ >= 90) RedPrimary else SuccessGreen,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun GradingReportCard(g: GradingAssessment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(g.studentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${g.currentBeltName} ➔ ${g.proposedBeltName}", fontSize = 12.sp, color = RedPrimary, fontWeight = FontWeight.Bold)
                Text("Date: ${g.assessmentDate} • Score: ${g.score}/100 (${g.techniqueGrade})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Assessed by: ${g.coachName}", fontSize = 10.sp, color = TextMuted)
            }
            StatusBadge(status = g.status)
        }
    }
}

@Composable
private fun AdmissionReportCard(adm: AdmissionRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(adm.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Program: ${adm.programId} • Branch: ${adm.preferredBranchId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Contact: ${adm.phoneNumber} • ${adm.email}", fontSize = 10.sp, color = TextMuted)
                if (adm.isMinor) {
                    Text("Guardian: ${adm.guardianName} (${adm.guardianPhone})", fontSize = 10.sp, color = GoldBelt)
                }
            }
            StatusBadge(status = adm.status)
        }
    }
}

@Composable
private fun EmptyReportState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Text(message, fontSize = 13.sp, color = TextMuted)
        }
    }
}

private fun escapeCsv(value: Any?): String {
    val str = value?.toString() ?: ""
    return if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
        "\"${str.replace("\"", "\"\"")}\""
    } else {
        str
    }
}
