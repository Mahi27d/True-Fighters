package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBeltAndCertificatesTab(
    branches: List<Branch>,
    programs: List<Program>,
    beltRanks: List<BeltRank>,
    gradingAssessments: List<GradingAssessment>,
    certificates: List<Certificate>,
    selectedBranchId: String,
    onApproveGrading: (assessmentId: String, adminNotes: String) -> Unit,
    onRejectGrading: (assessmentId: String, reason: String) -> Unit,
    onCreateBeltRank: (BeltRank) -> Unit,
    onToggleBeltRankStatus: (rankId: String, currentActive: Boolean) -> Unit
) {
    var subTab by remember { mutableStateOf(0) } // 0: Pending Approvals, 1: Belt Ladder Ranks, 2: History, 3: Certificates Register
    var selectedProgramId by remember { mutableStateOf(programs.firstOrNull()?.effectiveId ?: "all") }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var approvingAssessment by remember { mutableStateOf<GradingAssessment?>(null) }
    var adminApprovalNotes by remember { mutableStateOf("") }
    var rejectingAssessment by remember { mutableStateOf<GradingAssessment?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    var showCreateRankDialog by remember { mutableStateOf(false) }
    var viewingCertificate by remember { mutableStateOf<Certificate?>(null) }

    // Filter assessments by branch
    val branchFilteredAssessments = remember(gradingAssessments, selectedBranchId) {
        if (selectedBranchId == "all") gradingAssessments else gradingAssessments.filter { it.branchId == selectedBranchId }
    }
    val pendingAssessments = branchFilteredAssessments.filter { it.isPending }
    val historyAssessments = branchFilteredAssessments.filter { !it.isPending }

    val branchFilteredCerts = remember(certificates, selectedBranchId, searchQuery) {
        val base = if (selectedBranchId == "all") certificates else certificates.filter { it.branchId == selectedBranchId }
        if (searchQuery.isBlank()) base else base.filter {
            it.studentName.contains(searchQuery, ignoreCase = true) ||
            it.effectiveNumber.contains(searchQuery, ignoreCase = true) ||
            it.effectiveBelt.contains(searchQuery, ignoreCase = true)
        }
    }

    val programFilteredRanks = remember(beltRanks, selectedProgramId) {
        if (selectedProgramId == "all") beltRanks else beltRanks.filter { it.programId == selectedProgramId }
    }.sortedBy { it.rankOrder }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Sub-tabs
        TabRow(
            selectedTabIndex = subTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = RedPrimary
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Approvals", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (pendingAssessments.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(shape = CircleShape, color = RedPrimary) {
                                Text(
                                    text = "${pendingAssessments.size}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            )
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Belt Ladder", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("History", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
            Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("Certificates (${branchFilteredCerts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (subTab) {
            0 -> {
                // Pending Approvals
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Pending Belt Promotion Assessments",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Review coach grading submissions. Approval issues official certificate & updates student rank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (pendingAssessments.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("All pending belt assessments have been reviewed!", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    items(pendingAssessments) { assess ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("pending_assessment_${assess.effectiveId}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = assess.studentName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(
                                            text = "${assess.programName} • Branch: ${assess.branchName}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusBadge(status = assess.status)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Belt Upgrade Arrow
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Current", fontSize = 11.sp, color = TextMuted)
                                        BeltBadge(rank = assess.currentBeltName)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = RedPrimary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Proposed Promotion", fontSize = 11.sp, color = GoldBelt, fontWeight = FontWeight.Bold)
                                        BeltBadge(rank = assess.proposedBeltName)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Score: ${assess.score}/100 (${assess.techniqueGrade})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(text = "Assessed by: ${assess.coachName}", fontSize = 12.sp, color = GoldBelt)
                                }

                                if (assess.coachComments.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "\"${assess.coachComments}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            rejectingAssessment = assess
                                            rejectionReason = "Requires more hours of sparring practice before yellow belt test."
                                        },
                                        modifier = Modifier.weight(1f).testTag("reject_assessment_${assess.effectiveId}")
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reject", color = RedPrimary)
                                    }

                                    Button(
                                        onClick = {
                                            approvingAssessment = assess
                                            adminApprovalNotes = "Promoted with honors based on technical evaluation."
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        modifier = Modifier.weight(1.4f).testTag("approve_assessment_${assess.effectiveId}")
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Approve & Certify")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Belt Ladder Configuration
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Belt Progression Hierarchy",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Configure curriculum, order, and minimum requirements per martial art",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showCreateRankDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                modifier = Modifier.testTag("add_belt_rank_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Rank")
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Program filter row
                        ScrollableTabRow(
                            selectedTabIndex = (listOf("all") + programs.map { it.effectiveId }).indexOf(selectedProgramId).coerceAtLeast(0),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = RedPrimary,
                            edgePadding = 8.dp
                        ) {
                            Tab(
                                selected = selectedProgramId == "all",
                                onClick = { selectedProgramId = "all" },
                                text = { Text("All Programs", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            programs.forEach { prog ->
                                Tab(
                                    selected = selectedProgramId == prog.effectiveId,
                                    onClick = { selectedProgramId = prog.effectiveId },
                                    text = { Text(prog.effectiveName, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (programFilteredRanks.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No belt ranks configured for selected program.", color = TextMuted)
                                }
                            }
                        }
                    }

                    items(programFilteredRanks) { rank ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("belt_rank_${rank.effectiveId}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF212121)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#${rank.rankOrder}",
                                            fontWeight = FontWeight.Bold,
                                            color = GoldBelt,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = rank.beltName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BeltBadge(rank = rank.beltName)
                                        }
                                        Text(text = rank.programName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (rank.minClassesRequired > 0 || rank.minMonthsRequired > 0) {
                                            Text(
                                                text = "Requirements: ${rank.minClassesRequired} classes • ${rank.minMonthsRequired} months",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        if (rank.syllabus.isNotBlank()) {
                                            Text(text = "Syllabus: ${rank.syllabus}", fontSize = 11.sp, color = TextMuted)
                                        }
                                    }
                                }

                                Switch(
                                    checked = rank.active,
                                    onCheckedChange = { onToggleBeltRankStatus(rank.effectiveId, rank.active) },
                                    modifier = Modifier.testTag("toggle_rank_${rank.effectiveId}")
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // Historical Promotions
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(text = "Historical Belt Promotions & Assessments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (historyAssessments.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No historical assessments recorded yet.", color = TextMuted)
                                }
                            }
                        }
                    }

                    items(historyAssessments) { assess ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = assess.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(text = "${assess.programName} • ${assess.assessmentDate}", fontSize = 12.sp, color = TextMuted)
                                    }
                                    StatusBadge(status = assess.status)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BeltBadge(rank = assess.currentBeltName)
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp).padding(horizontal = 4.dp))
                                    BeltBadge(rank = assess.proposedBeltName)
                                }
                                if (assess.adminNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "Admin Note: ${assess.adminNotes}", fontSize = 12.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Certificates Register
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by student, certificate #, or belt...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("search_certificates_input"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Issued Certificates Register", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (branchFilteredCerts.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No certificates found.", color = TextMuted)
                                }
                            }
                        }
                    }

                    items(branchFilteredCerts) { cert ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { viewingCertificate = cert }.testTag("cert_item_${cert.effectiveId}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldBelt, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = cert.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(text = cert.effectiveNumber, fontSize = 12.sp, color = GoldBelt, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${cert.effectiveProgram} • Awarded: ${cert.effectiveBelt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "Date: ${cert.issuedAt} • Branch: ${cert.branchName}", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                                OutlinedButton(onClick = { viewingCertificate = cert }) {
                                    Text("View")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Approve Promotion Dialog
    approvingAssessment?.let { assess ->
        AlertDialog(
            onDismissRequest = { approvingAssessment = null },
            title = { Text("Approve Belt Promotion") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "You are approving promotion for ${assess.studentName} from ${assess.currentBeltName} to ${assess.proposedBeltName}.",
                        fontWeight = FontWeight.Medium
                    )
                    Text(text = "Program: ${assess.programName} • Branch: ${assess.branchName}", fontSize = 12.sp, color = TextMuted)
                    Text(text = "Coach Evaluation Score: ${assess.score}/100", fontSize = 12.sp, color = GoldBelt, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = adminApprovalNotes,
                        onValueChange = { adminApprovalNotes = it },
                        label = { Text("Chief Examiner / Admin Notes *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApproveGrading(assess.effectiveId, adminApprovalNotes)
                        approvingAssessment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    modifier = Modifier.testTag("confirm_approve_promotion_btn")
                ) {
                    Text("Issue Certificate & Promote")
                }
            },
            dismissButton = {
                TextButton(onClick = { approvingAssessment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reject Assessment Dialog
    rejectingAssessment?.let { assess ->
        AlertDialog(
            onDismissRequest = { rejectingAssessment = null },
            title = { Text("Reject Assessment / Defer Promotion") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Provide constructive feedback for student ${assess.studentName} and coach ${assess.coachName}:")
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Reason for Deferral *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectGrading(assess.effectiveId, rejectionReason)
                        rejectingAssessment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    modifier = Modifier.testTag("confirm_reject_promotion_btn")
                ) {
                    Text("Confirm Rejection")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingAssessment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Belt Rank Dialog
    if (showCreateRankDialog) {
        var rankProgramId by remember { mutableStateOf(programs.firstOrNull()?.effectiveId ?: "prog_karate") }
        var beltName by remember { mutableStateOf("") }
        var rankOrderText by remember { mutableStateOf("1") }
        var colorHex by remember { mutableStateOf("#FFFFFF") }
        var minClassesText by remember { mutableStateOf("24") }
        var minMonthsText by remember { mutableStateOf("2") }
        var syllabusText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateRankDialog = false },
            title = { Text("Add Program Belt Rank") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Program picker
                    Text(text = "Program *", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        programs.take(3).forEach { p ->
                            FilterChip(
                                selected = rankProgramId == p.effectiveId,
                                onClick = { rankProgramId = p.effectiveId },
                                label = { Text(p.effectiveName.take(14), fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = beltName,
                        onValueChange = { beltName = it },
                        label = { Text("Belt Name (e.g. Purple Belt) *") },
                        modifier = Modifier.fillMaxWidth().testTag("new_rank_name_input"),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rankOrderText,
                            onValueChange = { rankOrderText = it },
                            label = { Text("Order # *") },
                            modifier = Modifier.weight(1f).testTag("new_rank_order_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = colorHex,
                            onValueChange = { colorHex = it },
                            label = { Text("Color Hex") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minClassesText,
                            onValueChange = { minClassesText = it },
                            label = { Text("Min Classes") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minMonthsText,
                            onValueChange = { minMonthsText = it },
                            label = { Text("Min Months") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = syllabusText,
                        onValueChange = { syllabusText = it },
                        label = { Text("Syllabus & Test Criteria") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val progName = programs.find { it.effectiveId == rankProgramId }?.effectiveName ?: "Martial Arts"
                        val rank = BeltRank(
                            programId = rankProgramId,
                            programName = progName,
                            beltName = beltName.trim(),
                            rankOrder = rankOrderText.toIntOrNull() ?: 1,
                            colorHex = colorHex.trim().ifBlank { "#FFFFFF" },
                            minClassesRequired = minClassesText.toIntOrNull() ?: 0,
                            minMonthsRequired = minMonthsText.toIntOrNull() ?: 0,
                            syllabus = syllabusText.trim(),
                            active = true
                        )
                        onCreateBeltRank(rank)
                        showCreateRankDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    modifier = Modifier.testTag("submit_new_belt_rank_btn")
                ) {
                    Text("Save Belt Rank")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRankDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // View Certificate Modal Dialog
    viewingCertificate?.let { cert ->
        AlertDialog(
            onDismissRequest = { viewingCertificate = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldBelt)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Official Academy Certificate")
                }
            },
            text = {
                Card(
                    modifier = Modifier.fillMaxWidth().border(2.dp, GoldBelt, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TRUE FIGHTERS MARTIAL ARTS ACADEMY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedPrimary
                        )
                        Text(text = "Branch: ${cert.branchName}", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(text = cert.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(text = "This certifies that", fontSize = 12.sp, color = TextMuted)
                        Text(
                            text = cert.studentName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(text = "has successfully attained the rank of", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        BeltBadge(rank = cert.effectiveBelt)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "in ${cert.effectiveProgram}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(text = "Certificate #:", fontSize = 10.sp, color = TextMuted)
                                Text(text = cert.effectiveNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldBelt)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Verification Code:", fontSize = 10.sp, color = TextMuted)
                                Text(text = cert.verificationCode.ifBlank { "VREF-AUTHENTIC" }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Date: ${cert.issuedAt}", fontSize = 11.sp, color = TextMuted)
                            Text(text = "Signed: ${cert.issuedBy}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewingCertificate = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("Close")
                }
            }
        )
    }
}
