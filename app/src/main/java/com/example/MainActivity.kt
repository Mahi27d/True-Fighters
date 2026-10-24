package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Student
import com.example.data.repository.TrueFightersRepository
import com.example.features.authentication.AuthScreen
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.coach.CoachDashboardScreen
import com.example.ui.student.StudentDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RedDark
import com.example.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

    private val repository by lazy { TrueFightersRepository(this) }
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppContent(viewModel: MainViewModel) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    val roleOverride by viewModel.roleOverride.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val classSessions by viewModel.classSessions.collectAsStateWithLifecycle()
    val attendanceList by viewModel.attendanceList.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val progressRecords by viewModel.progressRecords.collectAsStateWithLifecycle()
    val certificates by viewModel.certificates.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val admissionRequests by viewModel.admissionRequests.collectAsStateWithLifecycle()
    val coaches by viewModel.coaches.collectAsStateWithLifecycle()
    val feePlans by viewModel.feePlans.collectAsStateWithLifecycle()

    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid != null) {
            viewModel.refreshUserProfile(uid)
        }
    }

    val isPendingOrSuspendedProfile = currentUserProfile != null && (
        !currentUserProfile!!.active ||
        currentUserProfile!!.status.lowercase() in listOf("pending", "rejected", "suspended", "inactive")
    )
    val isBootstrapAdmin = currentUser?.email == "Mahi27d@gmail.com" || currentUser?.phoneNumber == "+919876500001"
    val isAuthenticated = roleOverride != null || (
        currentUser != null &&
        (isBootstrapAdmin || (currentUserProfile != null && !isPendingOrSuspendedProfile))
    )

    if (!isAuthenticated) {
        AuthScreen(
            branches = branches,
            programs = programs,
            repository = viewModel.repository,
            onAuthSuccess = { role ->
                viewModel.setRoleOverride(role)
                currentUser = Firebase.auth.currentUser
            },
            onSubmitAdmission = { req ->
                viewModel.submitAdmission(req)
            }
        )
    } else {
        // Authenticated Session / Role Router
        val effectiveRole = when {
            roleOverride != null -> roleOverride!!
            currentUserProfile?.role?.isNotBlank() == true -> currentUserProfile!!.role.lowercase()
            currentUser?.email == "Mahi27d@gmail.com" || currentUser?.phoneNumber == "+919876500001" -> "admin"
            else -> "student"
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (effectiveRole) {
                "admin" -> {
                    AdminDashboardScreen(
                        branches = branches,
                        admissionRequests = admissionRequests,
                        students = students,
                        batches = batches,
                        invoices = invoices,
                        payments = payments,
                        programs = programs,
                        classSessions = classSessions,
                        coaches = coaches,
                        attendanceList = attendanceList,
                        feePlans = feePlans,
                        adminProfile = currentUserProfile,
                        onAddBranch = { viewModel.addBranch(it) },
                        onUpdateBranch = { viewModel.updateBranch(it) },
                        onToggleBranchStatus = { bId, active -> viewModel.toggleBranchStatus(bId, active) },
                        onApproveAdmission = { viewModel.approveAdmission(it) },
                        onRejectAdmission = { req, reason -> viewModel.rejectAdmission(req, reason) },
                        onRecordManualPayment = { viewModel.recordManualPayment(it) },
                        onGenerateInvoice = { viewModel.generateFeeInvoice(it) },
                        onCreateFeePlan = { viewModel.createFeePlan(it) },
                        onUpdateFeePlan = { viewModel.updateFeePlan(it) },
                        onToggleFeePlanStatus = { pId, active -> viewModel.toggleFeePlanStatus(pId, active) },
                        onRefundPayment = { payId, reason -> viewModel.refundPayment(payId, reason) },
                        onProvisionCoach = { viewModel.provisionCoach(it) },
                        onAssignCoachBranches = { uid, bIds -> viewModel.assignCoachBranches(uid, bIds) },
                        onAssignStudentBranch = { sId, uId, bId, bName -> viewModel.assignStudentBranch(sId, uId, bId, bName) },
                        onUpdateStudentProfile = { viewModel.updateStudentProfile(it) },
                        onSetStudentStatus = { sId, uId, status, reason -> viewModel.setStudentStatus(sId, uId, status, reason) },
                        onCreateProgram = { viewModel.createOrUpdateProgram(it) },
                        onUpdateProgram = { viewModel.createOrUpdateProgram(it) },
                        onToggleProgramStatus = { pId, active -> viewModel.setProgramStatus(pId, if (active) "inactive" else "active") },
                        onCreateBatch = { viewModel.createOrUpdateBatch(it) },
                        onUpdateBatch = { viewModel.createOrUpdateBatch(it) },
                        onToggleBatchStatus = { bId, active -> viewModel.setBatchStatus(bId, if (active) "inactive" else "active") },
                        onManageBatchStudent = { bId, sId, act, targetBId -> viewModel.manageBatchStudent(bId, sId, act, targetBId) },
                        onCreateSession = { viewModel.createClassSession(it) },
                        onRescheduleOrCancelSession = { sId, date, start, end, status, reason -> viewModel.rescheduleOrCancelSession(sId, date, start, end, status, reason) },
                        onSignOut = {
                            Firebase.auth.signOut()
                            viewModel.setRoleOverride(null)
                        }
                    )
                }
                "coach" -> {
                    CoachDashboardScreen(
                        batches = batches,
                        students = students,
                        branches = branches,
                        classSessions = classSessions,
                        coachProfile = currentUserProfile,
                        onMarkAttendance = { viewModel.markAttendance(it) },
                        onSaveAssessment = { viewModel.addProgressRecord(it) },
                        onPublishAnnouncement = { viewModel.publishAnnouncement(it) },
                        onSignOut = {
                            Firebase.auth.signOut()
                            viewModel.setRoleOverride(null)
                        }
                    )
                }
                else -> {
                    // Student Portal
                    val myStudent = students.firstOrNull { it.userId == currentUser?.uid }
                        ?: students.firstOrNull()
                        ?: Student(
                            fullName = currentUser?.displayName ?: "Rohan Sharma",
                            userId = currentUser?.uid ?: "std_default",
                            branchName = "True Fighters Central Dojo",
                            batchName = "Morning Warriors",
                            programName = "Traditional Karate",
                            currentBelt = "Yellow Belt"
                        )

                    StudentDashboardScreen(
                        currentStudent = myStudent,
                        branches = branches,
                        batches = batches,
                        attendanceList = attendanceList,
                        classSessions = classSessions,
                        invoices = invoices,
                        payments = payments,
                        progressRecords = progressRecords,
                        certificates = certificates,
                        videos = videos,
                        announcements = announcements,
                        onPayInvoice = { pay -> viewModel.recordPayment(pay) },
                        onSignOut = {
                            Firebase.auth.signOut()
                            viewModel.setRoleOverride(null)
                        }
                    )
                }
            }

            // Floating Role Switcher Pill for quick testing in emulator
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 80.dp, end = 16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = RedDark,
                shadowElevation = 6.dp
            ) {
                var menuExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .clickable { menuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Switch Role",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Role: ${effectiveRole.replaceFirstChar { it.uppercase() }}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Switch to Student Portal") },
                            onClick = {
                                viewModel.setRoleOverride("student")
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Switch to Coach Portal") },
                            onClick = {
                                viewModel.setRoleOverride("coach")
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Switch to Admin Portal") },
                            onClick = {
                                viewModel.setRoleOverride("admin")
                                menuExpanded = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Sign Out") },
                            onClick = {
                                Firebase.auth.signOut()
                                viewModel.setRoleOverride(null)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
