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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScheduleTab(
    classSessions: List<ClassSession>,
    batches: List<Batch>,
    branches: List<Branch>,
    coaches: List<UserProfile>,
    onCreateSession: (ClassSession) -> Unit,
    onRescheduleOrCancel: (sessionId: String, newDate: String?, newStart: String?, newEnd: String?, status: String?, reason: String) -> Unit
) {
    var viewMode by remember { mutableStateOf("daily") } // "daily", "weekly", "all"
    var selectedDate by remember { mutableStateOf(LocalDate.now().toString()) } // "YYYY-MM-DD"
    var selectedBranchFilter by remember { mutableStateOf("all") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<ClassSession?>(null) }

    val filteredSessions = remember(classSessions, viewMode, selectedDate, selectedBranchFilter) {
        classSessions.filter { sess ->
            val matchesBranch = selectedBranchFilter == "all" || sess.branchId == selectedBranchFilter
            val matchesDate = when (viewMode) {
                "daily" -> sess.effectiveDate == selectedDate
                "weekly" -> {
                    // within 7 days from selectedDate
                    val sessDate = sess.effectiveDate
                    sessDate >= selectedDate && sessDate <= LocalDate.parse(selectedDate).plusDays(6).toString()
                }
                else -> true
            }
            matchesBranch && matchesDate
        }.sortedBy { "${it.effectiveDate} ${it.startTime}" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Class Schedules",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Asia/Kolkata (IST) Standard Timezone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.testTag("admin_schedule_session_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Schedule Class")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // View Mode Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = viewMode == "daily",
                onClick = { viewMode = "daily" },
                label = { Text("Daily View") },
                leadingIcon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = viewMode == "weekly",
                onClick = { viewMode = "weekly" },
                label = { Text("Weekly View") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = viewMode == "all",
                onClick = { viewMode = "all" },
                label = { Text("All Upcoming") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Date selection bar
        if (viewMode == "daily" || viewMode == "weekly") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    selectedDate = LocalDate.parse(selectedDate).minusDays(if (viewMode == "weekly") 7 else 1).toString()
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                }
                Text(
                    text = if (viewMode == "daily") "Date: $selectedDate" else "Week starting: $selectedDate",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                IconButton(onClick = {
                    selectedDate = LocalDate.parse(selectedDate).plusDays(if (viewMode == "weekly") 7 else 1).toString()
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(48.dp), tint = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No class sessions scheduled for this period.", color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredSessions, key = { it.effectiveId }) { session ->
                    SessionCard(
                        session = session,
                        onRescheduleOrCancel = { editingSession = session }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            batches = batches,
            branches = branches,
            coaches = coaches,
            defaultDate = selectedDate,
            onDismiss = { showCreateDialog = false },
            onSave = {
                onCreateSession(it)
                showCreateDialog = false
            }
        )
    }

    editingSession?.let { session ->
        RescheduleOrCancelDialog(
            session = session,
            onDismiss = { editingSession = null },
            onConfirm = { date, start, end, status, reason ->
                onRescheduleOrCancel(session.effectiveId, date, start, end, status, reason)
                editingSession = null
            }
        )
    }
}

@Composable
private fun SessionCard(
    session: ClassSession,
    onRescheduleOrCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card_${session.effectiveId}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${session.batchName} • ${session.programName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = session.branchName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = session.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = RedPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(session.effectiveDate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = RedPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(session.effectiveTimeSlot, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val coachName = if (session.coachNames.isNotEmpty()) {
                session.coachNames.joinToString(", ")
            } else session.coachName.ifBlank { "Assigned Coach" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldBelt)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Coach: $coachName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onRescheduleOrCancel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reschedule / Cancel", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSessionDialog(
    batches: List<Batch>,
    branches: List<Branch>,
    coaches: List<UserProfile>,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (ClassSession) -> Unit
) {
    var selectedBranchId by remember { mutableStateOf(branches.firstOrNull { it.isBranchActive }?.effectiveId ?: "") }
    var selectedBatchId by remember { mutableStateOf(batches.firstOrNull { it.branchId == selectedBranchId }?.effectiveId ?: "") }
    var date by remember { mutableStateOf(defaultDate) }
    var startTime by remember { mutableStateOf("17:00") }
    var endTime by remember { mutableStateOf("18:30") }

    val activeBranches = branches.filter { it.isBranchActive }
    val branchBatches = batches.filter { it.branchId == selectedBranchId && it.isBatchActive }
    val currentBatch = batches.find { it.effectiveId == selectedBatchId }
    val selectedBranch = branches.find { it.effectiveId == selectedBranchId }

    // Coach candidates
    val branchCoaches = coaches.filter { c ->
        c.role == "coach" && (c.branchIds.isEmpty() || c.branchIds.contains(selectedBranchId))
    }
    var selectedCoachId by remember(currentBatch, branchCoaches) {
        mutableStateOf(currentBatch?.coachId?.ifBlank { branchCoaches.firstOrNull()?.userId ?: "" } ?: "")
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Class Session") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("1. Select Branch:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    activeBranches.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBranchId = b.effectiveId
                                    selectedBatchId = batches.firstOrNull { it.branchId == b.effectiveId }?.effectiveId ?: ""
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedBranchId == b.effectiveId,
                                onClick = {
                                    selectedBranchId = b.effectiveId
                                    selectedBatchId = batches.firstOrNull { it.branchId == b.effectiveId }?.effectiveId ?: ""
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(b.effectiveName, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    Text("2. Select Batch:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    if (branchBatches.isEmpty()) {
                        Text("No active batches for this branch.", color = TextMuted, fontSize = 12.sp)
                    } else {
                        branchBatches.forEach { b ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBatchId = b.effectiveId }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = selectedBatchId == b.effectiveId,
                                    onClick = { selectedBatchId = b.effectiveId }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${b.effectiveName} (${b.programName})", fontSize = 13.sp)
                            }
                        }
                    }
                }
                item {
                    Text("3. Assigned Coach:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    branchCoaches.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCoachId = c.userId }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedCoachId == c.userId,
                                onClick = { selectedCoachId = c.userId }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(c.displayName.ifBlank { c.phoneNumber }, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Session Date (YYYY-MM-DD) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("session_date_input")
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("Start Time (HH:mm) *") },
                            modifier = Modifier.weight(1f).testTag("session_start_time_input")
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("End Time (HH:mm) *") },
                            modifier = Modifier.weight(1f).testTag("session_end_time_input")
                        )
                    }
                }
                if (errorMessage != null) {
                    item {
                        Text(errorMessage!!, color = Color(0xFFC62828), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (startTime >= endTime) {
                        errorMessage = "Start time must be strictly before end time."
                        return@Button
                    }
                    if (date.isBlank() || selectedBranchId.isBlank() || selectedBatchId.isBlank()) {
                        errorMessage = "Please fill all required fields."
                        return@Button
                    }
                    val assignedCoach = coaches.find { it.userId == selectedCoachId }
                    val session = ClassSession(
                        branchId = selectedBranchId,
                        branchName = selectedBranch?.effectiveName ?: "",
                        batchId = selectedBatchId,
                        batchName = currentBatch?.effectiveName ?: "",
                        programId = currentBatch?.programId ?: "",
                        programName = currentBatch?.programName ?: "",
                        coachId = selectedCoachId,
                        coachName = assignedCoach?.displayName ?: "",
                        coachIds = if (selectedCoachId.isNotBlank()) listOf(selectedCoachId) else emptyList(),
                        coachNames = if (assignedCoach != null) listOf(assignedCoach.displayName) else emptyList(),
                        sessionDate = date.trim(),
                        date = date.trim(),
                        startTime = startTime.trim(),
                        endTime = endTime.trim(),
                        timeSlot = "${startTime.trim()} - ${endTime.trim()}",
                        status = "scheduled",
                        capacity = currentBatch?.capacity ?: 30L
                    )
                    onSave(session)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.testTag("save_session_button")
            ) {
                Text("Schedule Session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RescheduleOrCancelDialog(
    session: ClassSession,
    onDismiss: () -> Unit,
    onConfirm: (newDate: String?, newStart: String?, newEnd: String?, status: String?, reason: String) -> Unit
) {
    var newDate by remember { mutableStateOf(session.effectiveDate) }
    var newStart by remember { mutableStateOf(session.startTime) }
    var newEnd by remember { mutableStateOf(session.endTime) }
    var selectedStatus by remember { mutableStateOf(session.status) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule / Modify Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Class: ${session.batchName} (${session.programName})", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                OutlinedTextField(
                    value = newDate,
                    onValueChange = { newDate = it },
                    label = { Text("Rescheduled Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newStart,
                        onValueChange = { newStart = it },
                        label = { Text("Start (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = newEnd,
                        onValueChange = { newEnd = it },
                        label = { Text("End (HH:mm)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Session Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("scheduled", "completed", "cancelled").forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Modification") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(newDate.trim(), newStart.trim(), newEnd.trim(), selectedStatus, reason.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text("Apply Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}
