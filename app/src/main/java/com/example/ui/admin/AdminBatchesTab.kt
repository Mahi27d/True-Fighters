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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBatchesTab(
    batches: List<Batch>,
    programs: List<Program>,
    branches: List<Branch>,
    coaches: List<UserProfile>,
    students: List<Student>,
    onCreateBatch: (Batch) -> Unit,
    onUpdateBatch: (Batch) -> Unit,
    onToggleBatchStatus: (batchId: String, currentActive: Boolean) -> Unit,
    onManageStudentMembership: (batchId: String, studentId: String, action: String, targetBatchId: String?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedBranchFilter by remember { mutableStateOf("all") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingBatch by remember { mutableStateOf<Batch?>(null) }
    var rosterBatch by remember { mutableStateOf<Batch?>(null) }

    val filteredBatches = remember(batches, searchQuery, selectedBranchFilter) {
        batches.filter { b ->
            val matchesQuery = searchQuery.isBlank() ||
                b.effectiveName.contains(searchQuery, ignoreCase = true) ||
                b.programName.contains(searchQuery, ignoreCase = true) ||
                b.coachName.contains(searchQuery, ignoreCase = true)
            val matchesBranch = selectedBranchFilter == "all" || b.branchId == selectedBranchFilter
            matchesQuery && matchesBranch
        }
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
                    text = "Batch Management",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredBatches.size} active batches across branches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.testTag("admin_add_batch_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Batch")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search batches by name, program, or coach...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("batch_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredBatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No batches matching '$searchQuery'" else "No batches configured yet.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBatches, key = { it.effectiveId }) { batch ->
                    BatchCard(
                        batch = batch,
                        onEdit = { editingBatch = batch },
                        onManageRoster = { rosterBatch = batch },
                        onToggleStatus = { onToggleBatchStatus(batch.effectiveId, batch.isBatchActive) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        BatchEditDialog(
            batch = null,
            programs = programs,
            branches = branches,
            coaches = coaches,
            onDismiss = { showCreateDialog = false },
            onSave = {
                onCreateBatch(it)
                showCreateDialog = false
            }
        )
    }

    editingBatch?.let { batch ->
        BatchEditDialog(
            batch = batch,
            programs = programs,
            branches = branches,
            coaches = coaches,
            onDismiss = { editingBatch = null },
            onSave = {
                onUpdateBatch(it)
                editingBatch = null
            }
        )
    }

    rosterBatch?.let { batch ->
        BatchRosterDialog(
            batch = batch,
            allBatches = batches,
            allStudents = students,
            onDismiss = { rosterBatch = null },
            onManageStudent = { sId, act, targetBId ->
                onManageStudentMembership(batch.effectiveId, sId, act, targetBId)
            }
        )
    }
}

@Composable
private fun BatchCard(
    batch: Batch,
    onEdit: () -> Unit,
    onManageRoster: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_card_${batch.effectiveId}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = batch.effectiveName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${batch.programName} • ${batch.branchName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = if (batch.isBatchActive) "active" else "inactive")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = batch.schedule.ifBlank { "Regular Dojo Hours" }, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(6.dp))

            val coachDisplayName = if (batch.coachNames.isNotEmpty()) {
                batch.coachNames.joinToString(", ")
            } else {
                batch.coachName.ifBlank { "Assigned Dojo Sensei" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SportsMartialArts, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldBelt)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Coaches: $coachDisplayName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capacity indicator
            val enrolled = batch.enrolledCount
            val cap = batch.capacity
            val isFull = enrolled >= cap && cap > 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Roster: $enrolled / $cap enrolled",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isFull) RedPrimary else SuccessGreen
                )
                Text(
                    text = "${batch.availableCapacity} slots available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val progressFraction = if (cap > 0) (enrolled.toFloat() / cap.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isFull) RedPrimary else RedPrimary.copy(alpha = 0.8f),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleStatus,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (batch.isBatchActive) Color(0xFFC62828) else SuccessGreen
                    )
                ) {
                    Text(if (batch.isBatchActive) "Deactivate" else "Activate")
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = onManageRoster,
                    modifier = Modifier.testTag("manage_roster_button_${batch.effectiveId}")
                ) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Roster (${enrolled})")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text("Edit", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchEditDialog(
    batch: Batch?,
    programs: List<Program>,
    branches: List<Branch>,
    coaches: List<UserProfile>,
    onDismiss: () -> Unit,
    onSave: (Batch) -> Unit
) {
    var name by remember { mutableStateOf(batch?.effectiveName ?: "") }
    var selectedBranchId by remember { mutableStateOf(batch?.branchId?.ifBlank { branches.firstOrNull()?.effectiveId ?: "" } ?: "") }
    var selectedProgramId by remember { mutableStateOf(batch?.programId?.ifBlank { programs.firstOrNull()?.effectiveId ?: "" } ?: "") }
    var schedule by remember { mutableStateOf(batch?.schedule ?: "Mon, Wed, Fri - 5:00 PM to 6:30 PM") }
    var capacityStr by remember { mutableStateOf((batch?.capacity ?: 30L).toString()) }

    // Coach multi-selection
    var selectedCoachIds by remember { mutableStateOf(batch?.effectiveCoachIds ?: emptyList()) }

    // Eligible coaches: coaches assigned to selected branch
    val eligibleCoaches = remember(coaches, selectedBranchId) {
        coaches.filter { c ->
            c.role == "coach" && (c.branchIds.isEmpty() || c.branchIds.contains(selectedBranchId))
        }
    }

    val selectedBranch = branches.find { it.effectiveId == selectedBranchId }
    val selectedProgram = programs.find { it.effectiveId == selectedProgramId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (batch == null) "Create Academy Batch" else "Edit Batch") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Batch Name * (e.g. Morning Tigers)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_name_input")
                    )
                }
                item {
                    Text("Branch Assignment:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    branches.filter { it.isBranchActive }.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBranchId = b.effectiveId }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedBranchId == b.effectiveId,
                                onClick = { selectedBranchId = b.effectiveId }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(b.effectiveName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    Text("Martial Arts Program:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    programs.filter { it.isProgramActive }.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProgramId = p.effectiveId }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedProgramId == p.effectiveId,
                                onClick = { selectedProgramId = p.effectiveId }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${p.effectiveName} (${p.discipline})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    Text("Assigned Coaches (Authorized for Branch):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    if (eligibleCoaches.isEmpty()) {
                        Text("No coaches currently assigned to this branch.", color = TextMuted, fontSize = 12.sp)
                    } else {
                        eligibleCoaches.forEach { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCoachIds = if (selectedCoachIds.contains(c.userId)) {
                                            selectedCoachIds - c.userId
                                        } else {
                                            selectedCoachIds + c.userId
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = selectedCoachIds.contains(c.userId),
                                    onCheckedChange = { checked ->
                                        selectedCoachIds = if (checked == true) {
                                            selectedCoachIds + c.userId
                                        } else {
                                            selectedCoachIds - c.userId
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(c.displayName.ifBlank { c.phoneNumber }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = schedule,
                        onValueChange = { schedule = it },
                        label = { Text("Class Schedule Description *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = capacityStr,
                        onValueChange = { capacityStr = it },
                        label = { Text("Maximum Student Capacity *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && selectedBranchId.isNotBlank()) {
                        val cap = capacityStr.toLongOrNull() ?: 30L
                        val assignedCoachObjs = eligibleCoaches.filter { selectedCoachIds.contains(it.userId) }
                        val updated = (batch ?: Batch()).copy(
                            name = name.trim(),
                            batchName = name.trim(),
                            branchId = selectedBranchId,
                            branchName = selectedBranch?.effectiveName ?: "",
                            programId = selectedProgramId,
                            programName = selectedProgram?.effectiveName ?: "",
                            coachIds = selectedCoachIds,
                            coachNames = assignedCoachObjs.map { it.displayName },
                            coachId = selectedCoachIds.firstOrNull() ?: "",
                            coachName = assignedCoachObjs.firstOrNull()?.displayName ?: "",
                            schedule = schedule.trim(),
                            capacity = cap
                        )
                        onSave(updated)
                    }
                },
                enabled = name.isNotBlank() && selectedBranchId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.testTag("save_batch_button")
            ) {
                Text("Save Batch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BatchRosterDialog(
    batch: Batch,
    allBatches: List<Batch>,
    allStudents: List<Student>,
    onDismiss: () -> Unit,
    onManageStudent: (studentId: String, action: String, targetBatchId: String?) -> Unit
) {
    var showAddStudentSection by remember { mutableStateOf(false) }
    var transferringStudentId by remember { mutableStateOf<String?>(null) }
    var transferTargetBatchId by remember { mutableStateOf("") }

    val enrolledStudents = remember(allStudents, batch) {
        allStudents.filter { s ->
            batch.studentIds.contains(s.id) || s.batchId == batch.effectiveId
        }
    }

    val eligibleStudentsToAdd = remember(allStudents, batch) {
        allStudents.filter { s ->
            s.branchId == batch.branchId &&
            s.status.lowercase() == "active" &&
            !batch.studentIds.contains(s.id) &&
            s.batchId != batch.effectiveId
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Batch Roster: ${batch.effectiveName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${enrolledStudents.size} of ${batch.capacity} Students Enrolled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showAddStudentSection) {
                    item {
                        Text("Add Eligible Student to Batch:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    if (eligibleStudentsToAdd.isEmpty()) {
                        item {
                            Text("No unassigned active students found in branch '${batch.branchName}'.", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        items(eligibleStudentsToAdd) { std ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(std.fullName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(std.currentBelt, fontSize = 11.sp, color = TextMuted)
                                    }
                                    Button(
                                        onClick = {
                                            onManageStudent(std.id, "add", null)
                                            showAddStudentSection = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Enroll", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { showAddStudentSection = false }) {
                            Text("Back to Roster")
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enrolled Students:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            if (batch.availableCapacity > 0) {
                                TextButton(onClick = { showAddStudentSection = true }) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enroll Student", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (enrolledStudents.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No students currently enrolled in this batch.", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(enrolledStudents) { std ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(std.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${std.currentBelt} • ${std.phoneNumber}", fontSize = 11.sp, color = TextMuted)
                                        }
                                        Row {
                                            TextButton(
                                                onClick = { transferringStudentId = std.id },
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text("Transfer", fontSize = 11.sp)
                                            }
                                            TextButton(
                                                onClick = { onManageStudent(std.id, "remove", null) },
                                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828)),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text("Remove", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    if (transferringStudentId == std.id) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Select Target Batch:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        val otherBatches = allBatches.filter { it.effectiveId != batch.effectiveId && it.branchId == batch.branchId }
                                        otherBatches.forEach { otherB ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { transferTargetBatchId = otherB.effectiveId }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = transferTargetBatchId == otherB.effectiveId,
                                                    onClick = { transferTargetBatchId = otherB.effectiveId }
                                                )
                                                Text(otherB.effectiveName, fontSize = 12.sp)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(onClick = { transferringStudentId = null }) { Text("Cancel", fontSize = 11.sp) }
                                            Button(
                                                onClick = {
                                                    if (transferTargetBatchId.isNotBlank()) {
                                                        onManageStudent(std.id, "transfer", transferTargetBatchId)
                                                        transferringStudentId = null
                                                    }
                                                },
                                                enabled = transferTargetBatchId.isNotBlank(),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Confirm Transfer", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
