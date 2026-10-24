package com.example.ui.admin

import androidx.compose.foundation.background
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
import com.example.data.model.Attendance
import com.example.data.model.Branch
import com.example.ui.components.StatCard
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAttendanceReportsTab(
    attendanceList: List<Attendance>,
    branches: List<Branch>
) {
    var selectedBranchFilter by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("all") }

    val filteredRecords = remember(attendanceList, selectedBranchFilter, searchQuery, selectedStatusFilter) {
        attendanceList.filter { att ->
            val matchesBranch = selectedBranchFilter == "all" || att.branchId == selectedBranchFilter
            val matchesStatus = selectedStatusFilter == "all" || att.status.lowercase() == selectedStatusFilter.lowercase()
            val matchesQuery = searchQuery.isBlank() || att.studentName.contains(searchQuery, ignoreCase = true)
            matchesBranch && matchesStatus && matchesQuery
        }.sortedByDescending { it.date }
    }

    val totalRecords = filteredRecords.size
    val presentCount = filteredRecords.count { it.status.lowercase() == "present" }
    val lateCount = filteredRecords.count { it.status.lowercase() == "late" }
    val excusedCount = filteredRecords.count { it.status.lowercase() == "excused" }
    val absentCount = filteredRecords.count { it.status.lowercase() == "absent" }
    val attendanceRate = if (totalRecords > 0) ((presentCount + lateCount) * 100) / totalRecords else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Branch Attendance Reports",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time audit trail and attendance telemetry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Attendance Rate",
                    value = "$attendanceRate%",
                    icon = Icons.Default.Percent,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Logs",
                    value = "$totalRecords",
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Absent",
                    value = "$absentCount",
                    icon = Icons.Default.Cancel,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedStatusFilter == "all",
                    onClick = { selectedStatusFilter = "all" },
                    label = { Text("All ($totalRecords)") }
                )
                FilterChip(
                    selected = selectedStatusFilter == "present",
                    onClick = { selectedStatusFilter = "present" },
                    label = { Text("Present ($presentCount)") }
                )
                FilterChip(
                    selected = selectedStatusFilter == "late",
                    onClick = { selectedStatusFilter = "late" },
                    label = { Text("Late ($lateCount)") }
                )
                FilterChip(
                    selected = selectedStatusFilter == "excused",
                    onClick = { selectedStatusFilter = "excused" },
                    label = { Text("Excused ($excusedCount)") }
                )
                FilterChip(
                    selected = selectedStatusFilter == "absent",
                    onClick = { selectedStatusFilter = "absent" },
                    label = { Text("Absent ($absentCount)") }
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter attendance by student name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attendance_report_search_input")
            )
        }

        if (filteredRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No attendance logs found matching filters.", color = TextMuted)
                }
            }
        } else {
            items(filteredRecords, key = { it.id }) { att ->
                AttendanceLogCard(att = att)
            }
        }
    }
}

@Composable
private fun AttendanceLogCard(att: Attendance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = att.studentName.ifBlank { "Student ${att.studentId}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Date: ${att.date} • Marked by: ${att.markedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (att.correctionHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Corrected (${att.correctionHistory.size} time(s))",
                        fontSize = 11.sp,
                        color = GoldBelt,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (att.status.lowercase()) {
                    "present" -> SuccessGreen.copy(alpha = 0.15f)
                    "late" -> GoldBelt.copy(alpha = 0.2f)
                    "excused" -> Color(0xFF1976D2).copy(alpha = 0.15f)
                    else -> Color(0xFFC62828).copy(alpha = 0.15f)
                }
            ) {
                Text(
                    text = att.status.replaceFirstChar { it.uppercase() },
                    color = when (att.status.lowercase()) {
                        "present" -> SuccessGreen
                        "late" -> GoldBelt
                        "excused" -> Color(0xFF1976D2)
                        else -> Color(0xFFC62828)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
