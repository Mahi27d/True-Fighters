package com.example.ui.admission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AdmissionRequest
import com.example.data.model.Branch
import com.example.data.model.Program

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmissionDialog(
    branches: List<Branch>,
    programs: List<Program>,
    onDismiss: () -> Unit,
    onSubmit: (AdmissionRequest) -> Unit
) {
    val activeBranches = remember(branches) { branches.filter { it.isBranchActive } }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var isMinor by remember { mutableStateOf(false) }
    var guardianName by remember { mutableStateOf("") }
    var guardianPhone by remember { mutableStateOf("") }
    var guardianConsent by remember { mutableStateOf(false) }

    var selectedBranch by remember(activeBranches) { mutableStateOf(activeBranches.firstOrNull()?.effectiveName ?: "True Fighters Central Dojo") }
    var selectedBranchId by remember(activeBranches) { mutableStateOf(activeBranches.firstOrNull()?.effectiveId ?: "branch_main") }
    var branchExpanded by remember { mutableStateOf(false) }

    var selectedProgram by remember { mutableStateOf(programs.firstOrNull()?.name ?: "Traditional Karate") }
    var selectedProgramId by remember { mutableStateOf(programs.firstOrNull()?.id ?: "prog_karate") }
    var programExpanded by remember { mutableStateOf(false) }

    var preferredBatch by remember { mutableStateOf("Morning Warriors (6:30 AM)") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Academy Admission",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "True Fighters Martial Arts Academy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_admission_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Student Full Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("admission_fullname"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address *") },
                        modifier = Modifier.fillMaxWidth().testTag("admission_email"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Verified Mobile Phone Number *") },
                        modifier = Modifier.fillMaxWidth().testTag("admission_phone"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it },
                        label = { Text("Date of Birth (DD/MM/YYYY) *") },
                        modifier = Modifier.fillMaxWidth().testTag("admission_dob"),
                        singleLine = true
                    )

                    // Active Branch Selector
                    ExposedDropdownMenuBox(
                        expanded = branchExpanded,
                        onExpandedChange = { branchExpanded = !branchExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedBranch,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Preferred Active Academy Branch *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("admission_branch_selector")
                        )
                        ExposedDropdownMenu(
                            expanded = branchExpanded,
                            onDismissRequest = { branchExpanded = false }
                        ) {
                            activeBranches.ifEmpty {
                                listOf(
                                    Branch(id = "branch_main", name = "True Fighters Central Dojo"),
                                    Branch(id = "branch_west", name = "True Fighters West Arena"),
                                    Branch(id = "branch_south", name = "True Fighters South Elite")
                                )
                            }.forEach { branch ->
                                DropdownMenuItem(
                                    text = { Text(branch.effectiveName) },
                                    onClick = {
                                        selectedBranch = branch.effectiveName
                                        selectedBranchId = branch.effectiveId
                                        branchExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Program Selector
                    ExposedDropdownMenuBox(
                        expanded = programExpanded,
                        onExpandedChange = { programExpanded = !programExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedProgram,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Martial Arts Discipline *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = programExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("admission_program_selector")
                        )
                        ExposedDropdownMenu(
                            expanded = programExpanded,
                            onDismissRequest = { programExpanded = false }
                        ) {
                            programs.ifEmpty {
                                listOf(
                                    Program(id = "prog_karate", name = "Traditional Karate"),
                                    Program(id = "prog_taekwondo", name = "Olympic Taekwondo"),
                                    Program(id = "prog_kickboxing", name = "K-1 Kickboxing & Muay Thai"),
                                    Program(id = "prog_mma", name = "Mixed Martial Arts (MMA)"),
                                    Program(id = "prog_selfdefense", name = "Practical Self-Defense")
                                )
                            }.forEach { prog ->
                                DropdownMenuItem(
                                    text = { Text(prog.name) },
                                    onClick = {
                                        selectedProgram = prog.name
                                        selectedProgramId = prog.id
                                        programExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = preferredBatch,
                        onValueChange = { preferredBatch = it },
                        label = { Text("Preferred Batch Timings") },
                        modifier = Modifier.fillMaxWidth().testTag("admission_batch"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Applicant is a minor (under 18)", fontWeight = FontWeight.SemiBold)
                        Switch(checked = isMinor, onCheckedChange = { isMinor = it }, modifier = Modifier.testTag("admission_minor_switch"))
                    }

                    OutlinedTextField(
                        value = guardianName,
                        onValueChange = { guardianName = it },
                        label = { Text(if (isMinor) "Parent / Guardian Name *" else "Parent / Guardian Name (if under 18)") },
                        modifier = Modifier.fillMaxWidth().testTag("admission_guardian_name"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = guardianPhone,
                        onValueChange = { guardianPhone = it },
                        label = { Text(if (isMinor) "Parent / Guardian Phone *" else "Parent / Guardian Phone") },
                        modifier = Modifier.fillMaxWidth().testTag("admission_guardian_phone"),
                        singleLine = true
                    )

                    if (isMinor) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = guardianConsent,
                                onCheckedChange = { guardianConsent = it },
                                modifier = Modifier.testTag("admission_guardian_consent")
                            )
                            Text("Parent/Guardian consents to martial arts training and safety rules *", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (fullName.isBlank() || email.isBlank() || phone.isBlank() || dateOfBirth.isBlank()) {
                            errorMessage = "Please enter student name, email, verified phone, and date of birth."
                            return@Button
                        }
                        if (isMinor && (guardianName.isBlank() || guardianPhone.isBlank() || !guardianConsent)) {
                            errorMessage = "Guardian name, phone, and consent are required for minor applicants."
                            return@Button
                        }
                        isSubmitting = true
                        val req = AdmissionRequest(
                            fullName = fullName.trim(),
                            email = email.trim(),
                            phoneNumber = phone.trim(),
                            dateOfBirth = dateOfBirth.trim(),
                            isMinor = isMinor,
                            programId = selectedProgramId,
                            programName = selectedProgram,
                            preferredBranchId = selectedBranchId,
                            preferredBranchName = selectedBranch,
                            preferredBatchId = preferredBatch,
                            guardianName = guardianName.trim(),
                            guardianPhone = guardianPhone.trim(),
                            guardianConsent = guardianConsent,
                            status = "pending"
                        )
                        onSubmit(req)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_admission_button"),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Submit Admission Request", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
