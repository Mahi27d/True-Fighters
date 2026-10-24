package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Branch(
    @DocumentId val id: String = "",
    val branchId: String = "",
    val name: String = "",
    val branchName: String = "",
    val code: String = "",
    val branchCode: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val phone: String = "",
    val contactPhone: String = "",
    val status: String = "active", // "active", "inactive"
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { branchId }
    val effectiveName: String get() = branchName.ifBlank { name }
    val effectiveCode: String get() = branchCode.ifBlank { code }
    val effectivePhone: String get() = contactPhone.ifBlank { phone }
    val isBranchActive: Boolean get() = active && status.lowercase() != "inactive"
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val role: String = "student", // "student", "coach", "admin"
    val status: String = "active", // "pending", "approved", "active", "suspended", "inactive"
    val branchIds: List<String> = emptyList(),
    val batchIds: List<String> = emptyList(),
    val active: Boolean = true,
    val isMinor: Boolean = false,
    val dateOfBirth: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianConsent: Boolean = false,
    val preferredBranchId: String = "",
    val preferredBranchName: String = "",
    val programName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed class RoleValidationResult {
    data class Success(val profile: UserProfile, val effectiveRole: String) : RoleValidationResult()
    data class RoleMismatch(val assignedRole: String, val message: String) : RoleValidationResult()
    data class AccountInactive(val message: String) : RoleValidationResult()
    data class AccountSuspended(val message: String) : RoleValidationResult()
    data class RegistrationPending(val profile: UserProfile, val message: String) : RoleValidationResult()
    data class NeedsRegistration(val phoneNumber: String, val userId: String) : RoleValidationResult()
    data class Unregistered(val message: String) : RoleValidationResult()
    data class Error(val message: String) : RoleValidationResult()
}

@IgnoreExtraProperties
data class AdminRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val scope: String = "global", // "global" or "branch"
    val branchIds: List<String> = emptyList(),
    val active: Boolean = true,
    val provisionedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdmissionRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val dateOfBirth: String = "",
    val isMinor: Boolean = false,
    val programId: String = "",
    val programName: String = "",
    val preferredBranchId: String = "",
    val preferredBranchName: String = "",
    val preferredBatchId: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianConsent: Boolean = false,
    val status: String = "pending", // "pending", "approved", "rejected"
    val reviewedBy: String = "",
    val rejectionReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val reviewedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id
}

@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val userId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val batchId: String = "",
    val batchName: String = "",
    val programId: String = "",
    val programName: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val dateOfBirth: String = "",
    val isMinor: Boolean = false,
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianConsent: Boolean = false,
    val joinDate: String = "",
    val currentBelt: String = "White Belt",
    val status: String = "active", // "active", "inactive", "suspended"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id
    val admissionDate: String get() = joinDate
}

@IgnoreExtraProperties
data class Program(
    @DocumentId val id: String = "",
    val programId: String = "",
    val name: String = "",
    val programName: String = "",
    val description: String = "",
    val discipline: String = "", // "Karate", "Taekwondo", "Kickboxing", "MMA", "Boxing", "Self Defense"
    val level: String = "All Levels", // "Beginner", "Intermediate", "Advanced", "All Levels"
    val category: String = "",
    val branchIds: List<String> = emptyList(), // branches where this program is offered
    val status: String = "active", // "active", "inactive"
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { programId }
    val effectiveName: String get() = programName.ifBlank { name }
    val isProgramActive: Boolean get() = active && status.lowercase() != "inactive"
}

@IgnoreExtraProperties
data class Batch(
    @DocumentId val id: String = "",
    val batchId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val programId: String = "",
    val programName: String = "",
    val coachId: String = "",
    val coachName: String = "",
    val coachIds: List<String> = emptyList(),
    val coachNames: List<String> = emptyList(),
    val studentIds: List<String> = emptyList(),
    val name: String = "",
    val batchName: String = "",
    val schedule: String = "", // e.g. "Mon, Wed, Fri - 5:00 PM to 6:30 PM"
    val capacity: Long = 30L,
    val status: String = "active", // "active", "inactive"
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { batchId }
    val effectiveName: String get() = batchName.ifBlank { name }
    val effectiveCoachIds: List<String> get() = if (coachIds.isNotEmpty()) coachIds else if (coachId.isNotBlank()) listOf(coachId) else emptyList()
    val isBatchActive: Boolean get() = active && status.lowercase() != "inactive"
    val enrolledCount: Int get() = studentIds.size
    val availableCapacity: Long get() = (capacity - enrolledCount).coerceAtLeast(0L)
}

