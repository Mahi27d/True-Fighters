package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.TrueFightersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(val repository: TrueFightersRepository) : ViewModel() {

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    fun refreshUserProfile(userId: String) {
        viewModelScope.launch {
            _currentUserProfile.value = repository.getUserProfile(userId)
        }
    }

    val branches: StateFlow<List<Branch>> = repository.observeBranches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val programs: StateFlow<List<Program>> = repository.observePrograms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val batches: StateFlow<List<Batch>> = repository.observeBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val students: StateFlow<List<Student>> = repository.observeStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val classSessions: StateFlow<List<ClassSession>> = repository.observeClassSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val attendanceList: StateFlow<List<Attendance>> = repository.observeAttendance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val feePlans: StateFlow<List<FeePlan>> = repository.observeFeePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val invoices: StateFlow<List<FeeInvoice>> = repository.observeInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val payments: StateFlow<List<Payment>> = repository.observePayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val progressRecords: StateFlow<List<ProgressRecord>> = repository.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val certificates: StateFlow<List<Certificate>> = repository.observeCertificates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val beltRanks: StateFlow<List<BeltRank>> = repository.observeBeltRanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val gradingAssessments: StateFlow<List<GradingAssessment>> = repository.observeGradingAssessments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val videos: StateFlow<List<Video>> = repository.observeVideos(includeUnpublished = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val announcements: StateFlow<List<Announcement>> = repository.observeAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val admissionRequests: StateFlow<List<AdmissionRequest>> = repository.observeAdmissionRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val coaches: StateFlow<List<UserProfile>> = repository.observeCoaches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _roleOverride = MutableStateFlow<String?>(null)
    val roleOverride: StateFlow<String?> = _roleOverride.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedAcademyDefaultsIfNeeded()
        }
    }

    fun setRoleOverride(role: String?) {
        _roleOverride.value = role
    }

    fun submitAdmission(req: AdmissionRequest) {
        viewModelScope.launch {
            repository.submitAdmissionRequest(req)
        }
    }

    fun approveAdmission(req: AdmissionRequest, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val reviewer = _currentUserProfile.value?.displayName?.ifBlank { null }
                ?: _currentUserProfile.value?.phoneNumber?.ifBlank { null }
                ?: "Academy Admin"
            val res = repository.reviewAdmission(req.id, "approved", reviewer)
            if (res.isSuccess) {
                val studentUserId = req.userId.ifBlank { "user_${req.id}" }
                val firstInvoice = FeeInvoice(
                    id = "inv_init_${req.id}",
                    branchId = req.preferredBranchId,
                    studentId = "student_${req.userId.ifBlank { req.id }}",
                    studentUserId = studentUserId,
                    studentName = req.fullName,
                    period = "Admission Enrollment Fee",
                    amountPaise = 250000L,
                    dueDate = "10th of Month",
                    status = "unpaid"
                )
                repository.generateInvoice(firstInvoice)
            }
            onResult(res)
        }
    }

    fun rejectAdmission(req: AdmissionRequest, rejectionReason: String = "", onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val reviewer = _currentUserProfile.value?.displayName?.ifBlank { null }
                ?: _currentUserProfile.value?.phoneNumber?.ifBlank { null }
                ?: "Academy Admin"
            val res = repository.reviewAdmission(req.id, "rejected", reviewer, rejectionReason = rejectionReason)
            onResult(res)
        }
    }

    fun provisionCoach(coach: UserProfile) {
        viewModelScope.launch {
            repository.provisionCoach(coach)
        }
    }

    fun markAttendance(att: Attendance) {
        viewModelScope.launch {
            repository.markAttendance(att)
        }
    }

    fun recordPayment(payment: Payment) {
        viewModelScope.launch {
            repository.recordPayment(payment)
        }
    }

    fun addProgressRecord(record: ProgressRecord) {
        viewModelScope.launch {
            repository.addProgressRecord(record)
        }
    }

    fun addBranch(branch: Branch, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.addBranch(branch)
            onResult(res)
        }
    }

    fun updateBranch(branch: Branch, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.updateBranch(branch)
            onResult(res)
        }
    }

    fun toggleBranchStatus(branchId: String, currentActive: Boolean, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val newStatus = if (currentActive) "inactive" else "active"
            val res = repository.setBranchStatus(branchId, newStatus)
            onResult(res)
        }
    }

    fun assignCoachBranches(coachUserId: String, branchIds: List<String>, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.assignCoachBranches(coachUserId, branchIds)
            onResult(res)
        }
    }

    fun assignStudentBranch(studentId: String, userId: String, branchId: String, branchName: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.assignStudentBranch(studentId, userId, branchId, branchName)
            onResult(res)
        }
    }

    fun updateStudentProfile(student: Student, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.updateStudentProfile(student)
            onResult(res)
        }
    }

    fun setStudentStatus(
        studentId: String,
        userId: String,
        status: String,
        reason: String = "",
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.setStudentStatus(studentId, userId, status, reason)
            onResult(res)
        }
    }

    fun generateInvoice(invoice: FeeInvoice) {
        viewModelScope.launch {
            repository.generateInvoice(invoice)
        }
    }

    fun createOrUpdateProgram(program: Program, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createOrUpdateProgram(program)
            onResult(res)
        }
    }

    fun setProgramStatus(programId: String, status: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.setProgramStatus(programId, status)
            onResult(res)
        }
    }

    fun createOrUpdateBatch(batch: Batch, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createOrUpdateBatch(batch)
            onResult(res)
        }
    }

    fun setBatchStatus(batchId: String, status: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.setBatchStatus(batchId, status)
            onResult(res)
        }
    }

    fun manageBatchStudent(
        batchId: String,
        studentId: String,
        action: String,
        targetBatchId: String? = null,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.manageBatchStudent(batchId, studentId, action, targetBatchId)
            onResult(res)
        }
    }

    fun createClassSession(session: ClassSession, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createClassSessionWithValidation(session)
            onResult(res)
        }
    }

    fun rescheduleOrCancelSession(
        sessionId: String,
        newDate: String? = null,
        newStartTime: String? = null,
        newEndTime: String? = null,
        newStatus: String? = null,
        reason: String = "",
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.rescheduleOrCancelSession(sessionId, newDate, newStartTime, newEndTime, newStatus, reason)
            onResult(res)
        }
    }

    fun markBatchAttendance(sessionId: String, records: List<Attendance>, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.markBatchAttendance(sessionId, records)
            onResult(res)
        }
    }

    fun publishAnnouncement(announcement: Announcement) {
        viewModelScope.launch {
            repository.createAnnouncement(announcement)
        }
    }

    fun createFeePlan(plan: FeePlan, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createOrUpdateFeePlan(plan)
            onResult(res)
        }
    }

    fun updateFeePlan(plan: FeePlan, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createOrUpdateFeePlan(plan)
            onResult(res)
        }
    }

    fun toggleFeePlanStatus(planId: String, currentActive: Boolean, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.setFeePlanStatus(planId, if (currentActive) "inactive" else "active")
            onResult(res)
        }
    }

    fun generateFeeInvoice(invoice: FeeInvoice, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.generateInvoiceWithValidation(invoice)
            onResult(res)
        }
    }

    fun recordManualPayment(payment: Payment, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.recordPaymentWithValidation(payment)
            onResult(res)
        }
    }

    fun refundPayment(paymentId: String, reason: String = "", onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.refundPayment(paymentId, reason)
            onResult(res)
        }
    }

    // Phase 7 Actions
    fun createOrUpdateBeltRank(beltRank: BeltRank, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createOrUpdateBeltRank(beltRank)
            onResult(res)
        }
    }

    fun toggleBeltRankStatus(rankId: String, currentActive: Boolean, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.setBeltRankStatus(rankId, !currentActive)
            onResult(res)
        }
    }

    fun submitGradingAssessment(assessment: GradingAssessment, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.submitGradingAssessment(assessment)
            onResult(res)
        }
    }

    fun approveGradingAssessment(assessmentId: String, adminNotes: String = "", onResult: (Result<Certificate>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.approveGradingAssessment(assessmentId, adminNotes)
            onResult(res)
        }
    }

    fun rejectGradingAssessment(assessmentId: String, reason: String = "", onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.rejectGradingAssessment(assessmentId, reason)
            onResult(res)
        }
    }

    fun createOrUpdateVideo(video: Video, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.createOrUpdateVideo(video)
            onResult(res)
        }
    }

    fun toggleVideoPublished(videoId: String, currentPublished: Boolean, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.setVideoPublished(videoId, !currentPublished)
            onResult(res)
        }
    }

    fun deleteVideo(videoId: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deleteVideo(videoId)
            onResult(res)
        }
    }

    class Factory(private val repository: TrueFightersRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
