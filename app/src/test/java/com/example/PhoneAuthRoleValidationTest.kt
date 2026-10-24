package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.RoleValidationResult
import com.example.data.model.UserProfile
import com.example.data.repository.TrueFightersRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAuthRoleValidationTest : FirestoreEmulatorTestBase() {

  @Test
  fun unregisteredStudentPhone_requiresAdmissionRegistration() = runBlocking {
    val uid = signInTestUser("new_applicant_phone@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = uid,
        selectedRole = "student",
        phoneNumber = "+919876543299"
      )
    }

    assertTrue("Unregistered student should be prompted for admission registration", result is RoleValidationResult.NeedsRegistration)
  }

  @Test
  fun studentPhone_canProvisionStudentProfile() = runBlocking {
    val uid = signInTestUser("student_phone_test@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    repo.saveUserProfile(
      UserProfile(
        userId = uid,
        displayName = "Approved Student",
        phoneNumber = "+919876543210",
        role = "student",
        status = "active",
        active = true
      )
    )

    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = uid,
        selectedRole = "student",
        phoneNumber = "+919876543210"
      )
    }

    assertTrue("Validation should succeed for student", result is RoleValidationResult.Success)
    val success = result as RoleValidationResult.Success
    assertEquals("student", success.effectiveRole)
  }

  @Test
  fun wrongRoleLogin_studentAttemptingAdminRole_isDenied() = runBlocking {
    val uid = signInTestUser("student_sneaky@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    // Save student profile
    repo.saveUserProfile(
      UserProfile(
        userId = uid,
        displayName = "Regular Student",
        phoneNumber = "+919876543211",
        role = "student",
        active = true
      )
    )

    // Student selects "admin" login card
    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = uid,
        selectedRole = "admin",
        phoneNumber = "+919876543211"
      )
    }

    assertTrue("Student attempting admin login must be denied", result is RoleValidationResult.RoleMismatch)
    val mismatch = result as RoleValidationResult.RoleMismatch
    assertEquals(
      "This phone number is not authorized for the selected role. Please select your assigned role.",
      mismatch.message
    )
  }

  @Test
  fun wrongRoleLogin_studentAttemptingCoachRole_isDenied() = runBlocking {
    val uid = signInTestUser("student_to_coach@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    repo.saveUserProfile(
      UserProfile(
        userId = uid,
        displayName = "Karate Kid",
        phoneNumber = "+919876543212",
        role = "student",
        active = true
      )
    )

    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = uid,
        selectedRole = "coach",
        phoneNumber = "+919876543212"
      )
    }

    assertTrue("Student attempting coach login must be denied", result is RoleValidationResult.RoleMismatch)
    val mismatch = result as RoleValidationResult.RoleMismatch
    assertEquals(
      "This phone number is not authorized for the selected role. Please select your assigned role.",
      mismatch.message
    )
  }

  @Test
  fun unverifiedPhone_attemptingAdminRole_isDenied() = runBlocking {
    val uid = signInTestUser("unverified_admin_test@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = uid,
        selectedRole = "admin",
        phoneNumber = "+919999988888"
      )
    }

    assertTrue("Unregistered phone attempting admin role must be denied", result is RoleValidationResult.RoleMismatch)
    val mismatch = result as RoleValidationResult.RoleMismatch
    assertEquals(
      "This phone number is not authorized for the selected role. Please select your assigned role.",
      mismatch.message
    )
  }

  @Test
  fun verifiedAdminPhone_canLoginAsAdmin() = runBlocking {
    val uid = signInTestUser("master_admin@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = uid,
        selectedRole = "admin",
        phoneNumber = "+919876500001"
      )
    }

    assertTrue("Verified admin phone must be authorized", result is RoleValidationResult.Success)
    val success = result as RoleValidationResult.Success
    assertEquals("admin", success.effectiveRole)
  }

  @Test
  fun inactiveAccount_isDeniedWithDeactivatedMessage() = runBlocking {
    val uid = signInTestUser("suspended_member@truefighters.in")
    val repo = TrueFightersRepository(firestore)

    repo.saveUserProfile(
      UserProfile(
        userId = uid,
        displayName = "Suspended Student",
        phoneNumber = "+919876543219",
        role = "student",
        status = "inactive",
        active = false
      )
    )

    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = uid,
        selectedRole = "student",
        phoneNumber = "+919876543219"
      )
    }

    assertTrue("Inactive account must be rejected", result is RoleValidationResult.AccountInactive)
    val inactive = result as RoleValidationResult.AccountInactive
    assertTrue(inactive.message.contains("deactivated", ignoreCase = true))
  }

  @Test
  fun provisionedCoach_canLoginAsCoach() = runBlocking {
    val coachUid = signInTestUser("master_coach@truefighters.in")

    // Admin provisions the coach profile first
    val adminUid = signInTestUser("Mahi27d@gmail.com")
    val repo = TrueFightersRepository(firestore)
    repo.ensureAdminRecordMigrated(adminUid, "Mahi27d@gmail.com", "+919876500001")

    val provisionRes = repo.provisionCoach(
      UserProfile(
        userId = coachUid,
        displayName = "Master Sensei",
        phoneNumber = "+919876500002",
        role = "coach",
        status = "active",
        active = true,
        branchIds = listOf("branch_main")
      )
    )
    assertTrue("Admin coach provisioning must succeed: ${provisionRes.exceptionOrNull()}", provisionRes.isSuccess)

    // Coach signs in with phone OTP and resolves role
    signInTestUser("master_coach@truefighters.in")
    val result = withTimeout(5000L) {
      repo.validateAndResolveUserRole(
        userId = coachUid,
        selectedRole = "coach",
        phoneNumber = "+919876500002"
      )
    }

    assertTrue("Provisioned coach must be authorized", result is RoleValidationResult.Success)
    val success = result as RoleValidationResult.Success
    assertEquals("coach", success.effectiveRole)
  }
}