@IgnoreExtraProperties
data class ClassSession(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val batchId: String = "",
    val batchName: String = "",
    val programId: String = "",
    val programName: String = "",
    val coachId: String = "",
    val coachName: String = "",
    val coachIds: List<String> = emptyList(),
    val coachNames: List<String> = emptyList(),
    val sessionDate: String = "", // "YYYY-MM-DD"
    val startTime: String = "", // "HH:mm" e.g. "17:00"
    val endTime: String = "", // "HH:mm" e.g. "18:30"
    val date: String = "", // fallback for sessionDate
    val timeSlot: String = "", // fallback e.g. "17:00 - 18:30"
    val status: String = "scheduled", // "scheduled", "in_progress", "completed", "cancelled"
    val capacity: Long = 30L,
    val timezone: String = "Asia/Kolkata",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { sessionId }
    val effectiveDate: String get() = sessionDate.ifBlank { date }
    val effectiveTimeSlot: String get() = if (timeSlot.isNotBlank()) timeSlot else "$startTime - $endTime"
    val effectiveCoachIds: List<String> get() = if (coachIds.isNotEmpty()) coachIds else if (coachId.isNotBlank()) listOf(coachId) else emptyList()
}

@IgnoreExtraProperties
data class Attendance(
    @DocumentId val id: String = "", // deterministic: sessionId_studentId
    val branchId: String = "",
    val sessionId: String = "",
    val batchId: String = "",
    val studentId: String = "",
    val studentUserId: String = "",
    val studentName: String = "",
    val date: String = "",
    val status: String = "present", // "present", "absent", "late", "excused"
    val markedBy: String = "",
    val correctionHistory: List<Map<String, String>> = emptyList(),
    @ServerTimestamp val markedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FeePlan(
    @DocumentId val id: String = "",
    val planId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val branchIds: List<String> = emptyList(),
    val programId: String = "",
    val programName: String = "",
    val name: String = "",
    val planName: String = "",
    val description: String = "",
    val amountPaise: Long = 250000L, // in paise (e.g. ₹2,500.00 = 250000)
    val billingPeriod: String = "Monthly", // "Monthly", "Quarterly", "Annual"
    val billingFrequency: String = "Monthly",
    val dueDay: Int = 10, // Due day of month (e.g., 5th or 10th)
    val status: String = "active", // "active", "inactive"
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { planId }
    val effectiveName: String get() = planName.ifBlank { name }
    val effectiveFrequency: String get() = billingFrequency.ifBlank { billingPeriod }
    val isPlanActive: Boolean get() = active && status.lowercase() != "inactive"
}

@IgnoreExtraProperties
data class InvoiceItem(
    val title: String = "",
    val amountPaise: Long = 0L
)

@IgnoreExtraProperties
data class FeeInvoice(
    @DocumentId val id: String = "",
    val invoiceId: String = "",
    val invoiceNumber: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val studentId: String = "",
    val studentUserId: String = "",
    val studentName: String = "",
    val feePlanId: String = "",
    val feePlanName: String = "",
    val period: String = "", // e.g. "November 2026"
    val itemizedItems: List<InvoiceItem> = emptyList(),
    val amountPaise: Long = 0L,
    val paidPaise: Long = 0L,
    val dueDate: String = "", // "YYYY-MM-DD"
    val status: String = "pending", // "pending", "partially_paid", "paid", "overdue", "refunded"
    val paymentIds: List<String> = emptyList(),
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { invoiceId }
    val effectiveStatus: String get() = status.lowercase().trim()
    val balancePaise: Long get() = (amountPaise - paidPaise).coerceAtLeast(0L)
    val isPaid: Boolean get() = effectiveStatus == "paid" || (amountPaise > 0 && paidPaise >= amountPaise)
    val isOverdue: Boolean get() = effectiveStatus == "overdue"
}

@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val paymentId: String = "",
    val paymentNumber: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val invoiceId: String = "",
    val studentId: String = "",
    val studentUserId: String = "",
    val studentName: String = "",
    val amountPaise: Long = 0L,
    val paymentDate: String = "", // "YYYY-MM-DD"
    val method: String = "cash", // "cash", "bank_transfer", "upi", "online"
    val providerReference: String = "", // e.g. UTR number, bank txn ID, receipt number
    val status: String = "verified", // "pending", "verified", "failed", "refunded"
    val recordedBy: String = "",
    val notes: String = "",
    val auditLog: List<Map<String, String>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val verifiedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { paymentId }
    val effectiveDate: String get() = paymentDate.ifBlank { verifiedAt?.toDate()?.toString() ?: "" }
}

