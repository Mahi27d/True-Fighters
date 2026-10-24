package com.example.ui.coach

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
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachDashboardScreen(
    batches: List<Batch>,
    students: List<Student>,
    branches: List<Branch>,
    classSessions: List<ClassSession> = emptyList(),
    programs: List<Program> = emptyList(),
    beltRanks: List<BeltRank> = emptyList(),
    gradingAssessments: List<GradingAssessment> = emptyList(),
    coachProfile: UserProfile? = null,
    onMarkAttendance: (Attendance) -> Unit,
    onSaveAssessment: (ProgressRecord) -> Unit,
    onSubmitGradingAssessment: (GradingAssessment) -> Unit = {},
    onPublishAnnouncement: (Announcement) -> Unit,
    onSignOut: () -> Unit
) {
    val assignedBatches = remember(batches, coachProfile) {
        if (coachProfile == null || (coachProfile.branchIds.isEmpty() && coachProfile.batchIds.isEmpty())) {
            batches
        } else {
            batches.filter { b ->
                (coachProfile.branchIds.isNotEmpty() && coachProfile.branchIds.contains(b.branchId)) ||
                (coachProfile.batchIds.isNotEmpty() && coachProfile.batchIds.contains(b.id))
            }
        }
    }

    var selectedBatchId by remember(assignedBatches) {
        mutableStateOf(assignedBatches.firstOrNull()?.id ?: "")
    }
    var selectedTab by remember { mutableStateOf(0) } // 0: Attendance Roster, 1: Skill Assessments, 2: Announcements

    val currentBatch = assignedBatches.find { it.id == selectedBatchId } ?: assignedBatches.firstOrNull()

    val assignedBranchIds = coachProfile?.branchIds ?: emptyList()
    val assignedBatchIds = coachProfile?.batchIds ?: emptyList()

    val eligibleStudents = remember(students, assignedBranchIds, assignedBatchIds, currentBatch) {
        val baseList = if (assignedBranchIds.isEmpty() && assignedBatchIds.isEmpty()) {
            students
        } else {
            students.filter { s ->
                (assignedBranchIds.isNotEmpty() && assignedBranchIds.contains(s.branchId)) ||
                (assignedBatchIds.isNotEmpty() && assignedBatchIds.contains(s.batchId))
            }
        }
        if (currentBatch != null && currentBatch.id.isNotBlank()) {
            val batchSpecific = baseList.filter { it.batchId == currentBatch.id || it.batchName == currentBatch.name }
            if (batchSpecific.isNotEmpty()) batchSpecific else baseList
        } else {
            baseList
        }
    }

    val batchStudents = eligibleStudents.ifEmpty {
        listOf(
            Student(id = "std_1", fullName = "Arjun Verma", currentBelt = "Yellow Belt", branchName = "Central Dojo", batchName = currentBatch?.name ?: "Morning Warriors"),
            Student(id = "std_2", fullName = "Neha Sharma", currentBelt = "Green Belt", branchName = "Central Dojo", batchName = currentBatch?.name ?: "Morning Warriors"),
            Student(id = "std_3", fullName = "Vikram Aditya", currentBelt = "White Belt", branchName = "Central Dojo", batchName = currentBatch?.name ?: "Morning Warriors")
        )
    }

    // Assessment dialog state
    var assessingStudent by remember { mutableStateOf<Student?>(null) }
    var newRank by remember { mutableStateOf("Yellow Belt") }
    var newRankOrder by remember { mutableStateOf(2) }
    var scoreText by remember { mutableStateOf("85") }
    var techniqueGrade by remember { mutableStateOf("A") }
    var skillNotes by remember { mutableStateOf("") }
    var assessmentNotice by remember { mutableStateOf<String?>(null) }

    // Announcement state
    var announcementTitle by remember { mutableStateOf("") }
    var announcementMessage by remember { mutableStateOf("") }
    var announcementSentNotice by remember { mutableStateOf<String?>(null) }

    val coachBranchName = branches.find { coachProfile?.branchIds?.contains(it.id) == true }?.name
        ?: coachProfile?.preferredBranchName
        ?: "Assigned Dojo"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.SportsMartialArts, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Coach: ${coachProfile?.displayName?.ifBlank { "Sensei" } ?: "Sensei"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "$coachBranchName • ${currentBatch?.name ?: "Assigned Classes"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut, modifier = Modifier.testTag("coach_logout_button")) {
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
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RedPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Mark Attendance", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Belt Assessment", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Post Notice", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Mark Attendance Tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "Session: Today's Class", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "${batchStudents.size} Enrolled Fighters", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SuccessGreen
                                    ) {
                                        Text(
                                            text = "Active Session",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        items(batchStudents) { student ->
                            var markedStatus by remember { mutableStateOf("present") }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = student.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            BeltBadge(rank = student.currentBelt)
                                        }
                                        StatusBadge(status = markedStatus)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quick Status Buttons: Present, Late, Excused, Absent
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("present", "late", "excused", "absent").forEach { status ->
                                            val isSelected = markedStatus == status
                                            val (btnColor, txtColor) = when (status) {
                                                "present" -> Pair(SuccessGreen, Color.White)
                                                "late" -> Pair(Color(0xFFFF9800), Color.White)
                                                "excused" -> Pair(Color(0xFF1976D2), Color.White)
                                                else -> Pair(RedPrimary, Color.White)
                                            }
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        markedStatus = status
                                                        val att = Attendance(
                                                            branchId = currentBatch?.branchId ?: "branch_main",
                                                            sessionId = "session_today",
                                                            batchId = currentBatch?.id ?: "batch_1",
                                                            studentId = student.id,
                                                            studentUserId = student.userId.ifBlank { student.id },
                                                            studentName = student.fullName,
                                                            date = "Today",
                                                            status = status
                                                        )
                                                        onMarkAttendance(att)
                                                    },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) btnColor else MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = status.replaceFirstChar { it.uppercase() },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) txtColor else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Belt Assessment Tab
                    val myAssessments = remember(gradingAssessments, coachProfile, assignedBranchIds) {
                        gradingAssessments.filter { a ->
                            (coachProfile != null && (a.coachId == coachProfile.id || a.coachName == coachProfile.displayName)) ||
                            (assignedBranchIds.isNotEmpty() && assignedBranchIds.contains(a.branchId))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Skill & Belt Assessment",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Evaluate student technique, kata & sparring, and submit promotion recommendations for Admin approval",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (assessmentNotice != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessGreen.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = assessmentNotice ?: "", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        items(batchStudents) { student ->
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("assess_card_${student.id}"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = student.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Current: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            BeltBadge(rank = student.currentBelt)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            assessingStudent = student
                                            newRank = "Yellow Belt"
                                            newRankOrder = 2
                                            scoreText = "85"
                                            techniqueGrade = "Distinction"
                                            skillNotes = "Crisp chambering in kicks, strong stance transitions and good kata timing."
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                        modifier = Modifier.testTag("grade_student_${student.id}")
                                    ) {
                                        Text("Assess / Grade")
                                    }
                                }
                            }
                        }

                        if (myAssessments.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Submitted Grading Assessments",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            items(myAssessments) { assess ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = assess.studentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(text = "Date: ${assess.assessmentDate} • Score: ${assess.score}/100", fontSize = 11.sp, color = TextMuted)
                                            }
                                            StatusBadge(status = assess.status)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            BeltBadge(rank = assess.currentBeltName)
                                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 4.dp))
                                            BeltBadge(rank = assess.proposedBeltName)
                                        }
                                        if (assess.isApproved && assess.certificateId.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "Official Certificate Issued", fontSize = 11.sp, color = GoldBelt, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Post Batch Notice Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Send Batch Notice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Broadcast schedule updates or sparring gear requirements to students",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (announcementSentNotice != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SuccessGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = announcementSentNotice ?: "",
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = announcementTitle,
                            onValueChange = { announcementTitle = it },
                            label = { Text("Notice Title *") },
                            modifier = Modifier.fillMaxWidth().testTag("coach_notice_title"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = announcementMessage,
                            onValueChange = { announcementMessage = it },
                            label = { Text("Announcement Message *") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("coach_notice_message")
                        )

                        Button(
                            onClick = {
                                if (announcementTitle.isNotBlank() && announcementMessage.isNotBlank()) {
                                    val ann = Announcement(
                                        title = announcementTitle.trim(),
                                        message = announcementMessage.trim(),
                                        priority = "Important",
                                        branchIds = listOf(currentBatch?.branchId ?: "branch_main"),
                                        targetBatchIds = listOf(currentBatch?.id ?: "batch_1"),
                                        publishedByName = "Master Coach Arvind"
                                    )
                                    onPublishAnnouncement(ann)
                                    announcementSentNotice = "Notice '$announcementTitle' broadcasted to batch students!"
                                    announcementTitle = ""
                                    announcementMessage = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("coach_post_notice_btn")
                        ) {
                            Text("Broadcast Notice", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Assessment Grading Modal
    assessingStudent?.let { student ->
        val studentProgramId = student.programId.ifBlank { "prog_karate" }
        val availableRanks = remember(beltRanks, studentProgramId) {
            val ranks = beltRanks.filter { it.programId == studentProgramId }
            if (ranks.isNotEmpty()) ranks else listOf(
                BeltRank(beltName = "Yellow Belt", rankOrder = 2),
                BeltRank(beltName = "Orange Belt", rankOrder = 3),
                BeltRank(beltName = "Green Belt", rankOrder = 4),
                BeltRank(beltName = "Blue Belt", rankOrder = 5),
                BeltRank(beltName = "Brown Belt", rankOrder = 6),
                BeltRank(beltName = "Black Belt 1st Dan", rankOrder = 7)
            )
        }

        AlertDialog(
            onDismissRequest = { assessingStudent = null },
            title = { Text("Belt Assessment: ${student.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Current Rank: ${student.currentBelt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Text(text = "Select Target Rank *", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableRanks.take(4).forEach { r ->
                            FilterChip(
                                selected = newRank == r.beltName,
                                onClick = {
                                    newRank = r.beltName
                                    newRankOrder = r.rankOrder
                                },
                                label = { Text(r.beltName.replace(" Belt", ""), fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = scoreText,
                            onValueChange = { scoreText = it },
                            label = { Text("Score (0-100) *") },
                            modifier = Modifier.weight(1f).testTag("assessment_score_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = techniqueGrade,
                            onValueChange = { techniqueGrade = it },
                            label = { Text("Grade") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = skillNotes,
                        onValueChange = { skillNotes = it },
                        label = { Text("Coach Notes & Technical Feedback *") },
                        modifier = Modifier.fillMaxWidth().testTag("assessment_notes_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val coachName = coachProfile?.displayName?.ifBlank { "Master Coach" } ?: "Master Coach"
                        val assessment = GradingAssessment(
                            studentId = student.id,
                            studentUserId = student.userId.ifBlank { student.id },
                            studentName = student.fullName,
                            programId = student.programId.ifBlank { "prog_karate" },
                            programName = student.programName.ifBlank { "Martial Arts" },
                            branchId = currentBatch?.branchId ?: student.branchId.ifBlank { "branch_main" },
                            branchName = currentBatch?.branchName ?: student.branchName.ifBlank { coachBranchName },
                            batchId = currentBatch?.id ?: student.batchId,
                            currentBeltName = student.currentBelt,
                            proposedBeltName = newRank,
                            proposedRankOrder = newRankOrder,
                            coachId = coachProfile?.id ?: "",
                            coachName = coachName,
                            assessmentDate = "2026-10-23",
                            score = scoreText.toIntOrNull() ?: 80,
                            techniqueGrade = techniqueGrade,
                            status = "pending_approval",
                            coachComments = skillNotes
                        )
                        onSubmitGradingAssessment(assessment)

                        val record = ProgressRecord(
                            branchId = student.branchId.ifBlank { "branch_main" },
                            studentId = student.id,
                            studentUserId = student.userId.ifBlank { student.id },
                            studentName = student.fullName,
                            discipline = "Martial Arts",
                            rank = newRank,
                            assessmentDate = "23 Oct 2026",
                            skillNotes = skillNotes,
                            coachName = coachName
                        )
                        onSaveAssessment(record)

                        assessmentNotice = "Submitted grading assessment for ${student.fullName} -> $newRank. Pending admin approval."
                        assessingStudent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    modifier = Modifier.testTag("submit_grading_assessment_btn")
                ) {
                    Text("Submit for Admin Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { assessingStudent = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
