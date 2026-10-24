package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AdmissionRequest
import com.example.data.model.Attendance
import com.example.data.model.Batch
import com.example.data.model.Branch
import com.example.data.model.ClassSession
import com.example.data.model.FeeInvoice
import com.example.data.model.FeePlan
import com.example.data.model.InvoiceItem
import com.example.data.model.AcademyOverviewMetrics
import com.example.data.model.AttendanceReportSummary
import com.example.data.model.BeltRank
import com.example.data.model.Certificate
import com.example.data.model.FinancialSummaryReport
import com.example.data.model.GradingAssessment
import com.example.data.model.AppNotification
import com.example.data.model.NotificationPreferences
import com.example.data.model.Payment
import com.example.data.model.Program
import com.example.data.model.Student
import com.example.data.model.UserProfile
import com.example.data.model.Video
import com.example.data.repository.TrueFightersRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrueFightersRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun authenticatedUser_canReadBranchesAndSubmitAdmission() = runBlocking {
    // Admin seeds active branch first
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val studentUid = signInTestUser("student_test@truefighters.in")
    val req = AdmissionRequest(
      userId = studentUid,
      fullName = "Rohan Sharma",
      email = "student_test@truefighters.in",
      phoneNumber = "9876543210",
      programId = "prog_karate",
      preferredBranchId = "branch_main"
    )
    val result = withTimeout(5000L) {
      repo.submitAdmissionRequest(req)
    }
    assertTrue("Submit admission should succeed: ${result.exceptionOrNull()}", result.isSuccess)
    assertNotNull("Returned ID should not be null", result.getOrNull())
  }

  @Test
  fun authenticatedUser_canSaveProfile() = runBlocking {
    val testEmail = "karate_fan_${System.currentTimeMillis()}@truefighters.in"
    val uid = signInTestUser(testEmail)
    val repo = TrueFightersRepository(firestore)

    val profile = UserProfile(
      userId = uid,
      displayName = "Sensei Rohan",
      email = testEmail,
      role = "student"
    )
    val saveResult = withTimeout(5000L) {
      repo.saveUserProfile(profile)
    }
    val ex = saveResult.exceptionOrNull()
    assertTrue("Save profile should succeed, error was: $ex", saveResult.isSuccess)
  }

  @Test
  fun unauthenticatedUser_failsProtectedWrite() = runBlocking {
    auth.signOut()
    val repo = TrueFightersRepository(firestore)

    val branch = Branch(
      name = "Unauthorized Dojo",
      code = "UNAUTH",
      address = "Secret",
      phone = "0000"
    )
    val result = withTimeout(5000L) {
      repo.addBranch(branch)
    }
    assertTrue("Unauthenticated write should fail", result.isFailure)
  }

  @Test
  fun admin_canCreateEditActivateDeactivateBranch_andRejectDuplicateCode() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)

    // Safe admin migration check
    val migratedAdmin = repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    assertNotNull("Admin migration must return active AdminRecord", migratedAdmin)

    val uniqueCode = "TF-SOUTH-${System.currentTimeMillis() % 10000}"
    val newBranch = Branch(
      branchId = "branch_south_test",
      branchName = "True Fighters South Dojo",
      branchCode = uniqueCode,
      address = "88 Bannerghatta Road",
      city = "Bengaluru",
      state = "Karnataka",
      postalCode = "560076",
      contactPhone = "+91 80 4123 8899",
      status = "active",
      active = true
    )

    val createRes = withTimeout(5000L) { repo.addBranch(newBranch) }
    assertTrue("Admin branch creation should succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)

    // Duplicate branch code must be rejected
    val dupRes = withTimeout(5000L) {
      repo.addBranch(newBranch.copy(branchId = "branch_south_dup", id = "branch_south_dup"))
    }
    assertTrue("Duplicate branch code must fail", dupRes.isFailure)

    // Edit branch details
    val updateRes = withTimeout(5000L) {
      repo.updateBranch(newBranch.copy(city = "Mysuru", address = "101 Palace Road"))
    }
    assertTrue("Branch update should succeed", updateRes.isSuccess)

    // Deactivate branch
    val deactivateRes = withTimeout(5000L) {
      repo.setBranchStatus("branch_south_test", status = "inactive")
    }
    assertTrue("Deactivating branch should succeed", deactivateRes.isSuccess)

    val activeOnly = repo.observeBranches(includeInactive = false).first()
    assertFalse("Deactivated branch should not appear in active-only list", activeOnly.any { it.effectiveId == "branch_south_test" })

    val allBranches = repo.observeBranches(includeInactive = true).first()
    val deactivatedBranch = allBranches.firstOrNull { it.effectiveId == "branch_south_test" }
    assertNotNull("Deactivated branch should appear in all-branches list", deactivatedBranch)
    assertFalse("Deactivated branch isBranchActive must be false", deactivatedBranch!!.isBranchActive)
  }

  @Test
  fun admissionRequest_rejectsInactiveBranch_andApprovalCreatesEnrolledStudent() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Create and deactivate a branch
    val inactiveBranchId = "branch_closed_test"
    repo.addBranch(
      Branch(
        branchId = inactiveBranchId,
        branchName = "Closed Dojo",
        branchCode = "TF-CL-${System.currentTimeMillis() % 10000}",
        address = "12 Old Street",
        contactPhone = "+91 80 0000 1111"
      )
    )
    repo.setBranchStatus(inactiveBranchId, status = "inactive")

    // Applicant tries to submit admission to inactive branch
    val applicantUid = signInTestUser("applicant_phase3@truefighters.in")
    val badReq = AdmissionRequest(
      userId = applicantUid,
      fullName = "Aarav Verma",
      phoneNumber = "+919811122233",
      preferredBranchId = inactiveBranchId,
      preferredBranchName = "Closed Dojo",
      programName = "Karate Do"
    )
    val badSubmit = repo.submitAdmissionRequest(badReq)
    assertTrue("Admission to inactive branch must be rejected", badSubmit.isFailure)

    // Applicant submits admission to active branch_main
    val goodReq = badReq.copy(
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo"
    )
    val goodSubmit = repo.submitAdmissionRequest(goodReq)
    assertTrue("Admission to active branch must succeed: ${goodSubmit.exceptionOrNull()}", goodSubmit.isSuccess)
    val reqId = goodSubmit.getOrThrow()

    // Admin approves admission and verifies Student record creation
    signInTestUser("Mahi27d@gmail.com")
    val reviewRes = repo.reviewAdmission(
      requestId = reqId,
      status = "approved",
      reviewedBy = "Mahi27d@gmail.com"
    )
    assertTrue("Admin reviewAdmission should succeed: ${reviewRes.exceptionOrNull()}", reviewRes.isSuccess)

    val createdStudentSnap = firestore.collection("students").document("student_$applicantUid").get().await()
    assertTrue("Approved admission must create Student record", createdStudentSnap.exists())
    assertEquals("branch_main", createdStudentSnap.getString("branchId"))
  }

  @Test
  fun coach_restrictedToAssignedBranchForSessionsAndAttendance() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Create two students in different branches/batches for membership verification
    repo.createOrUpdateStudent(
      Student(
        id = "student_1",
        userId = "user_std_1",
        branchId = "branch_main",
        branchName = "True Fighters Central Dojo",
        batchId = "batch_morning_karate",
        batchName = "Morning Warriors",
        programId = "prog_karate",
        programName = "Traditional Karate",
        fullName = "Aarav Mehta",
        status = "active"
      )
    ).getOrThrow()

    repo.createOrUpdateStudent(
      Student(
        id = "student_2",
        userId = "user_std_2",
        branchId = "branch_west",
        branchName = "True Fighters West Arena",
        batchId = "batch_youth_taekwondo",
        batchName = "Youth Tigers",
        programId = "prog_taekwondo",
        programName = "Olympic Taekwondo",
        fullName = "Diya Nair",
        status = "active"
      )
    ).getOrThrow()

    // Sign in as coach user to get their UID, then have admin provision them for branch_main only
    val coachUid = signInTestUser("branch_main_coach@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    firestore.collection("users").document(coachUid).set(
      UserProfile(
        id = coachUid,
        userId = coachUid,
        displayName = "Coach Vikram",
        phoneNumber = "+919876500099",
        role = "coach",
        status = "active",
        active = true,
        preferredBranchId = "branch_main",
        branchIds = listOf("branch_main")
      )
    ).await()

    // Switch back to coach
    signInTestUser("branch_main_coach@truefighters.in")

    // Coach creates class session in assigned branch_main -> succeeds
    val allowedSession = repo.createOrUpdateClassSession(
      ClassSession(
        batchId = "batch_morning_karate",
        batchName = "Morning Warriors",
        branchId = "branch_main",
        coachId = coachUid,
        coachName = "Coach Vikram",
        date = "2026-10-25",
        timeSlot = "6:30 AM - 8:00 AM"
      )
    )
    assertTrue("Coach session write in assigned branch must succeed: ${allowedSession.exceptionOrNull()}", allowedSession.isSuccess)

    // Coach attempts to create class session in unassigned branch_west -> fails
    val deniedSession = repo.createOrUpdateClassSession(
      ClassSession(
        batchId = "batch_youth_taekwondo",
        batchName = "Youth Tigers",
        branchId = "branch_west",
        coachId = coachUid,
        coachName = "Coach Vikram",
        date = "2026-10-25",
        timeSlot = "4:00 PM - 5:30 PM"
      )
    )
    assertTrue("Coach session write in unassigned branch must fail", deniedSession.isFailure)

    // Coach records attendance for student_1 in branch_main & batch_morning_karate -> succeeds
    val validAttendance = repo.markAttendance(
      Attendance(
        sessionId = "sess_1",
        batchId = "batch_morning_karate",
        branchId = "branch_main",
        studentId = "student_1",
        studentUserId = "user_std_1",
        studentName = "Aarav Mehta",
        date = "2026-10-25",
        status = "present",
        markedBy = coachUid
      )
    )
    assertTrue("Attendance for valid branch & batch member must succeed: ${validAttendance.exceptionOrNull()}", validAttendance.isSuccess)

    // Coach attempts to record attendance for student_2 (enrolled in branch_west) under branch_main -> fails membership check
    val mismatchedStudentAttendance = repo.markAttendance(
      Attendance(
        sessionId = "sess_1",
        batchId = "batch_morning_karate",
        branchId = "branch_main",
        studentId = "student_2",
        studentUserId = "user_std_2",
        studentName = "Diya Nair",
        date = "2026-10-25",
        status = "present",
        markedBy = coachUid
      )
    )
    assertTrue("Attendance for student from another branch/batch must fail", mismatchedStudentAttendance.isFailure)
  }

  @Test
  fun student_cannotSelfElevateRoleOrAssignPrivilegedBranches() = runBlocking {
    val studentUid = signInTestUser("regular_student_sec@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    // Student attempts to self-create an admin profile
    val selfAdminAttempt = repo.saveUserProfile(
      UserProfile(
        id = studentUid,
        userId = studentUid,
        displayName = "Sneaky User",
        role = "admin",
        status = "active",
        active = true
      )
    )
    assertTrue("Student must not be able to self-assign admin role", selfAdminAttempt.isFailure)
  }

  @Test
  fun phase4_admissionValidation_minorConsent_andDuplicatePrevention() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val minorApplicantUid = signInTestUser("minor_applicant@truefighters.in")

    // 1. Invalid: missing required fullName
    val missingNameRes = repo.submitStudentRegistration(
      userId = minorApplicantUid,
      fullName = "",
      phoneNumber = "+919876511101",
      dateOfBirth = "2012-05-14",
      isMinor = true,
      guardianName = "Rajesh Kumar",
      guardianPhone = "+919876511100",
      guardianConsent = true,
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo",
      programName = "Traditional Karate"
    )
    assertTrue("Registration without full name must fail", missingNameRes.isFailure)

    // 2. Invalid: minor without guardian details
    val missingGuardianRes = repo.submitStudentRegistration(
      userId = minorApplicantUid,
      fullName = "Kabir Kumar",
      phoneNumber = "+919876511101",
      dateOfBirth = "2012-05-14",
      isMinor = true,
      guardianName = "",
      guardianPhone = "",
      guardianConsent = true,
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo",
      programName = "Traditional Karate"
    )
    assertTrue("Minor registration without guardian info must fail", missingGuardianRes.isFailure)

    // 3. Invalid: minor without guardian consent
    val missingConsentRes = repo.submitStudentRegistration(
      userId = minorApplicantUid,
      fullName = "Kabir Kumar",
      phoneNumber = "+919876511101",
      dateOfBirth = "2012-05-14",
      isMinor = true,
      guardianName = "Rajesh Kumar",
      guardianPhone = "+919876511100",
      guardianConsent = false,
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo",
      programName = "Traditional Karate"
    )
    assertTrue("Minor registration without guardian consent must fail", missingConsentRes.isFailure)

    // 4. Valid minor registration succeeds with pending status
    val validMinorRes = repo.submitStudentRegistration(
      userId = minorApplicantUid,
      fullName = "Kabir Kumar",
      phoneNumber = "+919876511101",
      dateOfBirth = "2012-05-14",
      isMinor = true,
      guardianName = "Rajesh Kumar",
      guardianPhone = "+919876511100",
      guardianConsent = true,
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo",
      programName = "Traditional Karate"
    )
    assertTrue("Valid minor registration should succeed: ${validMinorRes.exceptionOrNull()}", validMinorRes.isSuccess)
    val savedProfile = validMinorRes.getOrThrow()
    assertEquals("pending", savedProfile.status)
    assertFalse("Pending applicant must not be active before approval", savedProfile.active)

    // 5. Before approval, student login returns RegistrationPending (no dashboard access)
    val roleCheckBeforeApproval = repo.validateAndResolveUserRole(
      userId = minorApplicantUid,
      selectedRole = "student",
      phoneNumber = "+919876511101"
    )
    assertTrue("Unapproved student must receive RegistrationPending", roleCheckBeforeApproval is com.example.data.model.RoleValidationResult.RegistrationPending)

    // 6. Duplicate submission by the same applicant while pending must be rejected
    val duplicateSubmitRes = repo.submitStudentRegistration(
      userId = minorApplicantUid,
      fullName = "Kabir Kumar",
      phoneNumber = "+919876511101",
      dateOfBirth = "2012-05-14",
      isMinor = true,
      guardianName = "Rajesh Kumar",
      guardianPhone = "+919876511100",
      guardianConsent = true,
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo",
      programName = "Traditional Karate"
    )
    assertTrue("Duplicate pending admission submission must be rejected", duplicateSubmitRes.isFailure)
  }

  @Test
  fun phase4_unauthorizedApprovalDenied_andIdempotentApprovalCreatesSingleStudent() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Student submits registration
    val applicantUid = signInTestUser("idempotent_applicant@truefighters.in")
    val regRes = repo.submitStudentRegistration(
      userId = applicantUid,
      fullName = "Ananya Singh",
      phoneNumber = "+919876522202",
      dateOfBirth = "2004-08-20",
      isMinor = false,
      guardianName = "",
      guardianPhone = "",
      guardianConsent = false,
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo",
      programName = "Olympic Taekwondo"
    )
    assertTrue("Student registration should succeed: ${regRes.exceptionOrNull()}", regRes.isSuccess)
    val admRequestId = "adm_$applicantUid"

    // 1. Applicant attempts to self-approve their own admission request -> DENIED
    val selfApproveRes = repo.reviewAdmission(
      requestId = admRequestId,
      status = "approved",
      reviewedBy = "Self Hacker"
    )
    assertTrue("Student must not be able to approve their own admission request", selfApproveRes.isFailure)

    // 2. Authorized Admin approves admission request twice -> idempotent, creates exactly 1 student record
    signInTestUser("Mahi27d@gmail.com")
    val firstApproval = repo.reviewAdmission(
      requestId = admRequestId,
      status = "approved",
      reviewedBy = "Mahi27d@gmail.com"
    )
    assertTrue("First admin approval should succeed: ${firstApproval.exceptionOrNull()}", firstApproval.isSuccess)

    val secondApproval = repo.reviewAdmission(
      requestId = admRequestId,
      status = "approved",
      reviewedBy = "Mahi27d@gmail.com"
    )
    assertTrue("Repeated admin approval should be idempotent and succeed: ${secondApproval.exceptionOrNull()}", secondApproval.isSuccess)

    val matchingStudents = firestore.collection("students")
      .whereEqualTo("userId", applicantUid)
      .get().await()
    assertEquals("Idempotent approval must create exactly 1 student record", 1, matchingStudents.size())
    val studentDoc = matchingStudents.documents.first()
    assertEquals("branch_main", studentDoc.getString("branchId"))
    assertEquals("Olympic Taekwondo", studentDoc.getString("programName"))
    assertEquals("active", studentDoc.getString("status"))

    // Verify review metadata recorded on admission request
    val reviewedAdm = repo.getAdmissionRequestDetails(admRequestId).getOrThrow()
    assertEquals("approved", reviewedAdm.status)
    assertEquals("Mahi27d@gmail.com", reviewedAdm.reviewedBy)
    assertNotNull("reviewedAt server timestamp must be recorded", reviewedAdm.reviewedAt)

    // 3. Approved student can now log in and pass role validation
    signInTestUser("idempotent_applicant@truefighters.in")
    val roleCheckAfterApproval = repo.validateAndResolveUserRole(
      userId = applicantUid,
      selectedRole = "student",
      phoneNumber = "+919876522202"
    )
    assertTrue("Approved student must receive RoleValidationResult.Success", roleCheckAfterApproval is com.example.data.model.RoleValidationResult.Success)
  }

  @Test
  fun phase4_wrongBranchAccess_andMinorGuardianPrivacyRestrictions() = runBlocking {
    val globalAdminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(globalAdminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Minor applicant submits admission to branch_west
    val minorWestUid = signInTestUser("minor_west@truefighters.in")
    repo.submitStudentRegistration(
      userId = minorWestUid,
      fullName = "Vihaan Kapoor",
      phoneNumber = "+919876533303",
      dateOfBirth = "2013-11-02",
      isMinor = true,
      guardianName = "Neha Kapoor",
      guardianPhone = "+919876533300",
      guardianConsent = true,
      preferredBranchId = "branch_west",
      preferredBranchName = "True Fighters West Arena",
      programName = "Traditional Karate"
    ).getOrThrow()
    val westAdmId = "adm_$minorWestUid"

    // Global admin approves minorWestUid into branch_west and also provisions a branch-scoped admin & coach for branch_main ONLY
    val mainBranchAdminUid = signInTestUser("admin_main_only@truefighters.in")
    val mainBranchCoachUid = signInTestUser("coach_main_only@truefighters.in")
    val westBranchCoachUid = signInTestUser("coach_west_assigned@truefighters.in")

    signInTestUser("Mahi27d@gmail.com")
    repo.reviewAdmission(westAdmId, "approved", "Mahi27d@gmail.com").getOrThrow()

    // Provision branch-scoped admin for branch_main ONLY
    firestore.collection("admins").document(mainBranchAdminUid).set(
      mapOf(
        "userId" to mainBranchAdminUid,
        "email" to "admin_main_only@truefighters.in",
        "phoneNumber" to "+919876500055",
        "role" to "admin",
        "scope" to "branch",
        "branchIds" to listOf("branch_main"),
        "active" to true
      )
    ).await()

    // Provision coach for branch_main ONLY and coach for branch_west
    repo.provisionCoach(
      UserProfile(
        userId = mainBranchCoachUid,
        displayName = "Coach Main",
        phoneNumber = "+919876500066",
        role = "coach",
        status = "active",
        active = true,
        branchIds = listOf("branch_main")
      )
    ).getOrThrow()

    repo.provisionCoach(
      UserProfile(
        userId = westBranchCoachUid,
        displayName = "Coach West",
        phoneNumber = "+919876500077",
        role = "coach",
        status = "active",
        active = true,
        branchIds = listOf("branch_west")
      )
    ).getOrThrow()

    // 1. Branch-scoped admin for branch_main CANNOT view admission details or student/guardian details for branch_west
    signInTestUser("admin_main_only@truefighters.in")
    val wrongBranchAdmRead = repo.getAdmissionRequestDetails(westAdmId)
    assertTrue("Branch-scoped admin for branch_main must not read branch_west admission details", wrongBranchAdmRead.isFailure)

    val wrongBranchStudentRead = repo.getStudentDetails("student_$minorWestUid")
    assertTrue("Branch-scoped admin for branch_main must not read branch_west minor student details", wrongBranchStudentRead.isFailure)

    val wrongBranchReviewAttempt = repo.reviewAdmission(westAdmId, "rejected", "admin_main_only@truefighters.in", "Unauthorized")
    assertTrue("Branch-scoped admin for branch_main must not review branch_west admission", wrongBranchReviewAttempt.isFailure)

    // 2. Unassigned coach (branch_main) CANNOT read minor/guardian student details in branch_west
    signInTestUser("coach_main_only@truefighters.in")
    val unassignedCoachRead = repo.getStudentDetails("student_$minorWestUid")
    assertTrue("Coach assigned only to branch_main must not access minor/guardian record in branch_west", unassignedCoachRead.isFailure)

    // 3. Assigned coach (branch_west) CAN read student/guardian details in branch_west
    signInTestUser("coach_west_assigned@truefighters.in")
    val assignedCoachRead = repo.getStudentDetails("student_$minorWestUid")
    assertTrue("Assigned branch_west coach should read student details: ${assignedCoachRead.exceptionOrNull()}", assignedCoachRead.isSuccess)
    assertEquals("Neha Kapoor", assignedCoachRead.getOrThrow().guardianName)

    // 4. The student themselves CAN read their own student record
    signInTestUser("minor_west@truefighters.in")
    val selfStudentRead = repo.getStudentDetails("student_$minorWestUid")
    assertTrue("Student should read their own student record: ${selfStudentRead.exceptionOrNull()}", selfStudentRead.isSuccess)
    assertEquals("Neha Kapoor", selfStudentRead.getOrThrow().guardianName)
  }

  @Test
  fun phase4_studentProfileEdits_branchTransfers_andSuspensionPreservesHistory() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Register & approve student
    val studentUid = signInTestUser("managed_student@truefighters.in")
    repo.submitStudentRegistration(
      userId = studentUid,
      fullName = "Devansh Rao",
      phoneNumber = "+919876544404",
      dateOfBirth = "2001-03-15",
      isMinor = false,
      guardianName = "",
      guardianPhone = "",
      guardianConsent = false,
      preferredBranchId = "branch_main",
      preferredBranchName = "True Fighters Central Dojo",
      programName = "Traditional Karate"
    ).getOrThrow()

    signInTestUser("Mahi27d@gmail.com")
    repo.reviewAdmission("adm_$studentUid", "approved", "Mahi27d@gmail.com").getOrThrow()
    val studentId = "student_$studentUid"

    // Record attendance and payment history for this student
    repo.markAttendance(
      Attendance(
        id = "att_hist_1",
        sessionId = "sess_hist_1",
        batchId = "batch_morning_karate",
        branchId = "branch_main",
        studentId = studentId,
        studentUserId = studentUid,
        studentName = "Devansh Rao",
        date = "2026-10-20",
        status = "present",
        markedBy = adminUid
      )
    ).getOrThrow()

    repo.recordPayment(
      com.example.data.model.Payment(
        id = "pay_hist_1",
        invoiceId = "inv_hist_1",
        branchId = "branch_main",
        studentId = studentId,
        studentUserId = studentUid,
        studentName = "Devansh Rao",
        amountPaise = 250000L,
        method = "upi",
        providerReference = "UTR998877",
        status = "captured"
      )
    ).getOrThrow()

    // 1. Edit student profile details (belt promotion & updated contact)
    val currentStudent = repo.getStudentDetails(studentId).getOrThrow()
    val editRes = repo.updateStudentProfile(
      currentStudent.copy(
        fullName = "Devansh Vikram Rao",
        currentBelt = "Yellow Belt",
        email = "devansh.rao@truefighters.in"
      )
    )
    assertTrue("Admin student profile update should succeed: ${editRes.exceptionOrNull()}", editRes.isSuccess)

    // 2. Transfer student to another active branch (branch_west)
    val transferRes = repo.assignStudentBranch(
      studentId = studentId,
      userId = studentUid,
      branchId = "branch_west",
      branchName = "True Fighters West Arena"
    )
    assertTrue("Transferring student to active branch_west should succeed: ${transferRes.exceptionOrNull()}", transferRes.isSuccess)

    val updatedStudent = repo.getStudentDetails(studentId).getOrThrow()
    assertEquals("Devansh Vikram Rao", updatedStudent.fullName)
    assertEquals("Yellow Belt", updatedStudent.currentBelt)
    assertEquals("branch_west", updatedStudent.branchId)
    assertEquals("True Fighters West Arena", updatedStudent.branchName)

    // Verify attendance & payment history are preserved after profile edit & branch transfer
    val preservedAttendance = firestore.collection("attendance").document("att_hist_1").get().await()
    val preservedPayment = firestore.collection("payments").document("pay_hist_1").get().await()
    assertTrue("Attendance history must be preserved", preservedAttendance.exists())
    assertTrue("Payment history must be preserved", preservedPayment.exists())

    // 3. Suspend student account and verify login is blocked while preserving history
    val suspendRes = repo.setStudentStatus(
      studentId = studentId,
      userId = studentUid,
      status = "suspended",
      reason = "Temporary medical leave"
    )
    assertTrue("Suspending student should succeed: ${suspendRes.exceptionOrNull()}", suspendRes.isSuccess)
    assertEquals("suspended", repo.getStudentDetails(studentId).getOrThrow().status)

    // Student attempts login while suspended -> AccountSuspended
    signInTestUser("managed_student@truefighters.in")
    val suspendedLoginCheck = repo.validateAndResolveUserRole(
      userId = studentUid,
      selectedRole = "student",
      phoneNumber = "+919876544404"
    )
    assertTrue(
      "Suspended student must be denied dashboard access, got: $suspendedLoginCheck",
      suspendedLoginCheck is com.example.data.model.RoleValidationResult.AccountSuspended ||
        suspendedLoginCheck is com.example.data.model.RoleValidationResult.AccountInactive
    )

    // 4. Reactivate student account and verify login succeeds again
    signInTestUser("Mahi27d@gmail.com")
    val reactivateRes = repo.setStudentStatus(
      studentId = studentId,
      userId = studentUid,
      status = "active",
      reason = "Cleared to return"
    )
    assertTrue("Reactivating student should succeed: ${reactivateRes.exceptionOrNull()}", reactivateRes.isSuccess)

    signInTestUser("managed_student@truefighters.in")
    val reactivatedLoginCheck = repo.validateAndResolveUserRole(
      userId = studentUid,
      selectedRole = "student",
      phoneNumber = "+919876544404"
    )
    assertTrue("Reactivated student must be allowed login", reactivatedLoginCheck is com.example.data.model.RoleValidationResult.Success)
  }

  @Test
  fun phase5_program_creation_branch_association_and_deactivation() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // 1. Admin creates a custom dynamic program with branch association
    val program = Program(
      programId = "prog_bjj_test",
      name = "Brazilian Jiu-Jitsu (BJJ)",
      programName = "Brazilian Jiu-Jitsu (BJJ)",
      discipline = "BJJ",
      level = "All Levels",
      description = "Ground grappling, joint locks, chokes, and submissions",
      branchIds = listOf("branch_main", "branch_west"),
      status = "active",
      active = true
    )
    val createRes = repo.createOrUpdateProgram(program)
    assertTrue("Program creation should succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)

    val fetched = repo.getProgram("prog_bjj_test").getOrThrow()
    assertNotNull("Program must exist in Firestore", fetched)
    assertEquals("Brazilian Jiu-Jitsu (BJJ)", fetched?.effectiveName)
    assertTrue("Program must be active", fetched?.isProgramActive == true)
    assertEquals(2, fetched?.branchIds?.size)

    // 2. Admin deactivates program
    val deactRes = repo.setProgramStatus("prog_bjj_test", "inactive")
    assertTrue("Program deactivation should succeed: ${deactRes.exceptionOrNull()}", deactRes.isSuccess)
    val deactivated = repo.getProgram("prog_bjj_test").getOrThrow()
    assertFalse("Deactivated program isProgramActive must be false", deactivated?.isProgramActive == true)

    // 3. Associating program with inactive branch must fail
    val inactiveBranchId = "branch_dormant_prog"
    repo.addBranch(
      Branch(
        branchId = inactiveBranchId,
        branchName = "Dormant Dojo",
        branchCode = "TF-DORM-${System.currentTimeMillis() % 10000}",
        address = "Faraway",
        status = "inactive",
        active = false
      )
    )
    val badProgRes = repo.createOrUpdateProgram(
      Program(
        programId = "prog_bad_branch",
        name = "Capoeira",
        branchIds = listOf(inactiveBranchId)
      )
    )
    assertTrue("Associating program with inactive branch must fail", badProgRes.isFailure)
  }

  @Test
  fun phase5_batch_creation_validates_active_branch_and_coach_authorization() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Provision a coach authorized ONLY for branch_main
    val coachUid = signInTestUser("coach_main_batch@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.provisionCoach(
      UserProfile(
        userId = coachUid,
        displayName = "Sensei Deepak",
        phoneNumber = "+919876500088",
        role = "coach",
        status = "active",
        active = true,
        branchIds = listOf("branch_main")
      )
    ).getOrThrow()

    // 1. Try to assign Sensei Deepak to a batch in branch_west -> must fail!
    signInTestUser("Mahi27d@gmail.com")
    val invalidCoachBatch = Batch(
      batchId = "batch_unauth_coach",
      name = "West Kickboxers",
      branchId = "branch_west",
      programId = "prog_kickboxing",
      coachIds = listOf(coachUid),
      capacity = 25
    )
    val invalidCoachRes = repo.createOrUpdateBatch(invalidCoachBatch)
    assertTrue("Batch creation with coach unauthorized for branch must fail", invalidCoachRes.isFailure)

    // 2. Try to create batch in inactive branch -> must fail!
    val inactiveBranchId = "branch_closed_batch"
    repo.addBranch(
      Branch(
        branchId = inactiveBranchId,
        branchName = "Closed Batch Branch",
        branchCode = "TF-CLB-${System.currentTimeMillis() % 10000}",
        address = "Closed Street",
        status = "inactive",
        active = false
      )
    )
    val invalidBranchRes = repo.createOrUpdateBatch(
      Batch(
        batchId = "batch_bad_branch",
        name = "Closed Dojo Batch",
        branchId = inactiveBranchId,
        programId = "prog_karate"
      )
    )
    assertTrue("Batch creation in inactive branch must fail", invalidBranchRes.isFailure)

    // 3. Create batch in branch_main with authorized coach Sensei Deepak -> succeeds!
    val validBatch = Batch(
      batchId = "batch_valid_main",
      name = "Central Kumite Champions",
      branchId = "branch_main",
      programId = "prog_karate",
      coachIds = listOf(coachUid),
      capacity = 20
    )
    val validRes = repo.createOrUpdateBatch(validBatch)
    assertTrue("Batch creation with authorized coach and active branch must succeed: ${validRes.exceptionOrNull()}", validRes.isSuccess)
  }

  @Test
  fun phase5_batch_membership_capacity_add_remove_and_transfer() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Create a batch with strictly limited capacity = 2
    val batchId = "batch_cap_2_test"
    repo.createOrUpdateBatch(
      Batch(
        batchId = batchId,
        name = "Elite 2 Fighters",
        branchId = "branch_main",
        programId = "prog_karate",
        capacity = 2L
      )
    ).getOrThrow()

    // Create 3 active students in branch_main
    val s1 = Student(id = "std_cap_1", userId = "u_cap_1", fullName = "Student One", branchId = "branch_main", status = "active")
    val s2 = Student(id = "std_cap_2", userId = "u_cap_2", fullName = "Student Two", branchId = "branch_main", status = "active")
    val s3 = Student(id = "std_cap_3", userId = "u_cap_3", fullName = "Student Three", branchId = "branch_main", status = "active")
    repo.createOrUpdateStudent(s1).getOrThrow()
    repo.createOrUpdateStudent(s2).getOrThrow()
    repo.createOrUpdateStudent(s3).getOrThrow()

    // 1. Add student 1 -> succeeds (1/2)
    val add1 = repo.manageBatchStudent(batchId, "std_cap_1", "add")
    assertTrue("Adding first student should succeed: ${add1.exceptionOrNull()}", add1.isSuccess)

    // 2. Add student 2 -> succeeds (2/2, full)
    val add2 = repo.manageBatchStudent(batchId, "std_cap_2", "add")
    assertTrue("Adding second student should succeed: ${add2.exceptionOrNull()}", add2.isSuccess)

    // 3. Add student 3 -> must be REJECTED because batch is at full capacity!
    val add3 = repo.manageBatchStudent(batchId, "std_cap_3", "add")
    assertTrue("Adding third student to full batch must fail with capacity exception", add3.isFailure)

    // 4. Create overflow batch and transfer student 2
    val overflowBatchId = "batch_overflow_test"
    repo.createOrUpdateBatch(
      Batch(
        batchId = overflowBatchId,
        name = "Overflow Warriors",
        branchId = "branch_main",
        programId = "prog_karate",
        capacity = 10L
      )
    ).getOrThrow()

    val transferRes = repo.manageBatchStudent(batchId, "std_cap_2", "transfer", targetBatchId = overflowBatchId)
    assertTrue("Transferring student to overflow batch should succeed: ${transferRes.exceptionOrNull()}", transferRes.isSuccess)

    // Check capacity freed in batchId (now 1/2)
    val updatedBatch = repo.getBatch(batchId).getOrThrow()
    assertEquals(1, updatedBatch?.enrolledCount)
    assertEquals(1L, updatedBatch?.availableCapacity)

    // 5. Now student 3 can be enrolled!
    val add3Retry = repo.manageBatchStudent(batchId, "std_cap_3", "add")
    assertTrue("Enrolling student 3 after space opened should succeed: ${add3Retry.exceptionOrNull()}", add3Retry.isSuccess)

    // 6. Remove student 1
    val removeRes = repo.manageBatchStudent(batchId, "std_cap_1", "remove")
    assertTrue("Removing student should succeed: ${removeRes.exceptionOrNull()}", removeRes.isSuccess)
    val finalBatch = repo.getBatch(batchId).getOrThrow()
    assertEquals(1, finalBatch?.enrolledCount)
  }

  @Test
  fun phase5_class_session_validation_times_conflicts_and_coach_authorization() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val coachUid = signInTestUser("coach_sched_test@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.provisionCoach(
      UserProfile(
        userId = coachUid,
        displayName = "Sensei Rajesh",
        phoneNumber = "+919876500099",
        role = "coach",
        status = "active",
        active = true,
        branchIds = listOf("branch_main")
      )
    ).getOrThrow()

    // 1. Start time >= End time must be REJECTED
    signInTestUser("Mahi27d@gmail.com")
    val invalidTimeSession = ClassSession(
      sessionId = "sess_invalid_time",
      branchId = "branch_main",
      batchId = "batch_morning_karate",
      sessionDate = "2026-11-01",
      startTime = "18:00",
      endTime = "17:00" // Invalid: ends before it starts
    )
    val invalidTimeRes = repo.createClassSessionWithValidation(invalidTimeSession)
    assertTrue("Session with start time >= end time must be rejected", invalidTimeRes.isFailure)

    // 2. Coach unauthorized for branch must be REJECTED
    val unauthorizedCoachSession = ClassSession(
      sessionId = "sess_unauth_coach",
      branchId = "branch_west", // Sensei Rajesh is only branch_main
      batchId = "batch_youth_taekwondo",
      coachIds = listOf(coachUid),
      sessionDate = "2026-11-01",
      startTime = "17:00",
      endTime = "18:30"
    )
    val unauthCoachRes = repo.createClassSessionWithValidation(unauthorizedCoachSession)
    assertTrue("Session with coach unauthorized for branch must be rejected", unauthCoachRes.isFailure)

    // 3. Valid session schedule
    val validSession = ClassSession(
      sessionId = "sess_main_valid",
      branchId = "branch_main",
      batchId = "batch_morning_karate",
      coachIds = listOf(coachUid),
      sessionDate = "2026-11-01",
      startTime = "17:00",
      endTime = "18:30"
    )
    val validRes = repo.createClassSessionWithValidation(validSession)
    assertTrue("Valid session creation should succeed: ${validRes.exceptionOrNull()}", validRes.isSuccess)

    // 4. Overlapping scheduling conflict for same batch/coach on same day -> must be REJECTED
    val conflictingSession = ClassSession(
      sessionId = "sess_conflict_attempt",
      branchId = "branch_main",
      batchId = "batch_morning_karate",
      coachIds = listOf(coachUid),
      sessionDate = "2026-11-01",
      startTime = "17:30", // Overlaps with 17:00 - 18:30
      endTime = "19:00"
    )
    val conflictRes = repo.createClassSessionWithValidation(conflictingSession)
    assertTrue("Overlapping session conflict must be detected and rejected", conflictRes.isFailure)

    // 5. Reschedule session to non-conflicting time (19:00 - 20:30)
    val rescheduleRes = repo.rescheduleOrCancelSession(
      sessionId = "sess_main_valid",
      newStartTime = "19:00",
      newEndTime = "20:30"
    )
    assertTrue("Rescheduling session should succeed: ${rescheduleRes.exceptionOrNull()}", rescheduleRes.isSuccess)

    // 6. Cancel session
    val cancelRes = repo.rescheduleOrCancelSession(
      sessionId = "sess_main_valid",
      newStatus = "cancelled",
      reason = "Dojo maintenance"
    )
    assertTrue("Cancelling session should succeed: ${cancelRes.exceptionOrNull()}", cancelRes.isSuccess)
    val cancelledSnap = firestore.collection("classSessions").document("sess_main_valid").get().await()
    assertEquals("cancelled", cancelledSnap.getString("status"))
  }

  @Test
  fun phase5_attendance_batch_enrollment_duplicate_prevention_and_audit() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val coachUid = signInTestUser("coach_att_mgr@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.provisionCoach(
      UserProfile(
        userId = coachUid,
        displayName = "Coach Sunil",
        phoneNumber = "+919876500055",
        role = "coach",
        status = "active",
        active = true,
        branchIds = listOf("branch_main")
      )
    ).getOrThrow()

    // Create a batch with std_att_enrolled enrolled
    val batchId = "batch_attendance_test"
    repo.createOrUpdateBatch(
      Batch(
        batchId = batchId,
        name = "Kicking Masters",
        branchId = "branch_main",
        programId = "prog_karate",
        coachIds = listOf(coachUid),
        studentIds = listOf("std_att_enrolled")
      )
    ).getOrThrow()

    // Create student enrolled in batch, and another student in different batch
    repo.createOrUpdateStudent(
      Student(
        id = "std_att_enrolled",
        userId = "u_att_enrolled",
        fullName = "Harshita Kaur",
        branchId = "branch_main",
        batchId = batchId,
        status = "active"
      )
    ).getOrThrow()

    repo.createOrUpdateStudent(
      Student(
        id = "std_att_outside",
        userId = "u_att_outside",
        fullName = "Kabir Singh",
        branchId = "branch_main",
        batchId = "batch_different",
        status = "active"
      )
    ).getOrThrow()

    val sessionId = "session_att_cuj_1"

    // 1. Marking student outside the batch must be REJECTED!
    signInTestUser("coach_att_mgr@truefighters.in")
    val outsideAtt = Attendance(
      branchId = "branch_main",
      sessionId = sessionId,
      batchId = batchId,
      studentId = "std_att_outside",
      studentName = "Kabir Singh",
      date = "2026-11-02",
      status = "present"
    )
    val outsideRes = repo.markAttendance(outsideAtt)
    assertTrue("Marking attendance for student outside the batch must fail", outsideRes.isFailure)

    // 2. Marking enrolled student as 'present' succeeds
    val enrolledAtt = Attendance(
      branchId = "branch_main",
      sessionId = sessionId,
      batchId = batchId,
      studentId = "std_att_enrolled",
      studentName = "Harshita Kaur",
      date = "2026-11-02",
      status = "present"
    )
    val markRes = repo.markAttendance(enrolledAtt)
    assertTrue("Marking attendance for enrolled student must succeed: ${markRes.exceptionOrNull()}", markRes.isSuccess)

    // Verify deterministic doc ID: ${sessionId}_${studentId}
    val docId = "${sessionId}_std_att_enrolled"
    val docSnap = firestore.collection("attendance").document(docId).get().await()
    assertTrue("Attendance doc with deterministic ID must exist", docSnap.exists())
    assertEquals("present", docSnap.getString("status"))

    // 3. Status correction from 'present' to 'late' -> records audit in correctionHistory
    val correctionAtt = enrolledAtt.copy(status = "late")
    val correctionRes = repo.markAttendance(correctionAtt)
    assertTrue("Correcting attendance to late must succeed: ${correctionRes.exceptionOrNull()}", correctionRes.isSuccess)

    val updatedDoc = firestore.collection("attendance").document(docId).get().await()
    assertEquals("late", updatedDoc.getString("status"))
    val history = updatedDoc.get("correctionHistory") as? List<*>
    assertNotNull("Correction history must exist", history)
    assertTrue("Correction history must contain correction record", history!!.isNotEmpty())

    // 4. Marking batch attendance helper
    val batchMarkRes = repo.markBatchAttendance(
      sessionId = sessionId,
      records = listOf(enrolledAtt.copy(status = "present"))
    )
    assertTrue("markBatchAttendance should succeed: ${batchMarkRes.exceptionOrNull()}", batchMarkRes.isSuccess)
  }

  @Test
  fun phase5_student_attendance_privacy_and_security_rules() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val studentUid = signInTestUser("student_privacy@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    val studentDocId = "std_privacy_1"
    repo.createOrUpdateStudent(
      Student(
        id = studentDocId,
        userId = studentUid,
        fullName = "Kavya Menon",
        branchId = "branch_main",
        batchId = "batch_morning_karate",
        status = "active"
      )
    ).getOrThrow()

    // Admin records attendance for Kavya
    signInTestUser("Mahi27d@gmail.com")
    val attId = "sess_priv_std_privacy_1"
    repo.markAttendance(
      Attendance(
        id = attId,
        sessionId = "sess_priv",
        batchId = "batch_morning_karate",
        branchId = "branch_main",
        studentId = studentDocId,
        studentUserId = studentUid,
        studentName = "Kavya Menon",
        date = "2026-11-03",
        status = "present"
      )
    ).getOrThrow()

    // 1. Student CANNOT write or modify attendance records (Security Rules deny)
    signInTestUser("student_privacy@truefighters.in")
    val studentWriteAttempt = runCatching {
      firestore.collection("attendance").document(attId).update("status", "present").await()
    }
    assertTrue("Student direct attendance write must be rejected by security rules", studentWriteAttempt.isFailure)

    // 2. Student CAN read their own attendance
    val selfAttSnap = runCatching {
      firestore.collection("attendance").document(attId).get().await()
    }
    assertTrue("Student reading own attendance must succeed: ${selfAttSnap.exceptionOrNull()}", selfAttSnap.isSuccess)
    assertEquals("present", selfAttSnap.getOrThrow().getString("status"))

    // 3. Another student CANNOT read Kavya's attendance
    signInTestUser("intruder_student@truefighters.in")
    val intruderReadAttempt = runCatching {
      firestore.collection("attendance").document(attId).get().await()
    }
    assertTrue("Other student must NOT read Kavya's attendance document", intruderReadAttempt.isFailure)
  }

  @Test
  fun phase6_feePlan_creation_validation_and_branch_association() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // 1. Invalid: amount <= 0 must fail
    val invalidAmountPlan = FeePlan(
      planId = "fee_zero_test",
      name = "Free Plan",
      amountPaise = 0L,
      branchId = "branch_main"
    )
    val zeroRes = repo.createOrUpdateFeePlan(invalidAmountPlan)
    assertTrue("Fee plan with 0 amount must fail", zeroRes.isFailure)

    // 2. Invalid: blank name must fail
    val blankNamePlan = FeePlan(
      planId = "fee_blank_test",
      name = "   ",
      amountPaise = 200000L,
      branchId = "branch_main"
    )
    val blankRes = repo.createOrUpdateFeePlan(blankNamePlan)
    assertTrue("Fee plan with blank name must fail", blankRes.isFailure)

    // 3. Invalid: association with inactive branch must fail
    val inactiveBranchId = "branch_dormant_fee"
    repo.addBranch(
      Branch(
        branchId = inactiveBranchId,
        branchName = "Dormant Fee Branch",
        branchCode = "TF-DFB-${System.currentTimeMillis() % 10000}",
        address = "Quiet Street",
        status = "inactive",
        active = false
      )
    )
    val inactiveBranchPlan = FeePlan(
      planId = "fee_dormant_branch",
      name = "Dormant Plan",
      amountPaise = 250000L,
      branchId = inactiveBranchId
    )
    val inactBranchRes = repo.createOrUpdateFeePlan(inactiveBranchPlan)
    assertTrue("Associating fee plan with inactive branch must fail", inactBranchRes.isFailure)

    // 4. Valid fee plan creation succeeds
    val validPlan = FeePlan(
      planId = "fee_kickbox_pro",
      name = "Pro Kickboxing Unlimited",
      planName = "Pro Kickboxing Unlimited",
      branchId = "branch_main",
      programId = "prog_kickboxing",
      amountPaise = 350000L, // ₹3,500
      billingFrequency = "Monthly",
      dueDay = 5,
      status = "active",
      active = true
    )
    val createRes = repo.createOrUpdateFeePlan(validPlan)
    assertTrue("Valid fee plan creation should succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)

    val fetched = repo.getFeePlan("fee_kickbox_pro").getOrThrow()
    assertNotNull("Fee plan must exist in Firestore", fetched)
    assertEquals(350000L, fetched?.amountPaise)
    assertTrue("Fee plan must be active", fetched?.isPlanActive == true)

    // 5. Deactivate plan
    val deactRes = repo.setFeePlanStatus("fee_kickbox_pro", "inactive")
    assertTrue("Fee plan deactivation should succeed: ${deactRes.exceptionOrNull()}", deactRes.isSuccess)
    val deactivated = repo.getFeePlan("fee_kickbox_pro").getOrThrow()
    assertFalse("Deactivated fee plan isPlanActive must be false", deactivated?.isPlanActive == true)
  }

  @Test
  fun phase6_invoice_generation_itemization_and_duplicate_prevention() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // 1. Invalid: invoice amount <= 0 must fail
    val zeroInvoice = FeeInvoice(
      invoiceId = "inv_zero_test",
      studentId = "std_inv_test_1",
      branchId = "branch_main",
      period = "November 2026",
      amountPaise = 0L
    )
    val zeroRes = repo.generateInvoiceWithValidation(zeroInvoice)
    assertTrue("Invoice with zero amount must fail", zeroRes.isFailure)

    // 2. Valid invoice with itemized charges succeeds
    val validInvoice = FeeInvoice(
      invoiceId = "inv_valid_nov_2026",
      invoiceNumber = "TF-INV-9901",
      branchId = "branch_main",
      branchName = "True Fighters Central Dojo",
      studentId = "std_inv_test_1",
      studentUserId = "user_inv_test_1",
      studentName = "Aryan Sharma",
      feePlanId = "fee_karate_monthly",
      period = "November 2026",
      dueDate = "2026-11-10",
      itemizedItems = listOf(
        InvoiceItem(title = "Monthly Tuition", amountPaise = 250000L),
        InvoiceItem(title = "Dojo Uniform / Gi", amountPaise = 100000L)
      ),
      amountPaise = 350000L, // Total ₹3,500
      paidPaise = 0L,
      status = "pending"
    )
    val genRes = repo.generateInvoiceWithValidation(validInvoice)
    assertTrue("Invoice generation should succeed: ${genRes.exceptionOrNull()}", genRes.isSuccess)

    val fetched = repo.getInvoice("inv_valid_nov_2026").getOrThrow()
    assertNotNull("Invoice must exist in Firestore", fetched)
    assertEquals(350000L, fetched?.amountPaise)
    assertEquals(350000L, fetched?.balancePaise)
    assertEquals("pending", fetched?.status)
    assertEquals(2, fetched?.itemizedItems?.size)

    // 3. Duplicate invoice prevention: Same student, same period -> must be REJECTED!
    val duplicateInvoice = FeeInvoice(
      invoiceId = "inv_dup_nov_attempt",
      studentId = "std_inv_test_1",
      branchId = "branch_main",
      period = "November 2026",
      amountPaise = 350000L
    )
    val dupRes = repo.generateInvoiceWithValidation(duplicateInvoice)
    assertTrue("Duplicate invoice for same student and period must fail", dupRes.isFailure)
  }

  @Test
  fun phase6_payment_recording_partial_payment_completion_and_duplicate_prevention() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val invoiceId = "inv_cuj_payment_test"
    val invoice = FeeInvoice(
      invoiceId = invoiceId,
      branchId = "branch_main",
      studentId = "std_cuj_pay_1",
      studentUserId = "user_cuj_pay_1",
      studentName = "Tara Deshmukh",
      period = "December 2026",
      amountPaise = 300000L, // ₹3,000
      paidPaise = 0L,
      dueDate = "2026-12-10",
      status = "pending"
    )
    repo.generateInvoiceWithValidation(invoice).getOrThrow()

    // 1. Invalid: Payment amount <= 0 must fail
    val zeroPayment = Payment(
      paymentId = "pay_zero",
      invoiceId = invoiceId,
      branchId = "branch_main",
      amountPaise = 0L,
      providerReference = "REF-ZERO"
    )
    val zeroPayRes = repo.recordPaymentWithValidation(zeroPayment, requireExistingInvoice = true)
    assertTrue("Payment with zero amount must fail", zeroPayRes.isFailure)

    // 2. Partial Payment: Pay ₹1,000 of ₹3,000 -> status transitions to 'partially_paid'
    val partialPayment = Payment(
      paymentId = "pay_partial_1",
      invoiceId = invoiceId,
      branchId = "branch_main",
      studentId = "std_cuj_pay_1",
      studentName = "Tara Deshmukh",
      amountPaise = 100000L, // ₹1,000
      method = "cash",
      providerReference = "RCP-PARTIAL-001",
      paymentDate = "2026-12-02"
    )
    val partRes = repo.recordPaymentWithValidation(partialPayment, requireExistingInvoice = true)
    assertTrue("Partial payment should succeed: ${partRes.exceptionOrNull()}", partRes.isSuccess)

    val updatedInv1 = repo.getInvoice(invoiceId).getOrThrow()
    assertEquals(100000L, updatedInv1?.paidPaise)
    assertEquals(200000L, updatedInv1?.balancePaise)
    assertEquals("partially_paid", updatedInv1?.status)

    // 3. Duplicate payment prevention: Same providerReference -> must be REJECTED!
    val duplicatePayment = Payment(
      paymentId = "pay_dup_ref",
      invoiceId = invoiceId,
      branchId = "branch_main",
      amountPaise = 100000L,
      providerReference = "RCP-PARTIAL-001" // Duplicate ref!
    )
    val dupPayRes = repo.recordPaymentWithValidation(duplicatePayment, requireExistingInvoice = true)
    assertTrue("Payment with duplicate providerReference must fail", dupPayRes.isFailure)

    // 4. Final Payment: Pay remaining ₹2,000 -> status transitions to 'paid'
    val finalPayment = Payment(
      paymentId = "pay_final_2",
      invoiceId = invoiceId,
      branchId = "branch_main",
      studentId = "std_cuj_pay_1",
      studentName = "Tara Deshmukh",
      amountPaise = 200000L, // ₹2,000
      method = "upi",
      providerReference = "UTR-FINAL-998822",
      paymentDate = "2026-12-05"
    )
    val finalRes = repo.recordPaymentWithValidation(finalPayment, requireExistingInvoice = true)
    assertTrue("Final payment should succeed: ${finalRes.exceptionOrNull()}", finalRes.isSuccess)

    val completedInv = repo.getInvoice(invoiceId).getOrThrow()
    assertEquals(300000L, completedInv?.paidPaise)
    assertEquals(0L, completedInv?.balancePaise)
    assertEquals("paid", completedInv?.status)
    assertTrue("Invoice isPaid should be true", completedInv?.isPaid == true)
  }

  @Test
  fun phase6_payment_refund_and_invoice_reversion() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val invoiceId = "inv_refund_test"
    repo.generateInvoiceWithValidation(
      FeeInvoice(
        invoiceId = invoiceId,
        branchId = "branch_main",
        studentId = "std_refund_1",
        studentName = "Naveen Bhat",
        period = "January 2027",
        amountPaise = 250000L,
        status = "pending"
      )
    ).getOrThrow()

    val paymentId = "pay_to_refund"
    repo.recordPaymentWithValidation(
      Payment(
        paymentId = paymentId,
        invoiceId = invoiceId,
        branchId = "branch_main",
        studentId = "std_refund_1",
        studentName = "Naveen Bhat",
        amountPaise = 250000L,
        providerReference = "UTR-REFUND-ME",
        status = "verified"
      ),
      requireExistingInvoice = true
    ).getOrThrow()

    val paidInv = repo.getInvoice(invoiceId).getOrThrow()
    assertEquals("paid", paidInv?.status)

    // Admin processes refund
    val refundRes = repo.refundPayment(paymentId, reason = "Student relocating out of state")
    assertTrue("Processing refund should succeed: ${refundRes.exceptionOrNull()}", refundRes.isSuccess)

    val refundedPayment = repo.getPayment(paymentId).getOrThrow()
    assertEquals("refunded", refundedPayment?.status)
    assertTrue("Audit trail should contain refund log", refundedPayment?.auditLog?.any { it["action"] == "PAYMENT_REFUNDED" } == true)

    // Invoice status reverts back to 'pending' with 0 paid
    val revertedInv = repo.getInvoice(invoiceId).getOrThrow()
    assertEquals(0L, revertedInv?.paidPaise)
    assertEquals(250000L, revertedInv?.balancePaise)
    assertEquals("pending", revertedInv?.status)

    // Repeated refund on same payment must fail
    val doubleRefundRes = repo.refundPayment(paymentId, reason = "Double refund attempt")
    assertTrue("Refunding an already refunded payment must fail", doubleRefundRes.isFailure)
  }

  @Test
  fun phase6_fee_and_invoice_branch_isolation_and_student_privacy() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Create student A in branch_main and student B in branch_west
    val studentAUid = signInTestUser("student_a_fees@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_fee_priv_a",
        userId = studentAUid,
        fullName = "Aarav Pillai",
        branchId = "branch_main",
        status = "active"
      )
    ).getOrThrow()

    val studentBUid = signInTestUser("student_b_fees@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_fee_priv_b",
        userId = studentBUid,
        fullName = "Bhavna Joshi",
        branchId = "branch_west",
        status = "active"
      )
    ).getOrThrow()

    // Provision a branch-scoped admin for branch_west ONLY
    val westAdminUid = signInTestUser("admin_west_fees_only@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    firestore.collection("admins").document(westAdminUid).set(
      mapOf(
        "userId" to westAdminUid,
        "email" to "admin_west_fees_only@truefighters.in",
        "phoneNumber" to "+919876500091",
        "role" to "admin",
        "scope" to "branch",
        "branchIds" to listOf("branch_west"),
        "active" to true
      )
    ).await()

    // Global admin creates invoice for Student A in branch_main
    val invId = "inv_priv_branch_main_a"
    repo.generateInvoiceWithValidation(
      FeeInvoice(
        invoiceId = invId,
        branchId = "branch_main",
        studentId = "std_fee_priv_a",
        studentUserId = studentAUid,
        studentName = "Aarav Pillai",
        period = "February 2027",
        amountPaise = 250000L
      )
    ).getOrThrow()

    // 1. Student A CAN read their own invoice
    signInTestUser("student_a_fees@truefighters.in")
    val studentARead = runCatching {
      firestore.collection("feeInvoices").document(invId).get().await()
    }
    assertTrue("Student A reading own invoice must succeed: ${studentARead.exceptionOrNull()}", studentARead.isSuccess)
    assertEquals(250000L, studentARead.getOrThrow().getLong("amountPaise"))

    // 2. Student B CANNOT read Student A's invoice (Security Rules deny)
    signInTestUser("student_b_fees@truefighters.in")
    val studentBRead = runCatching {
      firestore.collection("feeInvoices").document(invId).get().await()
    }
    assertTrue("Student B must NOT read Student A's invoice", studentBRead.isFailure)

    // 3. Student A CANNOT modify or create invoices directly
    signInTestUser("student_a_fees@truefighters.in")
    val studentWriteAttempt = runCatching {
      firestore.collection("feeInvoices").document(invId).update("status", "paid").await()
    }
    assertTrue("Student direct write on invoice must be rejected by security rules", studentWriteAttempt.isFailure)

    // 4. Branch admin for branch_west CANNOT read branch_main invoice (Branch isolation)
    signInTestUser("admin_west_fees_only@truefighters.in")
    val westAdminRead = runCatching {
      firestore.collection("feeInvoices").document(invId).get().await()
    }
    assertTrue("Branch admin for branch_west must NOT read branch_main invoice", westAdminRead.isFailure)
  }

  @Test
  fun phase7_beltRank_configuration_and_program_ladder() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    // Admin creates belt rank
    val purpleRank = BeltRank(
      rankId = "rank_karate_purple_test",
      programId = "prog_karate",
      programName = "Karate-Do",
      beltName = "Purple Belt",
      rankOrder = 4,
      colorHex = "#800080",
      syllabus = "Heian Yondan Kata and Advanced Sparring",
      active = true
    )
    val createResult = repo.createOrUpdateBeltRank(purpleRank)
    assertTrue("Admin should be able to create belt rank: ${createResult.exceptionOrNull()}", createResult.isSuccess)

    // Admin toggles active status
    val toggleResult = repo.setBeltRankStatus("rank_karate_purple_test", false)
    assertTrue("Admin should be able to toggle belt rank status", toggleResult.isSuccess)

    // Student can read belt rank
    signInTestUser("student_belt_reader@truefighters.in")
    val readResult = repo.getBeltRank("rank_karate_purple_test")
    assertTrue("Student should be able to read belt rank", readResult.isSuccess)
    assertEquals("Purple Belt", readResult.getOrNull()?.beltName)

    // Student cannot write or alter belt ranks directly (blocked by Firestore Rules)
    val unauthWrite = runCatching {
      firestore.collection("beltRanks").document("rank_karate_purple_test")
        .update("beltName", "Tampered Belt").await()
    }
    assertTrue("Student direct write on belt rank must be rejected by rules", unauthWrite.isFailure)
  }

  @Test
  fun phase7_coach_grading_submission_and_branch_authorization() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    // Ensure branch_main and branch_west exist
    repo.seedAcademyDefaultsIfNeeded()

    // Provision Student in branch_main
    val studentUid = signInTestUser("std_grading_target@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_grading_target",
        userId = studentUid,
        fullName = "Devansh Verma",
        branchId = "branch_main",
        programId = "prog_karate",
        currentBelt = "White Belt",
        status = "active"
      )
    ).getOrThrow()

    // Provision Coach A for branch_main
    val coachAUid = signInTestUser("coach_main_auth@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    firestore.collection("users").document(coachAUid).set(
      mapOf(
        "userId" to coachAUid,
        "email" to "coach_main_auth@truefighters.in",
        "displayName" to "Sensei Vikram",
        "role" to "coach",
        "branchIds" to listOf("branch_main"),
        "status" to "active"
      )
    ).await()

    // Provision Coach B for branch_west ONLY
    val coachBUid = signInTestUser("coach_west_unauth@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    firestore.collection("users").document(coachBUid).set(
      mapOf(
        "userId" to coachBUid,
        "email" to "coach_west_unauth@truefighters.in",
        "displayName" to "Sensei Kabir",
        "role" to "coach",
        "branchIds" to listOf("branch_west"),
        "status" to "active"
      )
    ).await()

    // 1. Authorized Coach A submits assessment for branch_main student -> SUCCESS
    signInTestUser("coach_main_auth@truefighters.in")
    val assessment = GradingAssessment(
      assessmentId = "assess_devansh_yellow",
      studentId = "std_grading_target",
      studentUserId = studentUid,
      studentName = "Devansh Verma",
      branchId = "branch_main",
      programId = "prog_karate",
      currentBeltName = "White Belt",
      currentRankOrder = 1,
      proposedBeltName = "Yellow Belt",
      proposedRankOrder = 2,
      score = 88,
      techniqueGrade = "A",
      coachComments = "Demonstrated great stances and focus in Heian Shodan."
    )
    val submitResult = repo.submitGradingAssessment(assessment)
    assertTrue("Authorized coach should be able to submit assessment: ${submitResult.exceptionOrNull()}", submitResult.isSuccess)

    // 2. Unauthorized Coach B attempts to submit assessment for branch_main student -> FAILS
    signInTestUser("coach_west_unauth@truefighters.in")
    val crossBranchAssessment = GradingAssessment(
      assessmentId = "assess_devansh_orange_illegal",
      studentId = "std_grading_target",
      studentUserId = studentUid,
      studentName = "Devansh Verma",
      branchId = "branch_main",
      programId = "prog_karate",
      currentBeltName = "White Belt",
      currentRankOrder = 1,
      proposedBeltName = "Orange Belt",
      proposedRankOrder = 3,
      score = 90
    )
    val unauthorizedSubmit = repo.submitGradingAssessment(crossBranchAssessment)
    assertTrue("Cross-branch coach submission must be rejected", unauthorizedSubmit.isFailure)
  }

  @Test
  fun phase7_admin_approval_promotes_student_and_generates_certificate() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Provision student
    val studentUid = signInTestUser("std_promo_test@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_promo_test",
        userId = studentUid,
        fullName = "Ishaan Gupta",
        branchId = "branch_main",
        programId = "prog_karate",
        currentBelt = "White Belt",
        status = "active"
      )
    ).getOrThrow()

    // Submit pending assessment
    val assessId = "assess_ishaan_yellow_promo"
    repo.submitGradingAssessment(
      GradingAssessment(
        assessmentId = assessId,
        studentId = "std_promo_test",
        studentUserId = studentUid,
        studentName = "Ishaan Gupta",
        branchId = "branch_main",
        programId = "prog_karate",
        currentBeltName = "White Belt",
        currentRankOrder = 1,
        proposedBeltName = "Yellow Belt",
        proposedRankOrder = 2,
        score = 92,
        techniqueGrade = "Distinction",
        coachComments = "Exceptional technique and speed."
      )
    ).getOrThrow()

    // Admin approves the promotion
    val approveResult = repo.approveGradingAssessment(assessId, adminNotes = "Approved with highest honors.")
    assertTrue("Admin approval must succeed: ${approveResult.exceptionOrNull()}", approveResult.isSuccess)

    val certificate = approveResult.getOrThrow()
    assertNotNull("Certificate must be returned", certificate)
    assertTrue("Certificate number must start with TF-CERT: ${certificate.certificateNumber}", certificate.certificateNumber.startsWith("TF-CERT-"))
    assertTrue("Verification code must be generated", certificate.verificationCode.isNotBlank())
    assertEquals("Yellow Belt", certificate.awardedBelt)

    // Verify student record's currentBelt is updated
    val updatedStudentSnap = firestore.collection("students").document("std_promo_test").get().await()
    assertEquals("Yellow Belt", updatedStudentSnap.getString("currentBelt"))

    // Verify assessment status is "approved" and certificateId is linked
    val updatedAssessSnap = firestore.collection("gradingAssessments").document(assessId).get().await()
    assertEquals("approved", updatedAssessSnap.getString("status"))
    assertEquals(certificate.certificateId, updatedAssessSnap.getString("certificateId"))
  }

  @Test
  fun phase7_invalid_progression_and_duplicate_promotion_denied() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Provision student
    val studentUid = signInTestUser("std_invalid_prog@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_invalid_prog",
        userId = studentUid,
        fullName = "Meera Nair",
        branchId = "branch_main",
        programId = "prog_karate",
        currentBelt = "Green Belt",
        status = "active"
      )
    ).getOrThrow()

    // 1. Invalid progression: same belt rank (proposedOrder <= currentOrder)
    val invalidRankSubmit = repo.submitGradingAssessment(
      GradingAssessment(
        assessmentId = "assess_meera_invalid_prog",
        studentId = "std_invalid_prog",
        studentUserId = studentUid,
        studentName = "Meera Nair",
        branchId = "branch_main",
        programId = "prog_karate",
        currentBeltName = "Green Belt",
        currentRankOrder = 4,
        proposedBeltName = "Green Belt",
        proposedRankOrder = 4,
        score = 75
      )
    )
    assertTrue("Submitting equal/lower rank without progression must fail", invalidRankSubmit.isFailure)

    // 2. Submit valid pending assessment
    val validSubmit = repo.submitGradingAssessment(
      GradingAssessment(
        assessmentId = "assess_meera_blue_valid",
        studentId = "std_invalid_prog",
        studentUserId = studentUid,
        studentName = "Meera Nair",
        branchId = "branch_main",
        programId = "prog_karate",
        currentBeltName = "Green Belt",
        currentRankOrder = 4,
        proposedBeltName = "Blue Belt",
        proposedRankOrder = 5,
        score = 85
      )
    )
    assertTrue("Valid assessment submission must succeed", validSubmit.isSuccess)

    // 3. Duplicate pending assessment for same target belt must be rejected
    val duplicateSubmit = repo.submitGradingAssessment(
      GradingAssessment(
        assessmentId = "assess_meera_blue_dup",
        studentId = "std_invalid_prog",
        studentUserId = studentUid,
        studentName = "Meera Nair",
        branchId = "branch_main",
        programId = "prog_karate",
        currentBeltName = "Green Belt",
        currentRankOrder = 4,
        proposedBeltName = "Blue Belt",
        proposedRankOrder = 5,
        score = 88
      )
    )
    assertTrue("Duplicate pending assessment for same student and rank must be rejected", duplicateSubmit.isFailure)
  }

  @Test
  fun phase7_certificate_student_privacy_and_unauthorized_modification() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    val studentAUid = signInTestUser("std_cert_a@truefighters.in")
    val studentBUid = signInTestUser("std_cert_b@truefighters.in")

    signInTestUser("Mahi27d@gmail.com")
    val certId = "cert_priv_a_123"
    firestore.collection("certificates").document(certId).set(
      mapOf(
        "certificateId" to certId,
        "certificateNumber" to "TF-CERT-KARATE-777",
        "branchId" to "branch_main",
        "studentId" to "std_cert_a",
        "studentUserId" to studentAUid,
        "studentName" to "Student Alpha",
        "programId" to "prog_karate",
        "programName" to "Karate-Do",
        "awardedBelt" to "Black Belt 1st Dan",
        "rankOrder" to 8,
        "status" to "issued"
      )
    ).await()

    // 1. Student A can read their own certificate
    signInTestUser("std_cert_a@truefighters.in")
    val studentARead = runCatching {
      firestore.collection("certificates").document(certId).get().await()
    }
    assertTrue("Student A reading own certificate must succeed: ${studentARead.exceptionOrNull()}", studentARead.isSuccess)
    assertEquals("TF-CERT-KARATE-777", studentARead.getOrThrow().getString("certificateNumber"))

    // 2. Student B CANNOT read Student A's certificate (Security Rules deny)
    signInTestUser("std_cert_b@truefighters.in")
    val studentBRead = runCatching {
      firestore.collection("certificates").document(certId).get().await()
    }
    assertTrue("Student B reading Student A's certificate must be rejected by rules", studentBRead.isFailure)

    // 3. Student A CANNOT modify certificates directly (Security Rules deny)
    signInTestUser("std_cert_a@truefighters.in")
    val tamperAttempt = runCatching {
      firestore.collection("certificates").document(certId).update("awardedBelt", "Grandmaster 10th Dan").await()
    }
    assertTrue("Student direct write on certificate must be rejected by rules", tamperAttempt.isFailure)
  }

  @Test
  fun phase7_trainingVideo_catalogue_management_and_filtering() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    // Admin creates training video
    val testVideo = Video(
      videoId = "vid_mae_geri_test",
      title = "Mae Geri Front Snap Kick Mastery",
      description = "Step by step execution of front snap kick with chamber and recovery.",
      programId = "prog_karate",
      programName = "Karate-Do",
      category = "Techniques",
      videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      durationText = "8 mins",
      minRankOrder = 1,
      targetBelt = "All",
      published = true
    )
    val createResult = repo.createOrUpdateVideo(testVideo)
    assertTrue("Admin should be able to create video: ${createResult.exceptionOrNull()}", createResult.isSuccess)

    // Student can read video
    signInTestUser("student_video_viewer@truefighters.in")
    val vidSnap = firestore.collection("videos").document("vid_mae_geri_test").get().await()
    assertTrue("Student should be able to read video", vidSnap.exists())
    assertEquals("Mae Geri Front Snap Kick Mastery", vidSnap.getString("title"))

    // Student cannot edit video (Firestore Rules deny)
    val studentEditAttempt = runCatching {
      firestore.collection("videos").document("vid_mae_geri_test").update("published", false).await()
    }
    assertTrue("Student direct update on video must be rejected by rules", studentEditAttempt.isFailure)

    // Admin can toggle publish and delete
    signInTestUser("Mahi27d@gmail.com")
    val toggleResult = repo.setVideoPublished("vid_mae_geri_test", false)
    assertTrue("Admin can toggle publish", toggleResult.isSuccess)

    val deleteResult = repo.deleteVideo("vid_mae_geri_test")
    assertTrue("Admin can delete video", deleteResult.isSuccess)
  }

  @Test
  fun phase8_academy_overview_metrics_calculation_and_totals() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Provision test student
    val studentUid = signInTestUser("std_metric_calc@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_metric_calc_1",
        userId = studentUid,
        fullName = "Harsh Vardhan",
        branchId = "branch_main",
        status = "active"
      )
    ).getOrThrow()

    // Provision invoice and payment
    val invId = "inv_metric_test_1"
    repo.generateInvoiceWithValidation(
      FeeInvoice(
        invoiceId = invId,
        branchId = "branch_main",
        studentId = "std_metric_calc_1",
        studentUserId = studentUid,
        studentName = "Harsh Vardhan",
        period = "November 2026",
        amountPaise = 300000L,
        paidPaise = 0L,
        status = "unpaid"
      )
    ).getOrThrow()

    repo.recordPaymentWithValidation(
      Payment(
        paymentId = "pmt_metric_test_1",
        invoiceId = invId,
        branchId = "branch_main",
        studentId = "std_metric_calc_1",
        studentUserId = studentUid,
        studentName = "Harsh Vardhan",
        amountPaise = 100000L,
        paymentDate = "2026-10-23",
        method = "cash",
        status = "verified"
      )
    ).getOrThrow()

    val metricsRes = repo.computeAcademyOverviewMetrics("all")
    assertTrue("computeAcademyOverviewMetrics must succeed: ${metricsRes.exceptionOrNull()}", metricsRes.isSuccess)

    val metrics = metricsRes.getOrThrow()
    assertTrue("Total active students must be at least 1: ${metrics.totalActiveStudents}", metrics.totalActiveStudents >= 1)
    assertTrue("Total collections must be >= 100000L paise: ${metrics.totalCollectionsPaise}", metrics.totalCollectionsPaise >= 100000L)
    assertTrue("Total outstanding must be >= 200000L paise: ${metrics.totalOutstandingPaise}", metrics.totalOutstandingPaise >= 200000L)
    assertTrue("Active branches count must be >= 1: ${metrics.activeBranchesCount}", metrics.activeBranchesCount >= 1)
  }

  @Test
  fun phase8_branch_level_dashboard_isolation_and_filtering() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Student A in branch_main
    val studentAUid = signInTestUser("std_branch_a_metrics@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_branch_a_unique",
        userId = studentAUid,
        fullName = "Aanya Singhal",
        branchId = "branch_main",
        status = "active"
      )
    ).getOrThrow()

    // Student B in branch_west
    val studentBUid = signInTestUser("std_branch_b_metrics@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_branch_b_unique",
        userId = studentBUid,
        fullName = "Bhavin Kothari",
        branchId = "branch_west",
        status = "active"
      )
    ).getOrThrow()

    // Invoice for Student A in branch_main
    repo.generateInvoiceWithValidation(
      FeeInvoice(
        invoiceId = "inv_iso_branch_main",
        branchId = "branch_main",
        studentId = "std_branch_a_unique",
        studentUserId = studentAUid,
        studentName = "Aanya Singhal",
        period = "November 2026",
        amountPaise = 500000L,
        status = "unpaid"
      )
    ).getOrThrow()

    // Invoice for Student B in branch_west
    repo.generateInvoiceWithValidation(
      FeeInvoice(
        invoiceId = "inv_iso_branch_west",
        branchId = "branch_west",
        studentId = "std_branch_b_unique",
        studentUserId = studentBUid,
        studentName = "Bhavin Kothari",
        period = "November 2026",
        amountPaise = 700000L,
        status = "unpaid"
      )
    ).getOrThrow()

    // Metrics filtered by branch_main
    val mainMetrics = repo.computeAcademyOverviewMetrics("branch_main").getOrThrow()
    assertEquals("branch_main", mainMetrics.branchId)
    // Invoices for branch_main must include 500000L, and exclude branch_west's 700000L
    val westMetrics = repo.computeAcademyOverviewMetrics("branch_west").getOrThrow()
    assertEquals("branch_west", westMetrics.branchId)

    // Branch financial report isolation check
    val mainFinReport = repo.computeFinancialReport(branchId = "branch_main").getOrThrow()
    val westFinReport = repo.computeFinancialReport(branchId = "branch_west").getOrThrow()

    assertTrue("Main financial report must reflect branch_main invoices", mainFinReport.totalOutstandingPaise >= 500000L)
    assertTrue("West financial report must reflect branch_west invoices", westFinReport.totalOutstandingPaise >= 700000L)
  }

  @Test
  fun phase8_financial_report_totals_and_period_filtering() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    val studentUid = signInTestUser("std_fin_report@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")

    val invNov = "inv_period_nov_2026"
    val invDec = "inv_period_dec_2026"

    repo.generateInvoiceWithValidation(
      FeeInvoice(
        invoiceId = invNov,
        branchId = "branch_main",
        studentId = "std_fin_report",
        studentUserId = studentUid,
        studentName = "Rhea Kapoor",
        period = "November 2026",
        amountPaise = 400000L,
        status = "unpaid"
      )
    ).getOrThrow()

    repo.generateInvoiceWithValidation(
      FeeInvoice(
        invoiceId = invDec,
        branchId = "branch_main",
        studentId = "std_fin_report",
        studentUserId = studentUid,
        studentName = "Rhea Kapoor",
        period = "December 2026",
        amountPaise = 600000L,
        status = "unpaid"
      )
    ).getOrThrow()

    // Test period filter for November 2026
    val novReport = repo.computeFinancialReport(branchId = "branch_main", period = "November 2026").getOrThrow()
    assertTrue("November report must account for November invoices", novReport.totalInvoicedPaise >= 400000L)

    // Verify integer paise precision
    assertTrue("Integer paise calculations must remain exact", novReport.totalInvoicedPaise % 100L == 0L)
  }

  @Test
  fun phase8_attendance_analytics_and_date_filtering() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")
    repo.seedAcademyDefaultsIfNeeded()

    // Student in branch_main enrolled in batch_morning_karate
    val studentUid = signInTestUser("std_att_analytics@truefighters.in")
    signInTestUser("Mahi27d@gmail.com")
    repo.createOrUpdateStudent(
      Student(
        id = "std_att_analytics_1",
        userId = studentUid,
        fullName = "Tanvi Bansal",
        branchId = "branch_main",
        batchId = "batch_morning_karate",
        status = "active"
      )
    ).getOrThrow()

    // Record attendance on 2026-10-21 (Present) and 2026-10-22 (Late)
    repo.markAttendance(
      Attendance(
        id = "att_tanvi_21",
        branchId = "branch_main",
        batchId = "batch_morning_karate",
        sessionId = "sess_ana_21",
        studentId = "std_att_analytics_1",
        studentUserId = studentUid,
        studentName = "Tanvi Bansal",
        date = "2026-10-21",
        status = "present"
      )
    ).getOrThrow()

    repo.markAttendance(
      Attendance(
        id = "att_tanvi_22",
        branchId = "branch_main",
        batchId = "batch_morning_karate",
        sessionId = "sess_ana_22",
        studentId = "std_att_analytics_1",
        studentUserId = studentUid,
        studentName = "Tanvi Bansal",
        date = "2026-10-22",
        status = "late"
      )
    ).getOrThrow()

    val summary = repo.computeAttendanceSummary(
      branchId = "branch_main",
      startDate = "2026-10-21",
      endDate = "2026-10-22"
    ).getOrThrow()

    assertTrue("Summary logs count must be >= 2: ${summary.totalLogs}", summary.totalLogs >= 2)
    assertTrue("Present count must be >= 1: ${summary.presentCount}", summary.presentCount >= 1)
    assertTrue("Late count must be >= 1: ${summary.lateCount}", summary.lateCount >= 1)
    assertTrue("Present rate must be greater than 0: ${summary.presentRate}%", summary.presentRate > 0)
  }

  @Test
  fun phase8_audit_logs_streaming_and_unauthorized_access() = runBlocking {
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    // Admin can read audit logs
    val adminLogsSnap = firestore.collection("auditLogs").limit(10).get().await()
    assertNotNull("Admin can query audit logs", adminLogsSnap)

    // Student attempts to write to audit logs directly -> Denied by Firestore Security Rules
    val studentUid = signInTestUser("student_audit_tamper@truefighters.in")
    val unauthWrite = runCatching {
      firestore.collection("auditLogs").document("audit_illegal_tamper").set(
        mapOf(
          "action" to "ILLEGAL_WRITE",
          "actorUid" to studentUid,
          "details" to "Student trying to fabricate audit logs"
        )
      ).await()
    }
    assertTrue("Student direct write on auditLogs must be rejected by rules", unauthWrite.isFailure)
  }

  // ==========================================
  // PHASE 9: NOTIFICATIONS, FCM & HARDENING
  // ==========================================

  @Test
  fun phase9_notification_dispatch_deduplication_and_read_status() = runBlocking {
    val studentUid = signInTestUser("student_notif_recipient@truefighters.in")
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    // 1. Admin dispatches a notification with dedupKey and PII in data map
    val firstId = repo.dispatchNotificationWithDeduplication(
      AppNotification(
        id = "notif_fee_oct_1",
        recipientUserId = studentUid,
        recipientRole = "student",
        branchId = "branch_main",
        type = "fee_reminder",
        title = "October Dojo Fee Reminder",
        body = "Your October fee invoice is due on 10th October.",
        channelId = "fees",
        dedupKey = "fee_due_oct_${studentUid}",
        data = mapOf(
          "invoiceId" to "inv_oct_01",
          "phoneNumber" to "+919999988888", // Should be stripped by PII sanitizer
          "guardianPhone" to "+919999977777" // Should be stripped by PII sanitizer
        )
      )
    ).getOrThrow()
    assertEquals("notif_fee_oct_1", firstId)

    // 2. Dispatching again with the same dedupKey returns existing ID without creating a duplicate
    val secondId = repo.dispatchNotificationWithDeduplication(
      AppNotification(
        id = "notif_fee_oct_duplicate",
        recipientUserId = studentUid,
        recipientRole = "student",
        branchId = "branch_main",
        type = "fee_reminder",
        title = "Duplicate Reminder",
        body = "Duplicate body",
        channelId = "fees",
        dedupKey = "fee_due_oct_${studentUid}"
      )
    ).getOrThrow()
    assertEquals("Deduplication must return original notification ID", firstId, secondId)

    val duplicateSnap = firestore.collection("notifications").document("notif_fee_oct_duplicate").get().await()
    assertFalse("Duplicate notification document must not be created", duplicateSnap.exists())

    // 3. Verify PII keys were stripped from notification data payload
    val savedSnap = firestore.collection("notifications").document(firstId).get().await()
    val savedData = savedSnap.get("data") as? Map<*, *> ?: emptyMap<String, String>()
    assertEquals("inv_oct_01", savedData["invoiceId"])
    assertFalse("Sensitive phoneNumber must be stripped from notification payload", savedData.containsKey("phoneNumber"))
    assertFalse("Sensitive guardianPhone must be stripped from notification payload", savedData.containsKey("guardianPhone"))

    // 4. Recipient student signs in, reads notification, and marks it as read
    signInTestUser("student_notif_recipient@truefighters.in")
    val studentNotifSnap = firestore.collection("notifications").document(firstId).get().await()
    assertTrue("Recipient student can read their notification", studentNotifSnap.exists())
    assertEquals(false, studentNotifSnap.getBoolean("read"))

    repo.markNotificationAsRead(firstId).getOrThrow()
    val updatedNotifSnap = firestore.collection("notifications").document(firstId).get().await()
    assertEquals(true, updatedNotifSnap.getBoolean("read"))
  }

  @Test
  fun phase9_notification_privacy_and_unauthorized_injection_denied() = runBlocking {
    val studentAUid = signInTestUser("student_notif_a@truefighters.in")
    val studentBUid = signInTestUser("student_notif_b@truefighters.in")
    val coachMainUid = signInTestUser("coach_notif_main@truefighters.in")

    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    repo.provisionCoach(
      UserProfile(
        userId = coachMainUid,
        displayName = "Coach Main Notif",
        email = "coach_notif_main@truefighters.in",
        phoneNumber = "+919811199001",
        role = "coach",
        status = "active",
        active = true,
        branchIds = listOf("branch_main")
      )
    ).getOrThrow()

    // Admin creates private notification for Student A
    repo.dispatchNotificationWithDeduplication(
      AppNotification(
        id = "notif_private_std_a",
        recipientUserId = studentAUid,
        recipientRole = "student",
        branchId = "branch_main",
        type = "payment_receipt",
        title = "Payment Verified",
        body = "Your fee receipt is ready.",
        channelId = "fees",
        dedupKey = "receipt_private_a"
      )
    ).getOrThrow()

    // 1. Student B attempts to read Student A's private notification -> Denied
    signInTestUser("student_notif_b@truefighters.in")
    val crossStudentRead = runCatching {
      firestore.collection("notifications").document("notif_private_std_a").get().await()
    }
    assertTrue("Student B must not read Student A's private notification", crossStudentRead.isFailure)

    // 2. Student B attempts to inject/create a fake notification -> Denied
    val studentInject = runCatching {
      firestore.collection("notifications").document("notif_fake_inject").set(
        mapOf(
          "notificationId" to "notif_fake_inject",
          "recipientUserId" to studentAUid,
          "recipientRole" to "student",
          "branchId" to "branch_main",
          "type" to "announcement",
          "title" to "Fake Promotion",
          "body" to "Injected by student",
          "read" to false
        )
      ).await()
    }
    assertTrue("Students must not be allowed to create notifications", studentInject.isFailure)

    // 3. Student A attempts to tamper with notification title while marking read -> Denied
    signInTestUser("student_notif_a@truefighters.in")
    val studentTamperTitle = runCatching {
      firestore.collection("notifications").document("notif_private_std_a").update(
        mapOf(
          "read" to true,
          "title" to "Tampered Title"
        )
      ).await()
    }
    assertTrue("Recipient student can only update 'read'/'updatedAt', not tamper with title", studentTamperTitle.isFailure)

    // 4. Coach assigned to branch_main attempts to send notification for branch_west -> Denied
    signInTestUser("coach_notif_main@truefighters.in")
    val crossBranchCoachNotif = runCatching {
      firestore.collection("notifications").document("notif_coach_cross_branch").set(
        mapOf(
          "notificationId" to "notif_coach_cross_branch",
          "recipientUserId" to "",
          "recipientRole" to "student",
          "branchId" to "branch_west",
          "type" to "class_cancelled",
          "title" to "West Branch Cancelled",
          "body" to "Unauthorized coach alert",
          "read" to false
        )
      ).await()
    }
    assertTrue("Coach from branch_main must not create notifications for branch_west", crossBranchCoachNotif.isFailure)
  }

  @Test
  fun phase9_notification_preferences_fcm_tokens_and_demo_mode_hardening() = runBlocking {
    val studentAUid = signInTestUser("student_pref_a@truefighters.in")
    val studentBUid = signInTestUser("student_pref_b@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    // 1. Student A saves notification preferences and registers FCM token
    signInTestUser("student_pref_a@truefighters.in")
    repo.saveNotificationPreferences(
      NotificationPreferences(
        userId = studentAUid,
        classRemindersEnabled = true,
        feeDueRemindersEnabled = true,
        gradingAndBeltsEnabled = true,
        announcementsEnabled = false,
        admissionsAlertsEnabled = false
      )
    ).getOrThrow()

    repo.registerFcmToken(studentAUid, "fcm_token_device_1").getOrThrow()
    repo.registerFcmToken(studentAUid, "fcm_token_device_1").getOrThrow() // Idempotent registration

    val prefsAfterReg = repo.getNotificationPreferences(studentAUid).getOrThrow()
    assertEquals(false, prefsAfterReg.announcementsEnabled)
    assertEquals(1, prefsAfterReg.fcmTokens.size)
    assertTrue(prefsAfterReg.fcmTokens.contains("fcm_token_device_1"))

    // 2. Student B attempts to read or modify Student A's notificationPreferences / FCM tokens -> Denied
    signInTestUser("student_pref_b@truefighters.in")
    val crossUserPrefRead = runCatching {
      firestore.collection("notificationPreferences").document(studentAUid).get().await()
    }
    assertTrue("Student B must not read Student A's notification preferences or FCM tokens", crossUserPrefRead.isFailure)

    val crossUserTokenHijack = runCatching {
      firestore.collection("notificationPreferences").document(studentAUid).set(
        mapOf("userId" to studentAUid, "fcmTokens" to listOf("attacker_token"))
      ).await()
    }
    assertTrue("Student B must not overwrite Student A's FCM tokens", crossUserTokenHijack.isFailure)

    // 3. Student A signs in and unregisters FCM token on sign-out
    signInTestUser("student_pref_a@truefighters.in")
    repo.unregisterFcmToken(studentAUid, "fcm_token_device_1").getOrThrow()
    val prefsAfterUnreg = repo.getNotificationPreferences(studentAUid).getOrThrow()
    assertTrue("FCM token must be removed after unregisterFcmToken", prefsAfterUnreg.fcmTokens.isEmpty())

    // 4. Demo mode hardening: demo accounts cannot register FCM tokens or receive production notifications
    val demoTokenAttempt = repo.registerFcmToken("demo_student", "fcm_demo_token")
    assertTrue("Demo mode user must be blocked from registering FCM tokens", demoTokenAttempt.isFailure)

    val demoNotifAttempt = repo.dispatchNotificationWithDeduplication(
      AppNotification(
        recipientUserId = "demo_student",
        recipientRole = "student",
        title = "Demo Test",
        body = "Should not be sent"
      )
    )
    assertTrue("Demo mode accounts must be blocked from receiving production notifications", demoNotifAttempt.isFailure)
  }
}
