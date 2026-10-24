package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.BeltBadge
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedDark
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    branches: List<Branch>,
    admissionRequests: List<AdmissionRequest>,
    students: List<Student>,
    batches: List<Batch>,
    invoices: List<FeeInvoice>,
    payments: List<Payment>,
    programs: List<Program> = emptyList(),
    classSessions: List<ClassSession> = emptyList(),
    coaches: List<UserProfile> = emptyList(),
    attendanceList: List<Attendance> = emptyList(),
    feePlans: List<FeePlan> = emptyList(),
    beltRanks: List<BeltRank> = emptyList(),
    gradingAssessments: List<GradingAssessment> = emptyList(),
    certificates: List<Certificate> = emptyList(),
    videos: List<Video> = emptyList(),
    adminProfile: UserProfile? = null,
    onAddBranch: (Branch) -> Unit,
    onUpdateBranch: (Branch) -> Unit = {},
    onToggleBranchStatus: (branchId: String, currentActive: Boolean) -> Unit = { _, _ -> },
    onAssignCoachBranches: (coachUserId: String, branchIds: List<String>) -> Unit = { _, _ -> },
    onAssignStudentBranch: (studentId: String, userId: String, branchId: String, branchName: String) -> Unit = { _, _, _, _ -> },
    onUpdateStudentProfile: (Student) -> Unit = {},
    onSetStudentStatus: (studentId: String, userId: String, status: String, reason: String) -> Unit = { _, _, _, _ -> },
    onApproveAdmission: (AdmissionRequest) -> Unit,
    onRejectAdmission: (AdmissionRequest, String) -> Unit = { _, _ -> },
    onRecordManualPayment: (Payment) -> Unit,
    onGenerateInvoice: (FeeInvoice) -> Unit,
    onCreateFeePlan: (FeePlan) -> Unit = {},
    onUpdateFeePlan: (FeePlan) -> Unit = {},
    onToggleFeePlanStatus: (planId: String, currentActive: Boolean) -> Unit = { _, _ -> },
    onRefundPayment: (paymentId: String, reason: String) -> Unit = { _, _ -> },
    onProvisionCoach: (UserProfile) -> Unit = {},
    onCreateProgram: (Program) -> Unit = {},
    onUpdateProgram: (Program) -> Unit = {},
    onToggleProgramStatus: (programId: String, currentActive: Boolean) -> Unit = { _, _ -> },
    onCreateBatch: (Batch) -> Unit = {},
    onUpdateBatch: (Batch) -> Unit = {},
    onToggleBatchStatus: (batchId: String, currentActive: Boolean) -> Unit = { _, _ -> },
    onManageBatchStudent: (batchId: String, studentId: String, action: String, targetBatchId: String?) -> Unit = { _, _, _, _ -> },
    onCreateSession: (ClassSession) -> Unit = {},
    onRescheduleOrCancelSession: (sessionId: String, newDate: String?, newStart: String?, newEnd: String?, status: String?, reason: String) -> Unit = { _, _, _, _, _, _ -> },
    onApproveGrading: (assessmentId: String, adminNotes: String) -> Unit = { _, _ -> },
    onRejectGrading: (assessmentId: String, reason: String) -> Unit = { _, _ -> },
    onCreateBeltRank: (BeltRank) -> Unit = {},
    onToggleBeltRankStatus: (rankId: String, currentActive: Boolean) -> Unit = { _, _ -> },
    onCreateVideo: (Video) -> Unit = {},
    onToggleVideoPublished: (videoId: String, currentPublished: Boolean) -> Unit = { _, _ -> },
    onDeleteVideo: (videoId: String) -> Unit = {},
    onSignOut: () -> Unit
) {
    var selectedBranchId by remember { mutableStateOf("all") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Overview, 1: Admissions, 2: Branches, 3: Fees, 4: Students, 5: Coaches, 6: Programs, 7: Batches, 8: Schedule, 9: Attendance Reports

    // Dialog states
    var showAddBranchDialog by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showCreateInvoiceDialog by remember { mutableStateOf(false) }

    // Filter branches, admissions, and students to admin's authorized branches (empty branchIds = global admin)
    val allowedBranchIds = remember(adminProfile) { adminProfile?.branchIds ?: emptyList() }
    val authorizedBranches = remember(branches, allowedBranchIds) {
        if (allowedBranchIds.isEmpty()) branches else branches.filter { allowedBranchIds.contains(it.effectiveId) }
    }
    val authorizedAdmissions = remember(admissionRequests, allowedBranchIds) {
        if (allowedBranchIds.isEmpty()) admissionRequests else admissionRequests.filter { allowedBranchIds.contains(it.preferredBranchId) }
    }
    val authorizedStudents = remember(students, allowedBranchIds) {
        if (allowedBranchIds.isEmpty()) students else students.filter { allowedBranchIds.contains(it.branchId) }
    }

    // Calculated metrics
    val pendingAdmissions = authorizedAdmissions.count { it.status == "pending" }
    val totalCollectionsPaise = payments.sumOf { it.amountPaise }
    val totalOutstandingPaise = invoices.filter { it.status != "paid" }.sumOf { it.amountPaise - it.paidPaise }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "True Fighters HQ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Multi-Branch Management", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut, modifier = Modifier.testTag("admin_logout_button")) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = "Log Out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Admin Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RedPrimary,
                edgePadding = 12.dp
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Admissions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (pendingAdmissions > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(shape = CircleShape, color = RedPrimary) {
                                    Text(text = "$pendingAdmissions", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                )
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Branches", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Fees & Ledger", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Students (${authorizedStudents.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text("Coaches (${coaches.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 }, text = { Text("Programs (${programs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 7, onClick = { selectedTab = 7 }, text = { Text("Batches (${batches.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 8, onClick = { selectedTab = 8 }, text = { Text("Schedule (${classSessions.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 9, onClick = { selectedTab = 9 }, text = { Text("Attendance Reports", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                val pendingAssessmentsCount = remember(gradingAssessments) { gradingAssessments.count { it.isPending } }
                Tab(
                    selected = selectedTab == 10,
                    onClick = { selectedTab = 10 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Belts & Gradings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (pendingAssessmentsCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(shape = CircleShape, color = RedPrimary) {
                                    Text(text = "$pendingAssessmentsCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                )
                Tab(selected = selectedTab == 11, onClick = { selectedTab = 11 }, text = { Text("Videos (${videos.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
            }

            when (selectedTab) {
                0 -> AdminOverviewTab(
                    branches = authorizedBranches,
                    pendingAdmissions = pendingAdmissions,
                    totalCollectionsPaise = totalCollectionsPaise,
                    totalOutstandingPaise = totalOutstandingPaise,
                    studentsCount = authorizedStudents.size,
                    onOpenAdmissions = { selectedTab = 1 },
                    onOpenAddBranch = { showAddBranchDialog = true },
                    onOpenRecordPayment = { showRecordPaymentDialog = true },
                    onOpenCreateInvoice = { showCreateInvoiceDialog = true }
                )
                1 -> AdminAdmissionsTab(
                    admissionRequests = authorizedAdmissions,
                    branches = authorizedBranches,
                    onApprove = onApproveAdmission,
                    onReject = onRejectAdmission
                )
                2 -> AdminBranchesTab(
                    branches = authorizedBranches,
                    batches = batches,
                    students = authorizedStudents,
                    coaches = coaches,
                    onAddBranchClick = { showAddBranchDialog = true },
                    onUpdateBranch = onUpdateBranch,
                    onToggleBranchStatus = onToggleBranchStatus
                )
                3 -> AdminFeesTab(
                    invoices = invoices,
                    payments = payments,
                    feePlans = feePlans,
                    branches = authorizedBranches,
                    programs = programs,
                    students = authorizedStudents,
                    onCreateFeePlan = onCreateFeePlan,
                    onUpdateFeePlan = onUpdateFeePlan,
                    onToggleFeePlanStatus = onToggleFeePlanStatus,
                    onGenerateInvoice = onGenerateInvoice,
                    onRecordPayment = onRecordManualPayment,
                    onRefundPayment = onRefundPayment
                )
                4 -> AdminStudentsTab(
                    students = authorizedStudents,
                    branches = authorizedBranches,
                    attendanceList = attendanceList,
                    invoices = invoices,
                    payments = payments,
                    onAssignStudentBranch = onAssignStudentBranch,
                    onUpdateStudentProfile = onUpdateStudentProfile,
                    onSetStudentStatus = onSetStudentStatus
                )
                5 -> AdminCoachesTab(
                    coaches = coaches,
                    branches = authorizedBranches,
                    batches = batches,
                    onProvisionCoach = onProvisionCoach,
                    onAssignCoachBranches = onAssignCoachBranches
                )
                6 -> AdminProgramsTab(
                    programs = programs,
                    branches = authorizedBranches,
                    onCreateProgram = onCreateProgram,
                    onUpdateProgram = onUpdateProgram,
                    onToggleProgramStatus = onToggleProgramStatus
                )
                7 -> AdminBatchesTab(
                    batches = batches,
                    programs = programs,
                    branches = authorizedBranches,
                    coaches = coaches,
                    students = authorizedStudents,
                    onCreateBatch = onCreateBatch,
                    onUpdateBatch = onUpdateBatch,
                    onToggleBatchStatus = onToggleBatchStatus,
                    onManageStudentMembership = onManageBatchStudent
                )
                8 -> AdminScheduleTab(
                    classSessions = classSessions,
                    batches = batches,
                    branches = authorizedBranches,
                    coaches = coaches,
                    onCreateSession = onCreateSession,
                    onRescheduleOrCancel = onRescheduleOrCancelSession
                )
                9 -> AdminAttendanceReportsTab(
                    attendanceList = attendanceList,
                    branches = authorizedBranches
                )
                10 -> AdminBeltAndCertificatesTab(
                    branches = authorizedBranches,
                    programs = programs,
                    beltRanks = beltRanks,
                    gradingAssessments = gradingAssessments,
                    certificates = certificates,
                    selectedBranchId = selectedBranchId,
                    onApproveGrading = onApproveGrading,
                    onRejectGrading = onRejectGrading,
                    onCreateBeltRank = onCreateBeltRank,
                    onToggleBeltRankStatus = onToggleBeltRankStatus
                )
                11 -> AdminVideosTab(
                    programs = programs,
                    videos = videos,
                    onCreateVideo = onCreateVideo,
                    onTogglePublish = onToggleVideoPublished,
                    onDeleteVideo = onDeleteVideo
                )
            }
        }
    }

    // Add Branch Modal with validation & duplicate code check
    if (showAddBranchDialog) {
        var branchName by remember { mutableStateOf("") }
        var branchCode by remember { mutableStateOf("TF-") }
        var branchAddress by remember { mutableStateOf("") }
        var branchCity by remember { mutableStateOf("New Delhi") }
        var branchState by remember { mutableStateOf("Delhi") }
        var branchPostalCode by remember { mutableStateOf("") }
        var branchPhone by remember { mutableStateOf("+91 ") }
        var validationError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddBranchDialog = false },
            title = { Text("Add Academy Branch", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (validationError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().testTag("add_branch_error_message")
                        ) {
                            Text(
                                text = validationError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it; validationError = null },
                        label = { Text("Branch Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("branch_name_input")
                    )
                    OutlinedTextField(
                        value = branchCode,
                        onValueChange = { branchCode = it.uppercase(); validationError = null },
                        label = { Text("Branch Code (e.g. TF-EAST) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("branch_code_input")
                    )
                    OutlinedTextField(
                        value = branchAddress,
                        onValueChange = { branchAddress = it; validationError = null },
                        label = { Text("Street Address *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("branch_address_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = branchCity,
                            onValueChange = { branchCity = it },
                            label = { Text("City *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("branch_city_input")
                        )
                        OutlinedTextField(
                            value = branchState,
                            onValueChange = { branchState = it },
                            label = { Text("State *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("branch_state_input")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = branchPostalCode,
                            onValueChange = { branchPostalCode = it },
                            label = { Text("Postal Code") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f).testTag("branch_postal_input")
                        )
                        OutlinedTextField(
                            value = branchPhone,
                            onValueChange = { branchPhone = it },
                            label = { Text("Contact Phone *") },
                            singleLine = true,
                            modifier = Modifier.weight(1.1f).testTag("branch_phone_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = branchName.trim()
                        val trimmedCode = branchCode.trim().uppercase()
                        val trimmedAddr = branchAddress.trim()
                        val trimmedPhone = branchPhone.trim()
                        when {
                            trimmedName.isBlank() -> validationError = "Branch name is required."
                            trimmedCode.length < 3 -> validationError = "Valid branch code (min 3 chars) is required."
                            branches.any { it.effectiveCode.equals(trimmedCode, ignoreCase = true) } ->
                                validationError = "Branch code '$trimmedCode' already exists. Choose a unique code."
                            trimmedAddr.isBlank() -> validationError = "Branch street address is required."
                            trimmedPhone.length < 7 -> validationError = "Valid contact phone is required."
                            else -> {
                                onAddBranch(
                                    Branch(
                                        name = trimmedName,
                                        branchName = trimmedName,
                                        code = trimmedCode,
                                        branchCode = trimmedCode,
                                        address = trimmedAddr,
                                        city = branchCity.trim(),
                                        state = branchState.trim(),
                                        postalCode = branchPostalCode.trim(),
                                        phone = trimmedPhone,
                                        contactPhone = trimmedPhone,
                                        status = "active",
                                        active = true
                                    )
                                )
                                showAddBranchDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("submit_add_branch_button")
                ) {
                    Text("Create Branch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBranchDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Record Manual Payment Modal
    if (showRecordPaymentDialog) {
        var studentName by remember { mutableStateOf(students.firstOrNull()?.fullName ?: "Arjun Verma") }
        var amountText by remember { mutableStateOf("2500") }
        var method by remember { mutableStateOf("Cash") }
        var refText by remember { mutableStateOf("CASH-REC-${UUID.randomUUID().toString().take(6).uppercase()}") }

        AlertDialog(
            onDismissRequest = { showRecordPaymentDialog = false },
            title = { Text("Record Manual Fee Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = studentName, onValueChange = { studentName = it }, label = { Text("Student Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (₹)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = method, onValueChange = { method = it }, label = { Text("Payment Method (Cash / UPI / Bank Transfer)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = refText, onValueChange = { refText = it }, label = { Text("Reference / Receipt No") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountPaise = (amountText.toLongOrNull() ?: 2500L) * 100L
                        onRecordManualPayment(
                            Payment(
                                branchId = "branch_main",
                                studentName = studentName,
                                amountPaise = amountPaise,
                                method = method.lowercase(),
                                providerReference = refText,
                                status = "verified"
                            )
                        )
                        showRecordPaymentDialog = false
                    }
                ) {
                    Text("Record & Issue Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordPaymentDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create Invoice Modal
    if (showCreateInvoiceDialog) {
        var studentName by remember { mutableStateOf(students.firstOrNull()?.fullName ?: "Arjun Verma") }
        var period by remember { mutableStateOf("November 2026") }
        var amountText by remember { mutableStateOf("2500") }

        AlertDialog(
            onDismissRequest = { showCreateInvoiceDialog = false },
            title = { Text("Generate Monthly Invoice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = studentName, onValueChange = { studentName = it }, label = { Text("Student Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Billing Month / Period") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Fee Amount (₹)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountPaise = (amountText.toLongOrNull() ?: 2500L) * 100L
                        onGenerateInvoice(
                            FeeInvoice(
                                branchId = "branch_main",
                                studentName = studentName,
                                period = period,
                                amountPaise = amountPaise,
                                dueDate = "10th $period",
                                status = "unpaid"
                            )
                        )
                        showCreateInvoiceDialog = false
                    }
                ) {
                    Text("Generate Invoice")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateInvoiceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminOverviewTab(
    branches: List<Branch>,
    pendingAdmissions: Int,
    totalCollectionsPaise: Long,
    totalOutstandingPaise: Long,
    studentsCount: Int,
    onOpenAdmissions: () -> Unit,
    onOpenAddBranch: () -> Unit,
    onOpenRecordPayment: () -> Unit,
    onOpenCreateInvoice: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "Academy Key Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    title = "Pending Admissions",
                    value = "$pendingAdmissions",
                    icon = Icons.Default.PendingActions,
                    iconTint = if (pendingAdmissions > 0) RedPrimary else SuccessGreen,
                    subtitle = "Awaiting review",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Active Students",
                    value = if (studentsCount > 0) "$studentsCount" else "142",
                    icon = Icons.Default.Groups,
                    iconTint = SuccessGreen,
                    subtitle = "Across all dojos",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    title = "Monthly Collections",
                    value = "₹${"%,.0f".format(totalCollectionsPaise / 100.0)}",
                    icon = Icons.Default.CurrencyRupee,
                    iconTint = SuccessGreen,
                    subtitle = "Verified receipts",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Outstanding Dues",
                    value = "₹${"%,.0f".format(totalOutstandingPaise / 100.0)}",
                    icon = Icons.Default.ReceiptLong,
                    iconTint = RedPrimary,
                    subtitle = "Unpaid invoices",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Executive Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenAdmissions,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("Admissions ($pendingAdmissions)", fontSize = 11.sp)
                }
                Button(
                    onClick = onOpenRecordPayment,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Record Fee", fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAddBranch, modifier = Modifier.weight(1f)) {
                    Text("+ Add Branch", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onOpenCreateInvoice, modifier = Modifier.weight(1f)) {
                    Text("+ New Invoice", fontSize = 11.sp)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Active Academy Branches (${branches.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(branches) { branch ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = branch.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(text = "Code: ${branch.code} • ${branch.address}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "Phone: ${branch.phone}", fontSize = 11.sp, color = GoldBelt)
                    }
                    StatusBadge(status = if (branch.active) "Active" else "Inactive")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminAdmissionsTab(
    admissionRequests: List<AdmissionRequest>,
    branches: List<Branch>,
    onApprove: (AdmissionRequest) -> Unit,
    onReject: (AdmissionRequest, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, APPROVED, REJECTED
    var branchFilter by remember { mutableStateOf("ALL") }
    var selectedAdmissionForDetails by remember { mutableStateOf<AdmissionRequest?>(null) }
    var admissionToReject by remember { mutableStateOf<AdmissionRequest?>(null) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }

    val filteredAdmissions = admissionRequests.filter { req ->
        val matchesSearch = searchQuery.isBlank() ||
            req.fullName.contains(searchQuery, ignoreCase = true) ||
            req.phoneNumber.contains(searchQuery, ignoreCase = true) ||
            req.preferredBranchName.contains(searchQuery, ignoreCase = true) ||
            req.programName.contains(searchQuery, ignoreCase = true)
        val matchesStatus = when (statusFilter) {
            "PENDING" -> req.status.equals("pending", ignoreCase = true)
            "APPROVED" -> req.status.equals("approved", ignoreCase = true)
            "REJECTED" -> req.status.equals("rejected", ignoreCase = true)
            else -> true
        }
        val matchesBranch = branchFilter == "ALL" || req.preferredBranchId == branchFilter
        matchesSearch && matchesStatus && matchesBranch
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Admission Inquiries & Applications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "Review incoming student applications, verify minor/guardian details, and enroll into branches", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (confirmationMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().testTag("admission_confirmation_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = confirmationMessage ?: "",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { confirmationMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search applicant name, phone, branch, or program...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admission_search_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "PENDING" to "Pending", "APPROVED" to "Approved", "REJECTED" to "Rejected").forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.testTag("admission_status_filter_${key.lowercase()}")
                    )
                }
            }
            if (branches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = branchFilter == "ALL",
                        onClick = { branchFilter = "ALL" },
                        label = { Text("All Branches", fontSize = 11.sp) }
                    )
                    branches.take(4).forEach { b ->
                        FilterChip(
                            selected = branchFilter == b.effectiveId,
                            onClick = { branchFilter = b.effectiveId },
                            label = { Text(b.effectiveCode.ifBlank { b.effectiveName }, fontSize = 11.sp) },
                            modifier = Modifier.testTag("admission_branch_filter_${b.effectiveId}")
                        )
                    }
                }
            }
        }

        if (filteredAdmissions.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No matching admission requests found.", color = TextMuted)
                    }
                }
            }
        }

        items(filteredAdmissions) { req ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = req.fullName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (req.isMinor || req.guardianName.isNotBlank()) {
                                Text(text = "Minor Applicant • Guardian Consent: ${if (req.guardianConsent) "Verified" else "Recorded"}", fontSize = 11.sp, color = GoldBelt)
                            }
                        }
                        StatusBadge(status = req.status)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Discipline: ${req.programName.ifBlank { req.programId }} • Branch: ${req.preferredBranchName.ifBlank { req.preferredBranchId }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Phone: ${req.phoneNumber} ${if (req.dateOfBirth.isNotBlank()) "• DOB: ${req.dateOfBirth}" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (req.guardianName.isNotBlank()) {
                        Text(text = "Guardian: ${req.guardianName} (${req.guardianPhone})", fontSize = 11.sp, color = GoldBelt)
                    }
                    if (req.status.equals("rejected", ignoreCase = true) && req.rejectionReason.isNotBlank()) {
                        Text(text = "Rejection Reason: ${req.rejectionReason}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { selectedAdmissionForDetails = req },
                            modifier = Modifier.testTag("view_adm_details_${req.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Details", fontSize = 12.sp)
                        }

                        if (req.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onApprove(req)
                                        confirmationMessage = "Approved admission for ${req.fullName} into ${req.preferredBranchName.ifBlank { req.preferredBranchId }} (${req.programName}). Student profile & enrollment activated."
                                    },
                                    modifier = Modifier.testTag("approve_adm_${req.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Text("Approve & Enroll", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { admissionToReject = req },
                                    modifier = Modifier.testTag("reject_adm_${req.id}")
                                ) {
                                    Text("Reject", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Complete Application Details Dialog
    selectedAdmissionForDetails?.let { req ->
        AlertDialog(
            onDismissRequest = { selectedAdmissionForDetails = null },
            title = { Text("Admission Application Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Applicant: ${req.fullName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Application ID: ${req.id}", fontSize = 11.sp, color = TextMuted)
                    Text("Status: ${req.status.uppercase()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Verified Phone: ${req.phoneNumber}", fontSize = 12.sp)
                    if (req.email.isNotBlank()) Text("Email: ${req.email}", fontSize = 12.sp)
                    if (req.dateOfBirth.isNotBlank()) Text("Date of Birth: ${req.dateOfBirth}", fontSize = 12.sp)
                    Text("Preferred Branch: ${req.preferredBranchName.ifBlank { req.preferredBranchId }} (${req.preferredBranchId})", fontSize = 12.sp)
                    Text("Martial Arts Program: ${req.programName.ifBlank { req.programId }}", fontSize = 12.sp)
                    if (req.preferredBatchId.isNotBlank()) Text("Preferred Batch: ${req.preferredBatchId}", fontSize = 12.sp)
                    if (req.isMinor || req.guardianName.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Minor / Guardian Details", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldBelt)
                        Text("Guardian Name: ${req.guardianName.ifBlank { "N/A" }}", fontSize = 12.sp)
                        Text("Guardian Phone: ${req.guardianPhone.ifBlank { "N/A" }}", fontSize = 12.sp)
                        Text("Guardian Consent: ${if (req.guardianConsent) "Granted" else "Not Recorded"}", fontSize = 12.sp)
                    }
                    if (req.reviewedBy.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Reviewed By: ${req.reviewedBy}", fontSize = 11.sp, color = TextMuted)
                        if (req.rejectionReason.isNotBlank()) {
                            Text("Rejection Reason: ${req.rejectionReason}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAdmissionForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Reject Application Confirmation Dialog with optional rejection reason
    admissionToReject?.let { req ->
        var rejectionReason by remember(req) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { admissionToReject = null },
            title = { Text("Reject Admission Application?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reject the admission request for ${req.fullName} (${req.preferredBranchName})?", fontSize = 13.sp)
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Rejection Reason (optional)") },
                        placeholder = { Text("e.g. Batch capacity full or incomplete guardian details") },
                        modifier = Modifier.fillMaxWidth().testTag("rejection_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReject(req, rejectionReason.trim())
                        confirmationMessage = "Rejected admission application for ${req.fullName}."
                        admissionToReject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reject_admission_button")
                ) {
                    Text("Confirm Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { admissionToReject = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminBranchesTab(
    branches: List<Branch>,
    batches: List<Batch>,
    students: List<Student>,
    coaches: List<UserProfile>,
    onAddBranchClick: () -> Unit,
    onUpdateBranch: (Branch) -> Unit,
    onToggleBranchStatus: (String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, INACTIVE
    var selectedBranchForDetails by remember { mutableStateOf<Branch?>(null) }
    var branchToEdit by remember { mutableStateOf<Branch?>(null) }
    var branchToToggleStatus by remember { mutableStateOf<Branch?>(null) }

    val filteredBranches = branches.filter { branch ->
        val matchesSearch = branch.effectiveName.contains(searchQuery, ignoreCase = true) ||
            branch.effectiveCode.contains(searchQuery, ignoreCase = true) ||
            branch.city.contains(searchQuery, ignoreCase = true) ||
            branch.address.contains(searchQuery, ignoreCase = true)
        val matchesStatus = when (statusFilter) {
            "ACTIVE" -> branch.isBranchActive
            "INACTIVE" -> !branch.isBranchActive
            else -> true
        }
        matchesSearch && matchesStatus
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Academy Locations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = "Manage multi-branch dojos, status, and rosters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onAddBranchClick,
                    modifier = Modifier.testTag("add_branch_button")
                ) {
                    Text("+ Branch")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Name, Code, or City...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("branch_search_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All (${branches.size})", "ACTIVE" to "Active", "INACTIVE" to "Inactive").forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("branch_filter_${key.lowercase()}")
                    )
                }
            }
        }

        if (filteredBranches.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching branches found", fontWeight = FontWeight.Bold)
                        Text("Adjust your search or filter criteria, or create a new branch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filteredBranches) { b ->
                val bId = b.effectiveId
                val branchStudentsCount = students.count { it.branchId == bId }
                val branchCoachesCount = coaches.count { it.branchIds.contains(bId) }
                val isActive = b.isBranchActive

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("branch_card_${b.effectiveCode}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = b.effectiveName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    text = "Code: ${b.effectiveCode} • ${listOf(b.address, b.city, b.state, b.postalCode).filter { it.isNotBlank() }.joinToString(", ")}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = if (isActive) "Active" else "Inactive")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Contact: ${b.effectivePhone} • Students: $branchStudentsCount • Coaches: $branchCoachesCount", fontSize = 12.sp, color = GoldBelt)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedBranchForDetails = b },
                                modifier = Modifier.weight(1f).testTag("view_branch_${b.effectiveCode}")
                            ) {
                                Text("Details", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { branchToEdit = b },
                                modifier = Modifier.weight(1f).testTag("edit_branch_${b.effectiveCode}")
                            ) {
                                Text("Edit", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { branchToToggleStatus = b },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActive) MaterialTheme.colorScheme.error else SuccessGreen
                                ),
                                modifier = Modifier.weight(1f).testTag("toggle_branch_${b.effectiveCode}")
                            ) {
                                Text(if (isActive) "Deactivate" else "Activate", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Batches across Authorized Locations (${batches.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(batches) { batch ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = batch.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "${batch.programName} • ${batch.schedule}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Location: ${batch.branchName}", fontSize = 11.sp, color = GoldBelt)
                }
            }
        }
    }

    // View Branch Details Dialog (Students, Coaches, Batches assigned)
    selectedBranchForDetails?.let { branch ->
        val bId = branch.effectiveId
        val assignedStudents = students.filter { it.branchId == bId }
        val assignedCoaches = coaches.filter { it.branchIds.contains(bId) }
        val assignedBatches = batches.filter { it.branchId == bId }

        AlertDialog(
            onDismissRequest = { selectedBranchForDetails = null },
            title = { Text(branch.effectiveName, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Branch ID: $bId", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Code: ${branch.effectiveCode} | Status: ${if (branch.isBranchActive) "Active" else "Inactive"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Address: ${listOf(branch.address, branch.city, branch.state, branch.postalCode).filter { it.isNotBlank() }.joinToString(", ")}", fontSize = 12.sp)
                    Text("Contact Phone: ${branch.effectivePhone}", fontSize = 12.sp, color = GoldBelt)
                    HorizontalDivider()
                    Text("Assigned Coaches (${assignedCoaches.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (assignedCoaches.isEmpty()) {
                        Text("No coaches directly assigned to this branch.", fontSize = 12.sp, color = TextMuted)
                    } else {
                        assignedCoaches.forEach { c ->
                            Text("• ${c.displayName} (${c.phoneNumber})", fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider()
                    Text("Assigned Students (${assignedStudents.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (assignedStudents.isEmpty()) {
                        Text("No students currently assigned to this branch.", fontSize = 12.sp, color = TextMuted)
                    } else {
                        assignedStudents.take(8).forEach { s ->
                            Text("• ${s.fullName} — ${s.currentBelt} Belt (${s.batchName})", fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider()
                    Text("Scheduled Batches (${assignedBatches.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    assignedBatches.forEach { bt ->
                        Text("• ${bt.name} (${bt.schedule})", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBranchForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Branch Dialog
    branchToEdit?.let { branch ->
        var name by remember(branch) { mutableStateOf(branch.effectiveName) }
        var code by remember(branch) { mutableStateOf(branch.effectiveCode) }
        var address by remember(branch) { mutableStateOf(branch.address) }
        var city by remember(branch) { mutableStateOf(branch.city) }
        var state by remember(branch) { mutableStateOf(branch.state) }
        var postalCode by remember(branch) { mutableStateOf(branch.postalCode) }
        var phone by remember(branch) { mutableStateOf(branch.effectivePhone) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { branchToEdit = null },
            title = { Text("Edit Branch Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it; errorMsg = null }, label = { Text("Branch Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase(); errorMsg = null }, label = { Text("Branch Code *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it; errorMsg = null }, label = { Text("Street Address *") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = postalCode, onValueChange = { postalCode = it }, label = { Text("Postal Code") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone *") }, modifier = Modifier.weight(1f))
                    }
                    if (errorMsg != null) {
                        Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            name.isBlank() || code.isBlank() || address.isBlank() || phone.isBlank() -> {
                                errorMsg = "Name, Code, Address, and Contact Phone are required."
                            }
                            branches.any { it.effectiveId != branch.effectiveId && it.effectiveCode.equals(code.trim(), ignoreCase = true) } -> {
                                errorMsg = "Another branch already uses code '${code.trim().uppercase()}'."
                            }
                            else -> {
                                onUpdateBranch(
                                    branch.copy(
                                        name = name.trim(),
                                        branchName = name.trim(),
                                        code = code.trim().uppercase(),
                                        branchCode = code.trim().uppercase(),
                                        address = address.trim(),
                                        city = city.trim(),
                                        state = state.trim(),
                                        postalCode = postalCode.trim(),
                                        phone = phone.trim(),
                                        contactPhone = phone.trim()
                                    )
                                )
                                branchToEdit = null
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_edit_branch_button")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { branchToEdit = null }) { Text("Cancel") }
            }
        )
    }

    // Activate / Deactivate Confirmation Dialog
    branchToToggleStatus?.let { branch ->
        val currentlyActive = branch.isBranchActive
        val nextAction = if (currentlyActive) "Deactivate" else "Activate"
        AlertDialog(
            onDismissRequest = { branchToToggleStatus = null },
            title = { Text("$nextAction Branch?") },
            text = {
                Text(
                    if (currentlyActive)
                        "Are you sure you want to deactivate '${branch.effectiveName}'? New admission requests will no longer be accepted for this branch while inactive."
                    else
                        "Re-activate '${branch.effectiveName}' so students and coaches can resume operations at this dojo?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleBranchStatus(branch.effectiveId, !currentlyActive)
                        branchToToggleStatus = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentlyActive) MaterialTheme.colorScheme.error else SuccessGreen
                    ),
                    modifier = Modifier.testTag("confirm_toggle_branch_status_button")
                ) {
                    Text("Confirm $nextAction")
                }
            },
            dismissButton = {
                TextButton(onClick = { branchToToggleStatus = null }) { Text("Cancel") }
            }
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminStudentsTab(
    students: List<Student>,
    branches: List<Branch>,
    attendanceList: List<Attendance> = emptyList(),
    invoices: List<FeeInvoice> = emptyList(),
    payments: List<Payment> = emptyList(),
    onAssignStudentBranch: (String, String, String, String) -> Unit,
    onUpdateStudentProfile: (Student) -> Unit = {},
    onSetStudentStatus: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var branchFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, SUSPENDED, INACTIVE
    var selectedStudentForDetails by remember { mutableStateOf<Student?>(null) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }
    var studentToReassign by remember { mutableStateOf<Student?>(null) }
    var studentToToggleStatus by remember { mutableStateOf<Student?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    val filtered = students.filter { std ->
        val matchesSearch = searchQuery.isBlank() ||
            std.fullName.contains(searchQuery, ignoreCase = true) ||
            std.phoneNumber.contains(searchQuery, ignoreCase = true) ||
            std.branchName.contains(searchQuery, ignoreCase = true) ||
            std.programName.contains(searchQuery, ignoreCase = true)
        val matchesBranch = branchFilter == "ALL" || std.branchId == branchFilter
        val matchesStatus = when (statusFilter) {
            "ACTIVE" -> std.status.equals("active", ignoreCase = true)
            "SUSPENDED" -> std.status.equals("suspended", ignoreCase = true)
            "INACTIVE" -> std.status.equals("inactive", ignoreCase = true)
            else -> true
        }
        matchesSearch && matchesBranch && matchesStatus
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Enrolled Students Directory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "Search by name or phone, filter by branch and enrollment status, edit profiles, transfer, or suspend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            if (feedbackBanner != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("student_directory_feedback_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(feedbackBanner ?: "", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { feedbackBanner = null }, modifier = Modifier.size(22.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = SuccessGreen, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Student Name, Phone Number, or Branch...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("student_directory_search_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "ACTIVE" to "Active", "SUSPENDED" to "Suspended", "INACTIVE" to "Inactive").forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.testTag("student_status_filter_${key.lowercase()}")
                    )
                }
            }
            if (branches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = branchFilter == "ALL",
                        onClick = { branchFilter = "ALL" },
                        label = { Text("All Branches", fontSize = 11.sp) }
                    )
                    branches.take(4).forEach { b ->
                        FilterChip(
                            selected = branchFilter == b.effectiveId,
                            onClick = { branchFilter = b.effectiveId },
                            label = { Text(b.effectiveCode.ifBlank { b.effectiveName }, fontSize = 11.sp) },
                            modifier = Modifier.testTag("student_branch_filter_${b.effectiveId}")
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No students match the selected search or filters.", color = TextMuted)
                    }
                }
            }
        }

        items(filtered) { std ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = std.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "${std.programName} • ${std.batchName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (std.phoneNumber.isNotBlank()) {
                                Text(text = "Phone: ${std.phoneNumber}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(text = "Dojo: ${std.branchName}", fontSize = 11.sp, color = GoldBelt)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            BeltBadge(rank = std.currentBelt)
                            Spacer(modifier = Modifier.height(6.dp))
                            StatusBadge(status = std.status)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { selectedStudentForDetails = std },
                                modifier = Modifier.testTag("view_student_details_${std.id}")
                            ) {
                                Text("Details", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { studentToEdit = std },
                                modifier = Modifier.testTag("edit_student_profile_${std.id}")
                            ) {
                                Text("Edit", fontSize = 12.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { studentToReassign = std },
                                modifier = Modifier.testTag("assign_student_branch_${std.id}")
                            ) {
                                Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Transfer", fontSize = 12.sp)
                            }
                            val isStudentActive = std.status.equals("active", ignoreCase = true)
                            TextButton(
                                onClick = { studentToToggleStatus = std },
                                modifier = Modifier.testTag("toggle_student_status_${std.id}")
                            ) {
                                Text(
                                    text = if (isStudentActive) "Suspend" else "Reactivate",
                                    fontSize = 12.sp,
                                    color = if (isStudentActive) MaterialTheme.colorScheme.error else SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // View Student Details Dialog (including preserved attendance, invoices, and minor/guardian info)
    selectedStudentForDetails?.let { std ->
        val stdAttendanceCount = attendanceList.count { it.studentId == std.id || (std.userId.isNotBlank() && it.studentUserId == std.userId) }
        val stdInvoicesCount = invoices.count { it.studentId == std.id || (std.userId.isNotBlank() && it.studentUserId == std.userId) }
        val stdPaymentsCount = payments.count { it.studentId == std.id || (std.userId.isNotBlank() && it.studentUserId == std.userId) }

        AlertDialog(
            onDismissRequest = { selectedStudentForDetails = null },
            title = { Text(std.fullName, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Student ID: ${std.id}", fontSize = 11.sp, color = TextMuted)
                    Text("Enrollment Status: ${std.status.uppercase()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Branch: ${std.branchName} (${std.branchId})", fontSize = 12.sp)
                    Text("Program: ${std.programName} • Batch: ${std.batchName}", fontSize = 12.sp)
                    Text("Current Belt: ${std.currentBelt} • Joined: ${std.joinDate}", fontSize = 12.sp)
                    if (std.phoneNumber.isNotBlank()) Text("Phone: ${std.phoneNumber}", fontSize = 12.sp)
                    if (std.email.isNotBlank()) Text("Email: ${std.email}", fontSize = 12.sp)
                    if (std.dateOfBirth.isNotBlank()) Text("Date of Birth: ${std.dateOfBirth}", fontSize = 12.sp)
                    if (std.isMinor || std.guardianName.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Guardian & Minor Protection Details", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GoldBelt)
                        Text("Guardian Name: ${std.guardianName.ifBlank { "N/A" }}", fontSize = 12.sp)
                        Text("Guardian Phone: ${std.guardianPhone.ifBlank { "N/A" }}", fontSize = 12.sp)
                        Text("Guardian Consent: ${if (std.guardianConsent) "Verified" else "Recorded"}", fontSize = 12.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Preserved Training & Financial History", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Attendance Records: $stdAttendanceCount sessions", fontSize = 12.sp)
                    Text("Fee Invoices: $stdInvoicesCount • Payments: $stdPaymentsCount", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStudentForDetails = null }) { Text("Close") }
            }
        )
    }

    // Edit Student Profile Dialog (preserves enrollment, attendance, and payment history)
    studentToEdit?.let { std ->
        var fullName by remember(std) { mutableStateOf(std.fullName) }
        var phoneNumber by remember(std) { mutableStateOf(std.phoneNumber) }
        var email by remember(std) { mutableStateOf(std.email) }
        var dob by remember(std) { mutableStateOf(std.dateOfBirth) }
        var programName by remember(std) { mutableStateOf(std.programName) }
        var batchName by remember(std) { mutableStateOf(std.batchName) }
        var currentBelt by remember(std) { mutableStateOf(std.currentBelt) }
        var isMinor by remember(std) { mutableStateOf(std.isMinor) }
        var guardianName by remember(std) { mutableStateOf(std.guardianName) }
        var guardianPhone by remember(std) { mutableStateOf(std.guardianPhone) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { studentToEdit = null },
            title = { Text("Edit Student Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it; errorMsg = null }, label = { Text("Full Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("edit_student_name_input"))
                    OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it; errorMsg = null }, label = { Text("Phone Number") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("edit_student_phone_input"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("DOB (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = currentBelt, onValueChange = { currentBelt = it }, label = { Text("Current Belt") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = programName, onValueChange = { programName = it }, label = { Text("Program") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = batchName, onValueChange = { batchName = it }, label = { Text("Batch") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isMinor, onCheckedChange = { isMinor = it })
                        Text("Student is a minor (under 18)", fontSize = 12.sp)
                    }
                    if (isMinor || guardianName.isNotBlank()) {
                        OutlinedTextField(value = guardianName, onValueChange = { guardianName = it; errorMsg = null }, label = { Text("Guardian Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = guardianPhone, onValueChange = { guardianPhone = it; errorMsg = null }, label = { Text("Guardian Phone *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            fullName.isBlank() -> errorMsg = "Student full name is required."
                            isMinor && (guardianName.isBlank() || guardianPhone.isBlank()) -> errorMsg = "Guardian name and phone are required for minor students."
                            else -> {
                                onUpdateStudentProfile(
                                    std.copy(
                                        fullName = fullName.trim(),
                                        phoneNumber = phoneNumber.trim(),
                                        email = email.trim(),
                                        dateOfBirth = dob.trim(),
                                        programName = programName.trim(),
                                        batchName = batchName.trim(),
                                        currentBelt = currentBelt.trim(),
                                        isMinor = isMinor,
                                        guardianName = guardianName.trim(),
                                        guardianPhone = guardianPhone.trim(),
                                        guardianConsent = if (isMinor) true else std.guardianConsent
                                    )
                                )
                                feedbackBanner = "Updated profile for ${fullName.trim()}. Enrollment, attendance, and payment history preserved."
                                studentToEdit = null
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_edit_student_button")
                ) {
                    Text("Save Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToEdit = null }) { Text("Cancel") }
            }
        )
    }

    // Transfer Student Branch Dialog
    studentToReassign?.let { std ->
        val activeBranches = branches.filter { it.isBranchActive }
        var selectedBranchId by remember(std) {
            mutableStateOf(std.branchId.ifBlank { activeBranches.firstOrNull()?.effectiveId ?: "" })
        }
        AlertDialog(
            onDismissRequest = { studentToReassign = null },
            title = { Text("Transfer Branch for ${std.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select an active academy branch:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    activeBranches.forEach { b ->
                        val bId = b.effectiveId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBranchId = bId }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedBranchId == bId, onClick = { selectedBranchId = bId })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(b.effectiveName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Code: ${b.effectiveCode} • ${b.city}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = activeBranches.firstOrNull { it.effectiveId == selectedBranchId }
                        if (target != null) {
                            onAssignStudentBranch(std.id, std.userId, target.effectiveId, target.effectiveName)
                            feedbackBanner = "Transferred ${std.fullName} to ${target.effectiveName}."
                            studentToReassign = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_assign_student_branch_button")
                ) {
                    Text("Update Branch")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToReassign = null }) { Text("Cancel") }
            }
        )
    }

    // Suspend / Reactivate Student Confirmation Dialog
    studentToToggleStatus?.let { std ->
        val currentlyActive = std.status.equals("active", ignoreCase = true)
        val targetStatus = if (currentlyActive) "suspended" else "active"
        val actionLabel = if (currentlyActive) "Suspend" else "Reactivate"
        var reason by remember(std) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { studentToToggleStatus = null },
            title = { Text("$actionLabel Student Account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (currentlyActive)
                            "Suspending ${std.fullName} will block student dashboard login while preserving all enrollment, attendance, and payment history."
                        else
                            "Reactivating ${std.fullName} will restore active student enrollment and login access.",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason / Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_status_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetStudentStatus(std.id, std.userId, targetStatus, reason.trim())
                        feedbackBanner = "${if (currentlyActive) "Suspended" else "Reactivated"} student ${std.fullName}."
                        studentToToggleStatus = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentlyActive) MaterialTheme.colorScheme.error else SuccessGreen
                    ),
                    modifier = Modifier.testTag("confirm_toggle_student_status_button")
                ) {
                    Text("Confirm $actionLabel")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToToggleStatus = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminCoachesTab(
    coaches: List<UserProfile>,
    branches: List<Branch>,
    batches: List<Batch>,
    onProvisionCoach: (UserProfile) -> Unit,
    onAssignCoachBranches: (String, List<String>) -> Unit
) {
    var showProvisionDialog by remember { mutableStateOf(false) }
    var coachToEditBranches by remember { mutableStateOf<UserProfile?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Coaches Directory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = "Provision & assign multi-branch access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { showProvisionDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDark),
                    modifier = Modifier.testTag("provision_coach_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Provision Coach", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (coaches.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.SportsMartialArts, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No coaches provisioned yet", fontWeight = FontWeight.Bold)
                        Text("Click 'Provision Coach' to authorize coach mobile phone numbers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(coaches) { coach ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(RedPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.SportsMartialArts, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = coach.displayName.ifBlank { "Academy Coach" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(text = coach.phoneNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            StatusBadge(status = coach.status.ifBlank { "active" })
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val assignedBranchNames = branches.filter { coach.branchIds.contains(it.effectiveId) }.map { it.effectiveName }
                        val branchText = if (assignedBranchNames.isNotEmpty()) assignedBranchNames.joinToString(", ") else "No Branches Assigned"
                        Text(text = "Assigned Branches: $branchText", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GoldBelt)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = { coachToEditBranches = coach },
                                modifier = Modifier.testTag("manage_coach_branches_${coach.userId.ifBlank { coach.id }}")
                            ) {
                                Text("Manage Branches", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProvisionDialog) {
        var coachName by remember { mutableStateOf("") }
        var coachPhone by remember { mutableStateOf("+91 ") }
        val selectedBranchIds = remember {
            mutableStateListOf<String>().apply {
                branches.firstOrNull()?.effectiveId?.let { add(it) }
            }
        }

        AlertDialog(
            onDismissRequest = { showProvisionDialog = false },
            title = { Text("Provision Academy Coach") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = coachName,
                        onValueChange = { coachName = it },
                        label = { Text("Coach Full Name") },
                        modifier = Modifier.fillMaxWidth().testTag("provision_coach_name_input")
                    )
                    OutlinedTextField(
                        value = coachPhone,
                        onValueChange = { coachPhone = it },
                        label = { Text("Mobile Phone Number (+91...)") },
                        modifier = Modifier.fillMaxWidth().testTag("provision_coach_phone_input")
                    )
                    Text("Assign Branches (Multi-Select):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    branches.forEach { b ->
                        val bId = b.effectiveId
                        val checked = selectedBranchIds.contains(bId)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedBranchIds.remove(bId) else selectedBranchIds.add(bId)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!selectedBranchIds.contains(bId)) selectedBranchIds.add(bId)
                                    } else {
                                        selectedBranchIds.remove(bId)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "${b.effectiveName} (${b.effectiveCode})", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (coachName.isNotBlank() && coachPhone.length >= 10 && selectedBranchIds.isNotEmpty()) {
                            val firstBranch = branches.firstOrNull { it.effectiveId == selectedBranchIds.first() }
                            onProvisionCoach(
                                UserProfile(
                                    displayName = coachName.trim(),
                                    phoneNumber = coachPhone.trim(),
                                    role = "coach",
                                    status = "active",
                                    active = true,
                                    preferredBranchId = firstBranch?.effectiveId ?: "",
                                    preferredBranchName = firstBranch?.effectiveName ?: "",
                                    branchIds = selectedBranchIds.toList()
                                )
                            )
                            showProvisionDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_provision_coach_button")
                ) {
                    Text("Authorize & Provision")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProvisionDialog = false }) { Text("Cancel") }
            }
        )
    }

    coachToEditBranches?.let { coach ->
        val selectedBranchIds = remember(coach) {
            mutableStateListOf<String>().apply { addAll(coach.branchIds) }
        }
        AlertDialog(
            onDismissRequest = { coachToEditBranches = null },
            title = { Text("Assign Branches — ${coach.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select one or more authorized branches for this coach:", fontSize = 12.sp)
                    branches.forEach { b ->
                        val bId = b.effectiveId
                        val checked = selectedBranchIds.contains(bId)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedBranchIds.remove(bId) else selectedBranchIds.add(bId)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!selectedBranchIds.contains(bId)) selectedBranchIds.add(bId)
                                    } else {
                                        selectedBranchIds.remove(bId)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${b.effectiveName} (${b.effectiveCode})", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAssignCoachBranches(coach.userId.ifBlank { coach.id }, selectedBranchIds.toList())
                        coachToEditBranches = null
                    },
                    modifier = Modifier.testTag("confirm_coach_branches_button")
                ) {
                    Text("Save Branch Assignments")
                }
            },
            dismissButton = {
                TextButton(onClick = { coachToEditBranches = null }) { Text("Cancel") }
            }
        )
    }
}

