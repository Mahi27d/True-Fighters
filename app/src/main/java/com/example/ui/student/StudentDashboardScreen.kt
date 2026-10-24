package com.example.ui.student

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
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.getBeltColor
import com.example.ui.payment.PaymentDialog
import com.example.ui.payment.ReceiptDialog
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.video.VideoPlayerDialog

enum class StudentTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("Home", Icons.Default.Home),
    SCHEDULE("Schedule", Icons.Default.CalendarMonth),
    ATTENDANCE("Attendance", Icons.Default.CheckCircleOutline),
    FEES("Fees", Icons.Default.ReceiptLong),
    PROGRESS("Progress", Icons.Default.MilitaryTech),
    VIDEOS("Videos", Icons.Default.PlayCircleOutline)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    currentStudent: Student,
    branches: List<Branch>,
    batches: List<Batch>,
    attendanceList: List<Attendance>,
    classSessions: List<ClassSession> = emptyList(),
    invoices: List<FeeInvoice>,
    payments: List<Payment>,
    progressRecords: List<ProgressRecord>,
    certificates: List<Certificate>,
    videos: List<Video>,
    beltRanks: List<BeltRank> = emptyList(),
    gradingAssessments: List<GradingAssessment> = emptyList(),
    announcements: List<Announcement>,
    onPayInvoice: (Payment) -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(StudentTab.OVERVIEW) }
    var payingInvoice by remember { mutableStateOf<FeeInvoice?>(null) }
    var viewingPaymentReceipt by remember { mutableStateOf<Payment?>(null) }
    var viewingCertificate by remember { mutableStateOf<Certificate?>(null) }
    var viewingVideo by remember { mutableStateOf<Video?>(null) }
    var selectedVideoCategory by remember { mutableStateOf("All") }

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
                            Text("TF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "True Fighters Academy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentStudent.branchName.ifBlank { "Central Dojo" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut, modifier = Modifier.testTag("student_logout_button")) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = "Log Out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                StudentTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RedPrimary,
                            selectedTextColor = RedPrimary,
                            indicatorColor = RedPrimary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                StudentTab.OVERVIEW -> StudentOverviewTab(
                    student = currentStudent,
                    attendanceList = attendanceList,
                    invoices = invoices,
                    announcements = announcements,
                    videos = videos,
                    onNavigateToTab = { selectedTab = it },
                    onPayNow = { payingInvoice = it },
                    onWatchVideo = { viewingVideo = it }
                )
                StudentTab.SCHEDULE -> StudentScheduleTab(batches = batches, classSessions = classSessions)
                StudentTab.ATTENDANCE -> StudentAttendanceTab(attendanceList = attendanceList)
                StudentTab.FEES -> StudentFeesTab(
                    invoices = invoices,
                    payments = payments,
                    onPayInvoice = { payingInvoice = it },
                    onViewReceipt = { viewingPaymentReceipt = it }
                )
                StudentTab.PROGRESS -> StudentProgressTab(
                    student = currentStudent,
                    progressRecords = progressRecords,
                    certificates = certificates,
                    beltRanks = beltRanks,
                    gradingAssessments = gradingAssessments,
                    onViewCertificate = { viewingCertificate = it }
                )
                StudentTab.VIDEOS -> StudentVideosTab(
                    student = currentStudent,
                    videos = videos,
                    selectedCategory = selectedVideoCategory,
                    onCategorySelect = { selectedVideoCategory = it },
                    onWatchVideo = { viewingVideo = it }
                )
            }
        }
    }

    // Payment Dialog
    payingInvoice?.let { inv ->
        PaymentDialog(
            invoice = inv,
            onDismiss = { payingInvoice = null },
            onPaymentSuccess = { pay ->
                onPayInvoice(pay)
                viewingPaymentReceipt = pay
            }
        )
    }

    // Receipt Dialog
    viewingPaymentReceipt?.let { pay ->
        ReceiptDialog(
            payment = pay,
            onDismiss = { viewingPaymentReceipt = null }
        )
    }

    // Video Player Dialog
    viewingVideo?.let { vid ->
        VideoPlayerDialog(
            video = vid,
            onDismiss = { viewingVideo = null }
        )
    }

    // Certificate Dialog
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