@IgnoreExtraProperties
data class ProgressRecord(
    @DocumentId val id: String = "",
    val branchId: String = "",
    val studentId: String = "",
    val studentUserId: String = "",
    val studentName: String = "",
    val discipline: String = "",
    val rank: String = "", // e.g. "Yellow Belt", "Green Belt", "Brown Belt", "Black Belt 1st Dan"
    val assessmentDate: String = "",
    val skillNotes: String = "",
    val coachId: String = "",
    val coachName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BeltRank(
    @DocumentId val id: String = "",
    val rankId: String = "",
    val programId: String = "",
    val programName: String = "",
    val beltName: String = "",
    val rankOrder: Int = 1, // 1 = White, 2 = Yellow, etc. Higher number = higher rank
    val colorHex: String = "#FFFFFF",
    val stripeCount: Int = 0,
    val minClassesRequired: Int = 0,
    val minMonthsRequired: Int = 0,
    val syllabus: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { rankId }
}

@IgnoreExtraProperties
data class GradingAssessment(
    @DocumentId val id: String = "",
    val assessmentId: String = "",
    val studentId: String = "",
    val studentUserId: String = "",
    val studentName: String = "",
    val programId: String = "",
    val programName: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val batchId: String = "",
    val currentBeltRankId: String = "",
    val currentBeltName: String = "White Belt",
    val currentRankOrder: Int = 1,
    val proposedBeltRankId: String = "",
    val proposedBeltName: String = "Yellow Belt",
    val proposedRankOrder: Int = 2,
    val coachId: String = "",
    val coachName: String = "",
    val assessmentDate: String = "", // YYYY-MM-DD
    val score: Int = 0, // 0-100
    val techniqueGrade: String = "Pass", // "Pass", "A", "B", "Distinction"
    val kataOrFormsScore: Int = 0,
    val sparringScore: Int = 0,
    val status: String = "pending_approval", // "pending_approval", "approved", "rejected"
    val coachComments: String = "",
    val adminNotes: String = "",
    val approvedByAdminId: String = "",
    val approvedByAdminName: String = "",
    val approvedAt: String = "",
    val certificateId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { assessmentId }
    val isPending: Boolean get() = status.lowercase() == "pending_approval"
    val isApproved: Boolean get() = status.lowercase() == "approved"
    val isRejected: Boolean get() = status.lowercase() == "rejected"
}

@IgnoreExtraProperties
data class Certificate(
    @DocumentId val id: String = "",
    val certificateId: String = "",
    val certificateNumber: String = "", // e.g. "TF-CERT-KARATE-2026-00123"
    val branchId: String = "",
    val branchName: String = "",
    val studentId: String = "",
    val studentUserId: String = "",
    val studentName: String = "",
    val programId: String = "",
    val programName: String = "",
    val title: String = "Certificate of Belt Advancement",
    val discipline: String = "",
    val grade: String = "",
    val awardedBelt: String = "",
    val rankOrder: Int = 1,
    val assessmentId: String = "",
    val storagePath: String = "",
    val verificationCode: String = "",
    val issuedAt: String = "",
    val issuedBy: String = "",
    val issuedByAdminId: String = "",
    val status: String = "issued", // "issued", "revoked"
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { certificateId }
    val effectiveNumber: String get() = certificateNumber.ifBlank { "TF-CERT-${effectiveId.takeLast(6).uppercase()}" }
    val effectiveBelt: String get() = awardedBelt.ifBlank { grade }
    val effectiveProgram: String get() = programName.ifBlank { discipline }
}

@IgnoreExtraProperties
data class Video(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val title: String = "",
    val description: String = "",
    val programId: String = "",
    val programName: String = "",
    val category: String = "Techniques", // "Techniques", "Warmups", "Forms/Kata", "Sparring", "Self Defense", "Fitness"
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val durationText: String = "10 mins",
    val durationSeconds: Int = 600,
    val minRankOrder: Int = 1, // Minimum belt rank order required to view
    val targetBelt: String = "All", // e.g. "White Belt", "Yellow Belt", "All"
    val branchIds: List<String> = emptyList(), // empty = All branches
    val accessLevel: String = "All", // "All", "Beginner", "Advanced"
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val createdBy: String = "",
    val createdByName: String = ""
) {
    val effectiveId: String get() = id.ifBlank { videoId }
}

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val priority: String = "Normal", // "Normal", "Important", "Urgent"
    val branchIds: List<String> = emptyList(),
    val targetBatchIds: List<String> = emptyList(),
    val publishedByName: String = "Head Dojo Master",
    @ServerTimestamp val publishedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val branchId: String = "",
    val details: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
)

