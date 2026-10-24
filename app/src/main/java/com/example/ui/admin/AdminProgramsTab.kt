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
import com.example.data.model.Branch
import com.example.data.model.Program
import com.example.ui.components.StatusBadge
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProgramsTab(
    programs: List<Program>,
    branches: List<Branch>,
    onCreateProgram: (Program) -> Unit,
    onUpdateProgram: (Program) -> Unit,
    onToggleProgramStatus: (programId: String, currentActive: Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedBranchFilter by remember { mutableStateOf("all") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProgram by remember { mutableStateOf<Program?>(null) }

    val filteredPrograms = remember(programs, searchQuery, selectedBranchFilter) {
        programs.filter { prog ->
            val matchesQuery = searchQuery.isBlank() ||
                prog.effectiveName.contains(searchQuery, ignoreCase = true) ||
                prog.discipline.contains(searchQuery, ignoreCase = true)
            val matchesBranch = selectedBranchFilter == "all" ||
                prog.branchIds.isEmpty() ||
                prog.branchIds.contains(selectedBranchFilter)
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
                    text = "Martial Arts Programs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredPrograms.size} programs configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.testTag("admin_add_program_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Program")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search & Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by program or discipline (e.g. Karate, MMA)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("program_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredPrograms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No programs matching '$searchQuery'" else "No programs configured yet.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPrograms, key = { it.effectiveId }) { prog ->
                    ProgramCard(
                        program = prog,
                        branches = branches,
                        onEdit = { editingProgram = prog },
                        onToggleStatus = { onToggleProgramStatus(prog.effectiveId, prog.isProgramActive) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ProgramEditDialog(
            program = null,
            branches = branches,
            onDismiss = { showCreateDialog = false },
            onSave = {
                onCreateProgram(it)
                showCreateDialog = false
            }
        )
    }

    editingProgram?.let { prog ->
        ProgramEditDialog(
            program = prog,
            branches = branches,
            onDismiss = { editingProgram = null },
            onSave = {
                onUpdateProgram(it)
                editingProgram = null
            }
        )
    }
}

@Composable
private fun ProgramCard(
    program: Program,
    branches: List<Branch>,
    onEdit: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("program_card_${program.effectiveId}"),
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
                        text = program.effectiveName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${program.discipline} • ${program.level}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = if (program.isProgramActive) "active" else "inactive")
            }

            if (program.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = program.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val branchLabels = if (program.branchIds.isEmpty()) {
                "All Academy Branches"
            } else {
                branches.filter { program.branchIds.contains(it.effectiveId) }
                    .joinToString(", ") { it.effectiveName }
                    .ifBlank { "Selected Branches (${program.branchIds.size})" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = branchLabels,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onToggleStatus,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (program.isProgramActive) Color(0xFFC62828) else SuccessGreen
                    )
                ) {
                    Text(if (program.isProgramActive) "Deactivate" else "Activate")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text("Edit Program", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditDialog(
    program: Program?,
    branches: List<Branch>,
    onDismiss: () -> Unit,
    onSave: (Program) -> Unit
) {
    var name by remember { mutableStateOf(program?.effectiveName ?: "") }
    var discipline by remember { mutableStateOf(program?.discipline ?: "Karate") }
    var level by remember { mutableStateOf(program?.level ?: "All Levels") }
    var description by remember { mutableStateOf(program?.description ?: "") }
    var selectedBranchIds by remember { mutableStateOf(program?.branchIds ?: emptyList()) }

    val disciplineOptions = listOf("Karate", "Taekwondo", "Kickboxing", "MMA", "Boxing", "Jiu-Jitsu", "Self Defense")
    val levelOptions = listOf("All Levels", "Beginner", "Intermediate", "Advanced", "Youth & Kids")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (program == null) "Create Martial Arts Program" else "Edit Program") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Program Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("program_name_input")
                    )
                }
                item {
                    Text("Discipline / Martial Art:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        disciplineOptions.take(4).forEach { disc ->
                            FilterChip(
                                selected = discipline == disc,
                                onClick = { discipline = disc },
                                label = { Text(disc, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        disciplineOptions.drop(4).forEach { disc ->
                            FilterChip(
                                selected = discipline == disc,
                                onClick = { discipline = disc },
                                label = { Text(disc, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                item {
                    Text("Training Level:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        levelOptions.take(3).forEach { lvl ->
                            FilterChip(
                                selected = level == lvl,
                                onClick = { level = lvl },
                                label = { Text(lvl, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description & Syllabus Details") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Branch Availability:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    branches.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBranchIds = if (selectedBranchIds.contains(b.effectiveId)) {
                                        selectedBranchIds - b.effectiveId
                                    } else {
                                        selectedBranchIds + b.effectiveId
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedBranchIds.isEmpty() || selectedBranchIds.contains(b.effectiveId),
                                onCheckedChange = { checked ->
                                    selectedBranchIds = if (checked == true) {
                                        selectedBranchIds + b.effectiveId
                                    } else {
                                        selectedBranchIds - b.effectiveId
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(b.effectiveName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val updated = (program ?: Program()).copy(
                            name = name.trim(),
                            programName = name.trim(),
                            discipline = discipline,
                            level = level,
                            description = description.trim(),
                            branchIds = selectedBranchIds
                        )
                        onSave(updated)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.testTag("save_program_button")
            ) {
                Text("Save Program")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