@Composable
private fun StudentOverviewTab(
    student: Student,
    attendanceList: List<Attendance>,
    invoices: List<FeeInvoice>,
    announcements: List<Announcement>,
    videos: List<Video>,
    onNavigateToTab: (StudentTab) -> Unit,
    onPayNow: (FeeInvoice) -> Unit,
    onWatchVideo: (Video) -> Unit
) {
    val totalSessions = attendanceList.size
    val presentSessions = attendanceList.count { it.status == "present" }
    val attendancePercent = if (totalSessions > 0) (presentSessions * 100) / totalSessions else 92

    val unpaidInvoice = invoices.firstOrNull { it.status == "unpaid" || it.status == "partially_paid" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Student Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Welcome back,",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = student.fullName.ifBlank { "True Fighter" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${student.programName.ifBlank { "Traditional Karate" }} • ${student.batchName.ifBlank { "Morning Warriors" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BeltBadge(rank = student.currentBelt.ifBlank { "White Belt" })
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    // Next Class Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RedPrimary.copy(alpha = 0.12f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = RedPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Next Academy Class",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedPrimary
                                )
                                Text(
                                    text = "Today • 6:30 PM (Mat A)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = RedPrimary
                        ) {
                            Text(
                                text = "Confirmed",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Attendance",
                    value = "$attendancePercent%",
                    icon = Icons.Default.DoneAll,
                    iconTint = SuccessGreen,
                    subtitle = "$presentSessions attended",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Current Belt",
                    value = student.currentBelt.ifBlank { "White" }.replace("Belt", "").trim(),
                    icon = Icons.Default.MilitaryTech,
                    iconTint = GoldBelt,
                    subtitle = "Next: Exam Dec",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Fee Alert Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (unpaidInvoice != null) RedPrimary.copy(alpha = 0.08f) else SuccessGreen.copy(alpha = 0.08f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (unpaidInvoice != null) RedPrimary.copy(alpha = 0.4f) else SuccessGreen.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (unpaidInvoice != null) "Monthly Fee Due" else "Fee Status: Up to Date",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (unpaidInvoice != null) RedPrimary else SuccessGreen
                        )
                        Text(
                            text = if (unpaidInvoice != null) "₹${"%,.2f".format(unpaidInvoice.amountPaise / 100.0)} for ${unpaidInvoice.period}" else "All payments clear",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (unpaidInvoice != null) {
                        Button(
                            onClick = { onPayNow(unpaidInvoice) },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            modifier = Modifier.testTag("overview_pay_now_button")
                        ) {
                            Text("Pay Now")
                        }
                    } else {
                        OutlinedButton(onClick = { onNavigateToTab(StudentTab.FEES) }) {
                            Text("View Receipts")
                        }
                    }
                }
            }
        }

        // Latest Announcements
        item {
            Text(
                text = "Academy Announcements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(announcements.take(2)) { ann ->
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
                        Text(
                            text = ann.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        StatusBadge(status = ann.priority)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ann.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "From: ${ann.publishedByName}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Featured Training Video
        item {
            Text(
                text = "Featured Training Video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        videos.firstOrNull()?.let { vid ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWatchVideo(vid) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = RedPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = vid.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(text = "${vid.category} • ${vid.durationText}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentScheduleTab(batches: List<Batch>, classSessions: List<ClassSession> = emptyList()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Class Schedule & Batches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "True Fighters Academy weekly training schedules",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (classSessions.isNotEmpty()) {
            item {
                Text(
                    text = "Upcoming Class Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(classSessions.take(5)) { sess ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${sess.batchName} (${sess.programName})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${sess.effectiveDate} • ${sess.effectiveTimeSlot}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sess.branchName, fontSize = 11.sp, color = TextMuted)
                        }
                        StatusBadge(status = sess.status)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Weekly Batch Schedules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(batches) { batch ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = batch.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RedPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = batch.programName,
                                color = RedPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = batch.schedule, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = batch.branchName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Coach: ${batch.coachName}",
                        fontSize = 12.sp,
                        color = GoldBelt,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentAttendanceTab(attendanceList: List<Attendance>) {
    val total = attendanceList.size
    val present = attendanceList.count { it.status == "present" }
    val late = attendanceList.count { it.status == "late" }
    val percent = if (total > 0) (present * 100) / total else 94

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Attendance Records",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(title = "Monthly Attendance", value = "$percent%", icon = Icons.Default.Percent, modifier = Modifier.weight(1f))
                StatCard(title = "Classes Attended", value = "$present / $total", icon = Icons.Default.Check, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Recent Class Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (attendanceList.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No attendance records logged yet.", color = TextMuted)
                    }
                }
            }
        }

        items(attendanceList) { att ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Date: ${att.date.ifBlank { "Session Log" }}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(text = "Marked by: ${att.markedBy}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusBadge(status = att.status)
                }
            }
        }
    }
}

@Composable
private fun StudentFeesTab(
    invoices: List<FeeInvoice>,
    payments: List<Payment>,
    onPayInvoice: (FeeInvoice) -> Unit,
    onViewReceipt: (Payment) -> Unit
) {
    val totalDuePaise = invoices.sumOf { it.balancePaise }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Fee Invoices & Payments",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track your monthly invoices, outstanding dues, and official receipts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (totalDuePaise > 0) RedPrimary.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (totalDuePaise > 0) "Total Outstanding Dues" else "All Dues Cleared",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (totalDuePaise > 0) RedPrimary else SuccessGreen
                        )
                        Text(
                            text = "₹${"%,.2f".format(totalDuePaise / 100.0)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = if (totalDuePaise > 0) RedPrimary else SuccessGreen
                        )
                    }
                    Icon(
                        imageVector = if (totalDuePaise > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (totalDuePaise > 0) RedPrimary else SuccessGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Invoices (${invoices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (invoices.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No fee invoices issued yet.", color = TextMuted)
                    }
                }
            }
        }

        items(invoices) { inv ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Period: ${inv.period.ifBlank { "Monthly Fees" }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (inv.invoiceNumber.isNotBlank()) {
                                Text(text = "Invoice #${inv.invoiceNumber}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        StatusBadge(status = inv.status)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Amount: ₹${"%,.2f".format(inv.amountPaise / 100.0)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Paid: ₹${"%,.2f".format(inv.paidPaise / 100.0)}", fontSize = 12.sp, color = SuccessGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Balance: ₹${"%,.2f".format(inv.balancePaise / 100.0)}", fontWeight = FontWeight.Bold, color = if (inv.balancePaise > 0) RedPrimary else SuccessGreen)
                            Text(text = "Due: ${inv.dueDate.ifBlank { "31st of month" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (inv.itemizedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        inv.itemizedItems.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "• ${item.title}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "₹${"%,.2f".format(item.amountPaise / 100.0)}", fontSize = 11.sp)
                            }
                        }
                    }

                    if (inv.balancePaise > 0L) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onPayInvoice(inv) },
                            modifier = Modifier.fillMaxWidth().testTag("pay_invoice_btn_${inv.id}")
                        ) {
                            Text("Pay Fees Online (UPI / Card)")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Payment History & Receipts (${payments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (payments.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No payment receipts yet.", color = TextMuted)
                    }
                }
            }
        }

        items(payments) { pay ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "₹${"%,.2f".format(pay.amountPaise / 100.0)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (pay.status == "refunded") TextMuted else SuccessGreen)
                        Text(text = "Method: ${pay.method.uppercase()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (pay.providerReference.isNotBlank()) {
                            Text(text = "Ref: ${pay.providerReference}", fontSize = 11.sp, color = GoldBelt)
                        }
                    }
                    OutlinedButton(onClick = { onViewReceipt(pay) }) {
                        Icon(imageVector = Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Receipt")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentProgressTab(
    student: Student,
    progressRecords: List<ProgressRecord>,
    certificates: List<Certificate>,
    beltRanks: List<BeltRank> = emptyList(),
    gradingAssessments: List<GradingAssessment> = emptyList(),
    onViewCertificate: (Certificate) -> Unit = {}
) {
    val studentProgramId = student.programId.ifBlank { "prog_karate" }
    val programRanks = remember(beltRanks, studentProgramId) {
        val filtered = beltRanks.filter { it.programId == studentProgramId }.sortedBy { it.rankOrder }
        if (filtered.isNotEmpty()) filtered else listOf(
            BeltRank(beltName = "White Belt", rankOrder = 1, syllabus = "Basics: Seiken Tsuki, Age Uke, Mae Geri"),
            BeltRank(beltName = "Yellow Belt", rankOrder = 2, minClassesRequired = 24, minMonthsRequired = 2, syllabus = "Kata: Taikyoku Shodan. Strikes: Mawashi Geri"),
            BeltRank(beltName = "Orange Belt", rankOrder = 3, minClassesRequired = 48, minMonthsRequired = 4, syllabus = "Kata: Heian Shodan. Basic Kumite"),
            BeltRank(beltName = "Green Belt", rankOrder = 4, minClassesRequired = 72, minMonthsRequired = 6, syllabus = "Kata: Heian Nidan. Sparring timing"),
            BeltRank(beltName = "Blue Belt", rankOrder = 5, minClassesRequired = 100, minMonthsRequired = 9, syllabus = "Kata: Heian Sandan. Controlled Kumite"),
            BeltRank(beltName = "Brown Belt", rankOrder = 6, minClassesRequired = 140, minMonthsRequired = 12, syllabus = "Kata: Heian Godan, Tekki Shodan"),
            BeltRank(beltName = "Black Belt 1st Dan", rankOrder = 7, minClassesRequired = 220, minMonthsRequired = 24, syllabus = "Kata: Bassai Dai, Kanku Dai. Mastery in Kumite")
        )
    }

    val currentRankIndex = remember(programRanks, student.currentBelt) {
        val idx = programRanks.indexOfFirst { it.beltName.equals(student.currentBelt, ignoreCase = true) }
        if (idx >= 0) idx else 0
    }

    val myAssessments = remember(gradingAssessments, student.id, student.userId) {
        gradingAssessments.filter { it.studentId == student.id || (student.userId.isNotBlank() && it.studentUserId == student.userId) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Martial Arts Progress & Belt Journey",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track your ranking ladder, upcoming belt requirements, and coach assessments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Belt Journey Progression Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${student.programName.ifBlank { "Karate" }} Curriculum",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        BeltBadge(rank = student.currentBelt)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    programRanks.forEachIndexed { index, rank ->
                        val isAchieved = index <= currentRankIndex
                        val isCurrent = index == currentRankIndex
                        val isNext = index == currentRankIndex + 1

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isAchieved) SuccessGreen else if (isNext) GoldBelt else Color.Gray.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isAchieved) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                } else if (isNext) {
                                    Icon(imageVector = Icons.Default.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            BeltBadge(rank = rank.beltName)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isCurrent) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = RedPrimary
                                ) {
                                    Text(
                                        text = "CURRENT",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (isNext) {
                                Text(
                                    text = "NEXT GOAL (${rank.minClassesRequired} classes)",
                                    color = GoldBelt,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Coach Assessments & Grading Reviews",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (myAssessments.isEmpty() && progressRecords.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Initial Assessment: Good form & striking posture. Eligible for next belt grading exam after completing required classes.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "By: Master Arvind Singh • Chief Examiner", fontSize = 11.sp, color = GoldBelt, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        items(myAssessments) { assess ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BeltBadge(rank = assess.proposedBeltName)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Score: ${assess.score}/100", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        StatusBadge(status = assess.status)
                    }
                    if (assess.coachComments.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = assess.coachComments, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Examiner: ${assess.coachName} • ${assess.assessmentDate}", fontSize = 11.sp, color = GoldBelt)
                }
            }
        }

        items(progressRecords) { rec ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BeltBadge(rank = rec.rank)
                        Text(text = rec.assessmentDate.ifBlank { "Recent" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = rec.skillNotes, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Examiner: ${rec.coachName}", fontSize = 11.sp, color = GoldBelt)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Earned Certificates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (certificates.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Certificates are issued post belt grading exams.", color = TextMuted)
                    }
                }
            }
        }

        items(certificates) { cert ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onViewCertificate(cert) }, shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldBelt, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = cert.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = cert.effectiveNumber, fontSize = 12.sp, color = GoldBelt, fontWeight = FontWeight.SemiBold)
                            Text(text = "Awarded: ${cert.effectiveBelt} • ${cert.issuedAt}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(onClick = { onViewCertificate(cert) }) {
                        Text("View")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentVideosTab(
    student: Student,
    videos: List<Video>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onWatchVideo: (Video) -> Unit
) {
    val categories = listOf("All", "Techniques", "Warmups", "Forms/Kata", "Sparring", "Self Defense")
    val filtered = if (selectedCategory == "All") videos else videos.filter { it.category.equals(selectedCategory, ignoreCase = true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Training Video Library",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Official True Fighters syllabus & technique masterclasses for your discipline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips Row
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = RedPrimary,
                edgePadding = 8.dp
            ) {
                categories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Tab(
                        selected = isSelected,
                        onClick = { onCategorySelect(cat) },
                        text = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (filtered.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No training videos in this category.", color = TextMuted)
                    }
                }
            }
        }

        items(filtered) { vid ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onWatchVideo(vid) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RedPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = vid.category,
                                color = RedPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(text = vid.durationText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = vid.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = vid.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Belt Level: ${vid.targetBelt}", fontSize = 11.sp, color = GoldBelt)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Watch Drill", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedPrimary)
                        }
                    }
                }
            }
        }
    }
}