data class AcademyOverviewMetrics(
    val branchId: String = "all",
    val branchName: String = "All Branches",
    val totalActiveStudents: Int = 0,
    val totalSuspendedStudents: Int = 0,
    val pendingAdmissionsCount: Int = 0,
    val activeBranchesCount: Int = 0,
    val activeProgramsCount: Int = 0,
    val activeBatchesCount: Int = 0,
    val activeCoachesCount: Int = 0,
    val todayScheduledSessionsCount: Int = 0,
    val todayAttendanceMarkedCount: Int = 0,
    val todayPresentCount: Int = 0,
    val overallAttendanceRate: Int = 0,
    val totalCollectionsPaise: Long = 0L,
    val totalOutstandingPaise: Long = 0L,
    val overdueInvoicesCount: Int = 0,
    val overdueAmountPaise: Long = 0L,
    val totalRefundsPaise: Long = 0L,
    val pendingGradingAssessmentsCount: Int = 0,
    val totalApprovedPromotionsCount: Int = 0,
    val totalCertificatesIssuedCount: Int = 0
)

data class EnrollmentDistributionItem(
    val category: String,
    val activeCount: Int,
    val totalCount: Int,
    val percentage: Int
)

data class BatchOccupancyReportItem(
    val batchId: String,
    val batchName: String,
    val branchName: String,
    val programName: String,
    val capacity: Long,
    val enrolledCount: Int,
    val occupancyRate: Int,
    val coachName: String,
    val schedule: String
)

data class AttendanceReportSummary(
    val branchId: String = "all",
    val totalSessions: Int = 0,
    val totalLogs: Int = 0,
    val presentCount: Int = 0,
    val lateCount: Int = 0,
    val absentCount: Int = 0,
    val excusedCount: Int = 0,
    val presentRate: Int = 0
)

data class FinancialSummaryReport(
    val branchId: String = "all",
    val totalInvoicedPaise: Long = 0L,
    val totalCollectedPaise: Long = 0L,
    val totalOutstandingPaise: Long = 0L,
    val totalOverduePaise: Long = 0L,
    val totalRefundedPaise: Long = 0L,
    val paidInvoicesCount: Int = 0,
    val partialInvoicesCount: Int = 0,
    val unpaidInvoicesCount: Int = 0,
    val overdueInvoicesCount: Int = 0,
    val paymentsCount: Int = 0
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val notificationId: String = "",
    val recipientUserId: String = "",
    val recipientRole: String = "student", // "student", "coach", "admin", "all"
    val branchId: String = "",
    val batchId: String = "",
    val type: String = "general", // "admission", "schedule", "attendance", "fee", "grading", "announcement"
    val title: String = "",
    val body: String = "",
    val data: Map<String, String> = emptyMap(),
    val channelId: String = "general_notifications",
    val read: Boolean = false,
    val dedupKey: String = "",
    val createdByUid: String = "",
    val createdByEmail: String = "",
    val deliveryStatus: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val effectiveId: String get() = id.ifBlank { notificationId }
}

@IgnoreExtraProperties
data class NotificationPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val classRemindersEnabled: Boolean = true,
    val feeDueRemindersEnabled: Boolean = true,
    val gradingAndBeltsEnabled: Boolean = true,
    val announcementsEnabled: Boolean = true,
    val admissionsAlertsEnabled: Boolean = true,
    val fcmTokens: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)


