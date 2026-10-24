package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TrueFightersRepository(val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = com.google.firebase.auth.FirebaseAuth.getInstance(db.app)
    private val TAG = "TrueFightersRepo"

    fun currentUserId(): String? = auth.currentUser?.uid
    fun currentUserEmail(): String? = auth.currentUser?.email

    // User Profile
    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val docRef = db.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeUserProfile error: ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun getUserProfile(userId: String): UserProfile? = runCatching {
        val doc = db.collection("users").document(userId).get().await()
        if (doc.exists()) doc.toObject(UserProfile::class.java) else null
    }.getOrNull()

    suspend fun ensureAdminRecordMigrated(
        userId: String,
        email: String?,
        phoneNumber: String?
    ): AdminRecord? {
        val existingAdminDoc = runCatching {
            db.collection("admins").document(userId).get().await()
        }.getOrNull()

        if (existingAdminDoc != null && existingAdminDoc.exists()) {
            val record = existingAdminDoc.toObject(AdminRecord::class.java)
            return if (record?.active != false) record else null
        }

        // Safe administrator-provisioning migration: migrate legacy root admin into /admins/{userId}
        val normalizedPhone = phoneNumber?.replace("\\s+".toRegex(), "")
        val isBootstrapAdmin = email?.equals("Mahi27d@gmail.com", ignoreCase = true) == true || normalizedPhone == "+919876500001"
        if (isBootstrapAdmin) {
            val migratedRecord = AdminRecord(
                id = userId,
                userId = userId,
                displayName = "Master Administrator",
                email = email ?: "",
                phoneNumber = normalizedPhone ?: "",
                scope = "global",
                branchIds = emptyList(),
                active = true,
                provisionedBy = "system_migration"
            )
            runCatching {
                db.collection("admins").document(userId).set(
                    mapOf(
                        "userId" to userId,
                        "displayName" to migratedRecord.displayName,
                        "email" to migratedRecord.email,
                        "phoneNumber" to migratedRecord.phoneNumber,
                        "scope" to migratedRecord.scope,
                        "branchIds" to migratedRecord.branchIds,
                        "active" to true,
                        "provisionedBy" to migratedRecord.provisionedBy,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
            return migratedRecord
        }
        return null
    }

    suspend fun provisionAdmin(admin: AdminRecord): Result<Unit> = runCatching {
        val uid = admin.userId.ifBlank { admin.id }
        if (uid.isBlank()) throw IllegalArgumentException("Admin userId is required.")
        val scope = if (admin.branchIds.isEmpty()) "global" else admin.scope.ifBlank { "branch" }
        db.collection("admins").document(uid).set(
            mapOf(
                "userId" to uid,
                "displayName" to admin.displayName.trim(),
                "email" to admin.email.trim(),
                "phoneNumber" to admin.phoneNumber.trim(),
                "scope" to scope,
                "branchIds" to admin.branchIds,
                "active" to admin.active,
                "provisionedBy" to (auth.currentUser?.uid ?: "admin"),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        db.collection("users").document(uid).set(
            mapOf(
                "userId" to uid,
                "displayName" to admin.displayName.trim(),
                "email" to admin.email.trim(),
                "phoneNumber" to admin.phoneNumber.trim(),
                "role" to "admin",
                "status" to if (admin.active) "active" else "inactive",
                "active" to admin.active,
                "branchIds" to admin.branchIds,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun validateAndResolveUserRole(
        userId: String,
        selectedRole: String,
        phoneNumber: String?
    ): RoleValidationResult {
        val targetRole = selectedRole.lowercase().trim()
        val adminRecord = ensureAdminRecordMigrated(userId, currentUserEmail(), phoneNumber)
        val isVerifiedAdmin = adminRecord != null

        val userDoc = runCatching {
            db.collection("users").document(userId).get().await()
        }.getOrNull()

        val existingProfile = if (userDoc != null && userDoc.exists()) {
            userDoc.toObject(UserProfile::class.java)
        } else null

        if (existingProfile != null) {
            val status = existingProfile.status.lowercase().trim()
            if (status == "suspended") {
                return RoleValidationResult.AccountSuspended(
                    "This account has been suspended. Please contact True Fighters Martial Arts Academy administration."
                )
            }

            val actualRole = if (isVerifiedAdmin) "admin" else existingProfile.role.lowercase().trim()

            if (actualRole != targetRole) {
                return RoleValidationResult.RoleMismatch(
                    actualRole,
                    "This phone number is not authorized for the selected role. Please select your assigned role."
                )
            }

            if (actualRole == "student" && status == "pending") {
                return RoleValidationResult.RegistrationPending(
                    existingProfile,
                    "Your student registration request has been submitted and is currently pending academy approval. Our team will verify and activate your student account shortly."
                )
            }

            if (status == "inactive" || !existingProfile.active) {
                return RoleValidationResult.AccountInactive(
                    "This account has been deactivated. Please contact True Fighters Martial Arts Academy administration."
                )
            }

            val resolvedBranches = if (isVerifiedAdmin && adminRecord != null && adminRecord.branchIds.isNotEmpty()) {
                adminRecord.branchIds
            } else {
                existingProfile.branchIds
            }
            return RoleValidationResult.Success(
                existingProfile.copy(role = actualRole, branchIds = resolvedBranches),
                actualRole
            )
        }

        // Profile does not exist yet:
        if (isVerifiedAdmin) {
            if (targetRole == "admin") {
                val adminProfile = UserProfile(
                    userId = userId,
                    displayName = adminRecord?.displayName?.ifBlank { "Master Administrator" } ?: "Master Administrator",
                    phoneNumber = phoneNumber ?: "",
                    role = "admin",
                    status = "active",
                    branchIds = adminRecord?.branchIds ?: emptyList(),
                    active = true
                )
                saveUserProfile(adminProfile)
                return RoleValidationResult.Success(adminProfile, "admin")
            } else {
                return RoleValidationResult.RoleMismatch(
                    "admin",
                    "This phone number is not authorized for the selected role. Please select your assigned role."
                )
            }
        }

        if (targetRole == "student") {
            // Student phone verified but needs to complete registration request
            return RoleValidationResult.NeedsRegistration(
                phoneNumber = phoneNumber ?: "",
                userId = userId
            )
        }

        // Coaches and Admins cannot self-provision
        return RoleValidationResult.RoleMismatch(
            "unregistered",
            "This phone number is not authorized for the selected role. Please select your assigned role."
        )
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: profile.userId
        val docRef = db.collection("users").document(uid)
        val data = mapOf(
            "userId" to uid,
            "displayName" to profile.displayName,
            "email" to profile.email,
            "phoneNumber" to profile.phoneNumber,
            "role" to profile.role,
            "status" to profile.status,
            "branchIds" to profile.branchIds,
            "batchIds" to profile.batchIds,
            "active" to profile.active,
            "isMinor" to profile.isMinor,
            "dateOfBirth" to profile.dateOfBirth,
            "guardianName" to profile.guardianName,
            "guardianPhone" to profile.guardianPhone,
            "guardianConsent" to profile.guardianConsent,
            "preferredBranchId" to profile.preferredBranchId,
            "preferredBranchName" to profile.preferredBranchName,
            "programName" to profile.programName,
            "createdAt" to (profile.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data, SetOptions.merge()).await()
    }

    suspend fun submitStudentRegistration(
        userId: String,
        phoneNumber: String,
        fullName: String,
        dateOfBirth: String,
        isMinor: Boolean,
        guardianName: String,
        guardianPhone: String,
        guardianConsent: Boolean,
        preferredBranchId: String,
        preferredBranchName: String,
        programName: String,
        email: String = "",
        programId: String = "prog_karate"
    ): Result<UserProfile> = runCatching {
        val trimmedName = fullName.trim()
        val trimmedPhone = phoneNumber.trim()
        val trimmedDob = dateOfBirth.trim()
        if (trimmedName.isBlank()) throw IllegalArgumentException("Student full name is required.")
        if (trimmedPhone.length < 7) throw IllegalArgumentException("Valid verified phone number is required.")
        if (trimmedDob.isBlank()) throw IllegalArgumentException("Date of birth is required.")
        if (preferredBranchId.isBlank()) throw IllegalArgumentException("Preferred active branch is required.")
        if (programName.isBlank()) throw IllegalArgumentException("Martial arts program is required.")
        if (isMinor) {
            if (guardianName.isBlank() || guardianPhone.isBlank()) {
                throw IllegalArgumentException("Guardian name and phone are required for minor students.")
            }
            if (!guardianConsent) {
                throw IllegalArgumentException("Guardian consent is required for minor students.")
            }
        }

        val admissionReq = AdmissionRequest(
            id = "adm_$userId",
            userId = userId,
            fullName = trimmedName,
            email = email.trim(),
            phoneNumber = trimmedPhone,
            dateOfBirth = trimmedDob,
            isMinor = isMinor,
            programId = programId,
            programName = programName.trim(),
            preferredBranchId = preferredBranchId.trim(),
            preferredBranchName = preferredBranchName.trim(),
            guardianName = guardianName.trim(),
            guardianPhone = guardianPhone.trim(),
            guardianConsent = guardianConsent,
            status = "pending"
        )
        // Validate branch & duplicate check before saving user profile
        submitAdmissionRequest(admissionReq).getOrThrow()

        val profile = UserProfile(
            userId = userId,
            displayName = trimmedName,
            email = email.trim(),
            phoneNumber = trimmedPhone,
            role = "student",
            status = "pending",
            active = false,
            isMinor = isMinor,
            dateOfBirth = trimmedDob,
            guardianName = guardianName.trim(),
            guardianPhone = guardianPhone.trim(),
            guardianConsent = guardianConsent,
            preferredBranchId = preferredBranchId.trim(),
            preferredBranchName = preferredBranchName.trim(),
            programName = programName.trim(),
            branchIds = listOf(preferredBranchId.trim())
        )
        saveUserProfile(profile).getOrThrow()

        profile
    }

    // Coaches Management
    fun observeCoaches(): Flow<List<UserProfile>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("role", "coach")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(UserProfile::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun provisionCoach(coach: UserProfile): Result<Unit> = runCatching {
        val uid = if (coach.userId.isNotBlank()) coach.userId else "coach_${UUID.randomUUID().toString().take(8)}"
        val docRef = db.collection("users").document(uid)
        val data = mapOf(
            "userId" to uid,
            "displayName" to coach.displayName,
            "email" to coach.email,
            "phoneNumber" to coach.phoneNumber,
            "role" to "coach",
            "status" to "active",
            "active" to true,
            "branchIds" to coach.branchIds,
            "batchIds" to coach.batchIds,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data, SetOptions.merge()).await()
    }

    // Branches
    fun observeBranches(includeInactive: Boolean = true): Flow<List<Branch>> = callbackFlow {
        val query = if (includeInactive) {
            db.collection("branches")
        } else {
            db.collection("branches").whereEqualTo("active", true)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeBranches error: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(Branch::class.java) ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun addBranch(branch: Branch): Result<String> = runCatching {
        val codeToCheck = branch.effectiveCode.trim().uppercase()
        if (codeToCheck.isBlank()) {
            throw IllegalArgumentException("Branch code is required.")
        }
        val nameToCheck = branch.effectiveName.trim()
        if (nameToCheck.isBlank()) {
            throw IllegalArgumentException("Branch name is required.")
        }

        // Duplicate branch-code prevention
        val snap1 = db.collection("branches").whereEqualTo("code", codeToCheck).get().await()
        val snap2 = db.collection("branches").whereEqualTo("branchCode", codeToCheck).get().await()
        if (!snap1.isEmpty || !snap2.isEmpty) {
            throw IllegalArgumentException("Branch code '$codeToCheck' is already in use by another branch.")
        }

        val id = if (branch.effectiveId.isNotBlank()) branch.effectiveId else "branch_${UUID.randomUUID().toString().take(8)}"
        val phone = branch.effectivePhone.trim()
        val status = if (branch.status.isNotBlank()) branch.status.lowercase() else if (branch.active) "active" else "inactive"
        val isActive = status == "active"

        val data = mapOf(
            "branchId" to id,
            "name" to nameToCheck,
            "branchName" to nameToCheck,
            "code" to codeToCheck,
            "branchCode" to codeToCheck,
            "address" to branch.address.trim(),
            "city" to branch.city.trim(),
            "state" to branch.state.trim(),
            "postalCode" to branch.postalCode.trim(),
            "phone" to phone,
            "contactPhone" to phone,
            "status" to status,
            "active" to isActive,
            "createdBy" to (auth.currentUser?.uid ?: "admin"),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("branches").document(id).set(data).await()
        id
    }

    suspend fun updateBranch(branch: Branch): Result<Unit> = runCatching {
        val branchId = branch.effectiveId
        if (branchId.isBlank()) {
            throw IllegalArgumentException("Branch ID is required for updating branch details.")
        }
        val codeToCheck = branch.effectiveCode.trim().uppercase()
        val nameToCheck = branch.effectiveName.trim()
        if (nameToCheck.isBlank()) {
            throw IllegalArgumentException("Branch name cannot be empty.")
        }

        // Verify duplicate branch-code does not collide with other branches
        if (codeToCheck.isNotBlank()) {
            val snap1 = db.collection("branches").whereEqualTo("code", codeToCheck).get().await()
            val snap2 = db.collection("branches").whereEqualTo("branchCode", codeToCheck).get().await()
            val collisions = (snap1.documents + snap2.documents).filter { it.id != branchId }
            if (collisions.isNotEmpty()) {
                throw IllegalArgumentException("Branch code '$codeToCheck' is already registered for another branch.")
            }
        }

        val phone = branch.effectivePhone.trim()
        val status = if (branch.status.isNotBlank()) branch.status.lowercase() else if (branch.active) "active" else "inactive"
        val isActive = status == "active"

        val updates = mapOf(
            "name" to nameToCheck,
            "branchName" to nameToCheck,
            "code" to codeToCheck,
            "branchCode" to codeToCheck,
            "address" to branch.address.trim(),
            "city" to branch.city.trim(),
            "state" to branch.state.trim(),
            "postalCode" to branch.postalCode.trim(),
            "phone" to phone,
            "contactPhone" to phone,
            "status" to status,
            "active" to isActive,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("branches").document(branchId).update(updates).await()
    }

    suspend fun setBranchStatus(branchId: String, status: String): Result<Unit> = runCatching {
        val normalized = status.lowercase().trim()
        val isActive = normalized == "active"
        db.collection("branches").document(branchId).update(
            mapOf(
                "status" to normalized,
                "active" to isActive,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun assignCoachBranches(coachUserId: String, branchIds: List<String>): Result<Unit> = runCatching {
        db.collection("users").document(coachUserId).update(
            mapOf(
                "branchIds" to branchIds,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun assignStudentBranch(
        studentId: String,
        userId: String,
        branchId: String,
        branchName: String
    ): Result<Unit> = runCatching {
        if (branchId.isBlank()) {
            throw IllegalArgumentException("Target branch ID is required.")
        }
        val branchDoc = db.collection("branches").document(branchId).get().await()
        if (branchDoc.exists()) {
            val targetBranch = branchDoc.toObject(Branch::class.java)
            if (targetBranch != null && !targetBranch.isBranchActive) {
                throw IllegalArgumentException("Cannot transfer student to inactive branch '${targetBranch.effectiveName}'.")
            }
        }

        if (studentId.isNotBlank()) {
            db.collection("students").document(studentId).update(
                mapOf(
                    "branchId" to branchId,
                    "branchName" to branchName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        if (userId.isNotBlank()) {
            val userRef = db.collection("users").document(userId)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "branchIds" to listOf(branchId),
                        "preferredBranchId" to branchId,
                        "preferredBranchName" to branchName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        recordAuditLog(
            action = "STUDENT_BRANCH_TRANSFER",
            targetType = "student",
            targetId = studentId.ifBlank { userId },
            branchId = branchId,
            details = "Transferred student to branch $branchName ($branchId)"
        )
    }

    suspend fun updateStudentProfile(student: Student): Result<Unit> = runCatching {
        if (student.id.isBlank()) {
            throw IllegalArgumentException("Student ID is required to update profile.")
        }
        val trimmedName = student.fullName.trim()
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Student full name cannot be blank.")
        }
        if (student.isMinor && (student.guardianName.isBlank() || student.guardianPhone.isBlank())) {
            throw IllegalArgumentException("Guardian name and phone are required for minor students.")
        }

        val updates = mapOf(
            "fullName" to trimmedName,
            "phoneNumber" to student.phoneNumber.trim(),
            "email" to student.email.trim(),
            "dateOfBirth" to student.dateOfBirth.trim(),
            "isMinor" to student.isMinor,
            "guardianName" to student.guardianName.trim(),
            "guardianPhone" to student.guardianPhone.trim(),
            "guardianConsent" to student.guardianConsent,
            "programId" to student.programId,
            "programName" to student.programName,
            "batchId" to student.batchId,
            "batchName" to student.batchName,
            "currentBelt" to student.currentBelt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("students").document(student.id).update(updates).await()

        if (student.userId.isNotBlank()) {
            val userRef = db.collection("users").document(student.userId)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "displayName" to trimmedName,
                        "phoneNumber" to student.phoneNumber.trim(),
                        "email" to student.email.trim(),
                        "dateOfBirth" to student.dateOfBirth.trim(),
                        "isMinor" to student.isMinor,
                        "guardianName" to student.guardianName.trim(),
                        "guardianPhone" to student.guardianPhone.trim(),
                        "guardianConsent" to student.guardianConsent,
                        "programName" to student.programName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        recordAuditLog(
            action = "STUDENT_PROFILE_UPDATED",
            targetType = "student",
            targetId = student.id,
            branchId = student.branchId,
            details = "Updated student profile for $trimmedName"
        )
    }

    suspend fun setStudentStatus(
        studentId: String,
        userId: String,
        status: String,
        reason: String = ""
    ): Result<Unit> = runCatching {
        val normalized = status.lowercase().trim()
        if (normalized !in listOf("active", "suspended", "inactive")) {
            throw IllegalArgumentException("Invalid student status '$status'.")
        }
        var targetBranchId = ""
        if (studentId.isNotBlank()) {
            val stdRef = db.collection("students").document(studentId)
            val stdSnap = stdRef.get().await()
            if (stdSnap.exists()) {
                targetBranchId = stdSnap.getString("branchId") ?: ""
                stdRef.update(
                    mapOf(
                        "status" to normalized,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        if (userId.isNotBlank()) {
            val userRef = db.collection("users").document(userId)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                val userStatus = if (normalized == "active") "approved" else normalized
                val isActive = normalized == "active"
                userRef.update(
                    mapOf(
                        "status" to userStatus,
                        "active" to isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        recordAuditLog(
            action = "STUDENT_STATUS_${normalized.uppercase()}",
            targetType = "student",
            targetId = studentId.ifBlank { userId },
            branchId = targetBranchId,
            details = "Changed student status to $normalized. ${if (reason.isNotBlank()) "Reason: $reason" else ""}".trim()
        )
    }

    // Programs
    fun observePrograms(branchId: String? = null, includeInactive: Boolean = false): Flow<List<Program>> = callbackFlow {
        var query: Query = db.collection("programs")
        if (!includeInactive) {
            query = query.whereEqualTo("active", true)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(Program::class.java) ?: emptyList()
            val filtered = if (!branchId.isNullOrBlank()) {
                list.filter { p -> p.branchIds.isEmpty() || p.branchIds.contains(branchId) }
            } else {
                list
            }
            trySend(filtered)
        }
        awaitClose { listener.remove() }
    }

    suspend fun getProgram(programId: String): Result<Program?> = runCatching {
        if (programId.isBlank()) return@runCatching null
        val snap = db.collection("programs").document(programId).get().await()
        if (snap.exists()) snap.toObject(Program::class.java) else null
    }

    suspend fun createOrUpdateProgram(program: Program): Result<String> = runCatching {
        val trimmedName = program.effectiveName.trim()
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Program name cannot be blank.")
        }
        val id = if (program.id.isNotBlank()) program.id else if (program.programId.isNotBlank()) program.programId else "prog_${UUID.randomUUID().toString().take(8)}"

        // If specific branchIds are provided, verify branches exist and are active
        if (program.branchIds.isNotEmpty()) {
            for (bId in program.branchIds) {
                if (bId.isNotBlank()) {
                    val branchSnap = db.collection("branches").document(bId).get().await()
                    if (branchSnap.exists()) {
                        val branchObj = branchSnap.toObject(Branch::class.java)
                        if (branchObj != null && !branchObj.isBranchActive) {
                            throw IllegalArgumentException("Cannot associate program with inactive branch '${branchObj.effectiveName}'.")
                        }
                    }
                }
            }
        }

        val creator = program.createdBy.ifBlank {
            auth.currentUser?.email ?: auth.currentUser?.uid ?: "admin"
        }
        val data = mapOf(
            "programId" to id,
            "name" to trimmedName,
            "programName" to trimmedName,
            "description" to program.description.trim(),
            "discipline" to program.discipline.trim(),
            "level" to program.level.trim().ifBlank { "All Levels" },
            "category" to program.category.trim(),
            "branchIds" to program.branchIds,
            "status" to program.status.trim().ifBlank { "active" },
            "active" to (program.status.lowercase() != "inactive" && program.active),
            "createdBy" to creator,
            "createdAt" to (program.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("programs").document(id).set(data, SetOptions.merge()).await()
        recordAuditLog(
            action = if (program.id.isNotBlank()) "PROGRAM_UPDATED" else "PROGRAM_CREATED",
            targetType = "program",
            targetId = id,
            branchId = program.branchIds.firstOrNull() ?: "",
            details = "Program '$trimmedName' (${program.discipline}) saved."
        )
        id
    }

    suspend fun setProgramStatus(programId: String, status: String): Result<Unit> = runCatching {
        val normalized = status.lowercase().trim()
        if (normalized !in listOf("active", "inactive")) {
            throw IllegalArgumentException("Invalid program status '$status'. Must be active or inactive.")
        }
        val isActive = normalized == "active"
        db.collection("programs").document(programId).update(
            mapOf(
                "status" to normalized,
                "active" to isActive,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        recordAuditLog(
            action = "PROGRAM_STATUS_CHANGED",
            targetType = "program",
            targetId = programId,
            branchId = "",
            details = "Program status set to $normalized"
        )
    }

    // Batches
    fun observeBatches(
        branchId: String? = null,
        programId: String? = null,
        includeInactive: Boolean = false
    ): Flow<List<Batch>> = callbackFlow {
        var query: Query = db.collection("batches")
        if (!includeInactive) {
            query = query.whereEqualTo("active", true)
        }
        if (!branchId.isNullOrBlank()) {
            query = query.whereEqualTo("branchId", branchId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(Batch::class.java) ?: emptyList()
            val filtered = if (!programId.isNullOrBlank()) {
                list.filter { it.programId == programId }
            } else {
                list
            }
            trySend(filtered)
        }
        awaitClose { listener.remove() }
    }

    suspend fun getBatch(batchId: String): Result<Batch?> = runCatching {
        if (batchId.isBlank()) return@runCatching null
        val snap = db.collection("batches").document(batchId).get().await()
        if (snap.exists()) snap.toObject(Batch::class.java) else null
    }

    suspend fun createOrUpdateBatch(batch: Batch): Result<String> = runCatching {
        val trimmedName = batch.effectiveName.trim()
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Batch name cannot be blank.")
        }
        if (batch.branchId.isBlank()) {
            throw IllegalArgumentException("Batch requires a valid branch assignment.")
        }
        // Validate branch is active
        val branchDoc = db.collection("branches").document(batch.branchId).get().await()
        if (!branchDoc.exists()) {
            throw IllegalArgumentException("Branch '${batch.branchId}' does not exist.")
        }
        val branchObj = branchDoc.toObject(Branch::class.java)
        if (branchObj != null && !branchObj.isBranchActive) {
            throw IllegalArgumentException("Cannot create or assign batch to inactive branch '${branchObj.effectiveName}'.")
        }

        // Validate program if specified
        if (batch.programId.isNotBlank()) {
            val progDoc = db.collection("programs").document(batch.programId).get().await()
            if (progDoc.exists()) {
                val progObj = progDoc.toObject(Program::class.java)
                if (progObj != null && !progObj.isProgramActive) {
                    throw IllegalArgumentException("Cannot assign batch to inactive program '${progObj.effectiveName}'.")
                }
            }
        }

        // Validate coach authorization for that branch
        val effectiveCoaches = batch.effectiveCoachIds
        for (coachId in effectiveCoaches) {
            if (coachId.isNotBlank()) {
                val coachDoc = db.collection("users").document(coachId).get().await()
                if (coachDoc.exists()) {
                    val coachObj = coachDoc.toObject(UserProfile::class.java)
                    if (coachObj != null && coachObj.role == "coach" && !coachObj.branchIds.contains(batch.branchId)) {
                        throw IllegalArgumentException("Coach '$coachId' (${coachObj.displayName}) is not authorized for branch '${batch.branchId}'.")
                    }
                }
            }
        }

        val id = if (batch.id.isNotBlank()) batch.id else if (batch.batchId.isNotBlank()) batch.batchId else "batch_${UUID.randomUUID().toString().take(8)}"
        val finalCapacity = if (batch.capacity > 0) batch.capacity else 30L
        val data = mapOf(
            "batchId" to id,
            "branchId" to batch.branchId,
            "branchName" to batch.branchName.ifBlank { branchObj?.effectiveName ?: "" },
            "programId" to batch.programId,
            "programName" to batch.programName,
            "coachId" to (batch.coachId.ifBlank { effectiveCoaches.firstOrNull() ?: "" }),
            "coachName" to batch.coachName,
            "coachIds" to effectiveCoaches,
            "coachNames" to batch.coachNames,
            "studentIds" to batch.studentIds,
            "name" to trimmedName,
            "batchName" to trimmedName,
            "schedule" to batch.schedule.trim(),
            "capacity" to finalCapacity,
            "status" to batch.status.trim().ifBlank { "active" },
            "active" to (batch.status.lowercase() != "inactive" && batch.active),
            "createdAt" to (batch.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("batches").document(id).set(data, SetOptions.merge()).await()
        recordAuditLog(
            action = if (batch.id.isNotBlank()) "BATCH_UPDATED" else "BATCH_CREATED",
            targetType = "batch",
            targetId = id,
            branchId = batch.branchId,
            details = "Batch '$trimmedName' in ${batch.branchName} saved."
        )
        id
    }

    suspend fun setBatchStatus(batchId: String, status: String): Result<Unit> = runCatching {
        val normalized = status.lowercase().trim()
        if (normalized !in listOf("active", "inactive")) {
            throw IllegalArgumentException("Invalid batch status '$status'. Must be active or inactive.")
        }
        val isActive = normalized == "active"
        db.collection("batches").document(batchId).update(
            mapOf(
                "status" to normalized,
                "active" to isActive,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        recordAuditLog(
            action = "BATCH_STATUS_CHANGED",
            targetType = "batch",
            targetId = batchId,
            branchId = "",
            details = "Batch status set to $normalized"
        )
    }

    suspend fun manageBatchStudent(
        batchId: String,
        studentId: String,
        action: String, // "add", "remove", "transfer"
        targetBatchId: String? = null
    ): Result<Unit> = runCatching {
        if (batchId.isBlank() || studentId.isBlank()) {
            throw IllegalArgumentException("batchId and studentId are required.")
        }
        val batchRef = db.collection("batches").document(batchId)
        val batchSnap = batchRef.get().await()
        if (!batchSnap.exists()) {
            throw IllegalArgumentException("Batch '$batchId' not found.")
        }
        val batch = batchSnap.toObject(Batch::class.java)
            ?: throw IllegalArgumentException("Failed to parse batch '$batchId'.")

        val studentRef = db.collection("students").document(studentId)
        val studentSnap = studentRef.get().await()
        if (!studentSnap.exists()) {
            throw IllegalArgumentException("Student '$studentId' not found.")
        }
        val student = studentSnap.toObject(Student::class.java)
            ?: throw IllegalArgumentException("Failed to parse student '$studentId'.")

        if (student.status.lowercase() == "suspended") {
            throw IllegalStateException("Suspended student cannot be added to or modified in a batch.")
        }

        when (action.lowercase().trim()) {
            "add" -> {
                if (batch.studentIds.contains(studentId)) {
                    // Already in batch, idempotent return
                    return@runCatching
                }
                if (batch.enrolledCount >= batch.capacity) {
                    throw IllegalStateException("Batch '${batch.effectiveName}' is at maximum capacity (${batch.capacity}).")
                }
                batchRef.update(
                    mapOf(
                        "studentIds" to FieldValue.arrayUnion(studentId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                studentRef.update(
                    mapOf(
                        "batchId" to batchId,
                        "batchName" to batch.effectiveName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (student.userId.isNotBlank()) {
                    val userRef = db.collection("users").document(student.userId)
                    val userSnap = userRef.get().await()
                    if (userSnap.exists()) {
                        userRef.update(
                            mapOf(
                                "batchIds" to FieldValue.arrayUnion(batchId),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                recordAuditLog(
                    action = "BATCH_STUDENT_ADDED",
                    targetType = "batch",
                    targetId = batchId,
                    branchId = batch.branchId,
                    details = "Added student ${student.fullName} ($studentId) to batch ${batch.effectiveName}"
                )
            }
            "remove" -> {
                batchRef.update(
                    mapOf(
                        "studentIds" to FieldValue.arrayRemove(studentId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                studentRef.update(
                    mapOf(
                        "batchId" to "",
                        "batchName" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (student.userId.isNotBlank()) {
                    val userRef = db.collection("users").document(student.userId)
                    val userSnap = userRef.get().await()
                    if (userSnap.exists()) {
                        userRef.update(
                            mapOf(
                                "batchIds" to FieldValue.arrayRemove(batchId),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                recordAuditLog(
                    action = "BATCH_STUDENT_REMOVED",
                    targetType = "batch",
                    targetId = batchId,
                    branchId = batch.branchId,
                    details = "Removed student ${student.fullName} ($studentId) from batch ${batch.effectiveName}"
                )
            }
            "transfer" -> {
                val targetId = targetBatchId ?: throw IllegalArgumentException("targetBatchId is required for batch transfer.")
                val targetRef = db.collection("batches").document(targetId)
                val targetSnap = targetRef.get().await()
                if (!targetSnap.exists()) {
                    throw IllegalArgumentException("Target batch '$targetId' not found.")
                }
                val targetBatch = targetSnap.toObject(Batch::class.java)
                    ?: throw IllegalArgumentException("Failed to parse target batch.")
                if (targetBatch.enrolledCount >= targetBatch.capacity) {
                    throw IllegalStateException("Target batch '${targetBatch.effectiveName}' is at maximum capacity (${targetBatch.capacity}).")
                }
                // Remove from current
                batchRef.update(mapOf("studentIds" to FieldValue.arrayRemove(studentId), "updatedAt" to FieldValue.serverTimestamp())).await()
                // Add to target
                targetRef.update(mapOf("studentIds" to FieldValue.arrayUnion(studentId), "updatedAt" to FieldValue.serverTimestamp())).await()
                studentRef.update(
                    mapOf(
                        "batchId" to targetId,
                        "batchName" to targetBatch.effectiveName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (student.userId.isNotBlank()) {
                    val userRef = db.collection("users").document(student.userId)
                    val userSnap = userRef.get().await()
                    if (userSnap.exists()) {
                        userRef.update(
                            mapOf(
                                "batchIds" to FieldValue.arrayUnion(targetId),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                recordAuditLog(
                    action = "BATCH_STUDENT_TRANSFERRED",
                    targetType = "batch",
                    targetId = targetId,
                    branchId = targetBatch.branchId,
                    details = "Transferred student ${student.fullName} from ${batch.effectiveName} to ${targetBatch.effectiveName}"
                )
            }
            else -> throw IllegalArgumentException("Unsupported action '$action'. Must be add, remove, or transfer.")
        }
    }

    // Students
    fun observeStudents(branchId: String? = null, userId: String? = null): Flow<List<Student>> = callbackFlow {
        var query: Query = db.collection("students")
        if (!userId.isNullOrBlank()) {
            query = query.whereEqualTo("userId", userId)
        } else if (!branchId.isNullOrBlank()) {
            query = query.whereEqualTo("branchId", branchId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeStudents error: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(Student::class.java) ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    suspend fun createOrUpdateStudent(student: Student): Result<String> = runCatching {
        val id = if (student.id.isNotBlank()) student.id else if (student.userId.isNotBlank()) "student_${student.userId}" else "student_${UUID.randomUUID().toString().take(8)}"
        val data = mapOf(
            "userId" to student.userId,
            "branchId" to student.branchId,
            "branchName" to student.branchName,
            "batchId" to student.batchId,
            "batchName" to student.batchName,
            "programId" to student.programId,
            "programName" to student.programName,
            "fullName" to student.fullName,
            "phoneNumber" to student.phoneNumber,
            "email" to student.email,
            "dateOfBirth" to student.dateOfBirth,
            "isMinor" to student.isMinor,
            "guardianName" to student.guardianName,
            "guardianPhone" to student.guardianPhone,
            "guardianConsent" to student.guardianConsent,
            "joinDate" to student.joinDate,
            "currentBelt" to student.currentBelt,
            "status" to student.status,
            "createdAt" to (student.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("students").document(id).set(data, SetOptions.merge()).await()
        id
    }

    // Class Sessions
    fun observeClassSessions(
        branchId: String? = null,
        batchId: String? = null,
        coachId: String? = null,
        date: String? = null
    ): Flow<List<ClassSession>> = callbackFlow {
        var query: Query = db.collection("classSessions")
        if (!branchId.isNullOrBlank()) {
            query = query.whereEqualTo("branchId", branchId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(ClassSession::class.java) ?: emptyList()
            val filtered = list.filter { session ->
                (batchId.isNullOrBlank() || session.batchId == batchId) &&
                (coachId.isNullOrBlank() || session.effectiveCoachIds.contains(coachId) || session.coachId == coachId) &&
                (date.isNullOrBlank() || session.effectiveDate == date)
            }
            trySend(filtered)
        }
        awaitClose { listener.remove() }
    }

    suspend fun createOrUpdateClassSession(session: ClassSession): Result<String> = runCatching {
        if (session.branchId.isBlank()) {
            throw IllegalArgumentException("Class session requires a valid branchId.")
        }
        if (session.batchId.isBlank()) {
            throw IllegalArgumentException("Class session requires a valid batchId.")
        }
        val sessionDate = session.effectiveDate.trim()
        if (sessionDate.isBlank()) {
            throw IllegalArgumentException("Class session date is required.")
        }

        // Validate branch is active
        val branchDoc = db.collection("branches").document(session.branchId).get().await()
        if (!branchDoc.exists()) {
            throw IllegalArgumentException("Branch '${session.branchId}' does not exist.")
        }
        val branchObj = branchDoc.toObject(Branch::class.java)
        if (branchObj != null && !branchObj.isBranchActive) {
            throw IllegalArgumentException("Cannot schedule class session in inactive branch '${branchObj.effectiveName}'.")
        }

        // Validate batch belongs to that branch
        val batchDoc = db.collection("batches").document(session.batchId).get().await()
        if (batchDoc.exists()) {
            val batchBranchId = batchDoc.getString("branchId") ?: ""
            if (batchBranchId.isNotBlank() && batchBranchId != session.branchId) {
                throw IllegalArgumentException("Batch '${session.batchId}' belongs to branch '$batchBranchId', not '${session.branchId}'.")
            }
        }

        // Time ordering validation (e.g. 17:00 < 18:30)
        val startTime = session.startTime.trim()
        val endTime = session.endTime.trim()
        if (startTime.isNotBlank() && endTime.isNotBlank()) {
            if (startTime >= endTime) {
                throw IllegalArgumentException("Class start time ($startTime) must be strictly before end time ($endTime).")
            }
        }

        // Coach authorization checks
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val callerDoc = db.collection("users").document(uid).get().await()
            if (callerDoc.exists()) {
                val caller = callerDoc.toObject(UserProfile::class.java)
                if (caller?.role == "coach" && !caller.branchIds.contains(session.branchId)) {
                    throw IllegalAccessException("Coach is not authorized to schedule class sessions for branch '${session.branchId}'.")
                }
            }
        }
        for (cId in session.effectiveCoachIds) {
            if (cId.isNotBlank()) {
                val coachDoc = db.collection("users").document(cId).get().await()
                if (coachDoc.exists()) {
                    val coachObj = coachDoc.toObject(UserProfile::class.java)
                    if (coachObj != null && coachObj.role == "coach" && !coachObj.branchIds.contains(session.branchId)) {
                        throw IllegalArgumentException("Assigned coach '$cId' (${coachObj.displayName}) is not authorized for branch '${session.branchId}'.")
                    }
                }
            }
        }

        // Scheduling conflict detection
        if (startTime.isNotBlank() && endTime.isNotBlank()) {
            val existingSessionsSnap = db.collection("classSessions")
                .whereEqualTo("branchId", session.branchId)
                .get().await()
            val existingSessions = existingSessionsSnap.toObjects(ClassSession::class.java)
            for (ex in existingSessions) {
                if (ex.effectiveId != session.effectiveId &&
                    ex.effectiveDate == sessionDate &&
                    ex.status.lowercase() != "cancelled"
                ) {
                    val exStart = ex.startTime.trim()
                    val exEnd = ex.endTime.trim()
                    if (exStart.isNotBlank() && exEnd.isNotBlank()) {
                        val overlaps = (exStart < endTime && startTime < exEnd)
                        if (overlaps) {
                            val coachOverlap = ex.effectiveCoachIds.any { session.effectiveCoachIds.contains(it) }
                            val batchOverlap = ex.batchId == session.batchId
                            if (batchOverlap || coachOverlap) {
                                throw IllegalStateException("Scheduling conflict: Session overlaps with existing active session '${ex.batchName.ifBlank { ex.effectiveId }}' ($exStart-$exEnd).")
                            }
                        }
                    }
                }
            }
        }

        val id = if (session.id.isNotBlank()) session.id else if (session.sessionId.isNotBlank()) session.sessionId else "session_${UUID.randomUUID().toString().take(8)}"
        val data = mapOf(
            "sessionId" to id,
            "branchId" to session.branchId,
            "branchName" to session.branchName.ifBlank { branchObj?.effectiveName ?: "" },
            "batchId" to session.batchId,
            "batchName" to session.batchName,
            "programId" to session.programId,
            "programName" to session.programName,
            "coachId" to (session.coachId.ifBlank { session.effectiveCoachIds.firstOrNull() ?: uid ?: "" }),
            "coachName" to session.coachName,
            "coachIds" to session.effectiveCoachIds,
            "coachNames" to session.coachNames,
            "sessionDate" to sessionDate,
            "startTime" to startTime,
            "endTime" to endTime,
            "date" to sessionDate,
            "timeSlot" to session.effectiveTimeSlot,
            "status" to session.status.ifBlank { "scheduled" },
            "capacity" to session.capacity,
            "timezone" to (session.timezone.ifBlank { "Asia/Kolkata" }),
            "createdAt" to (session.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("classSessions").document(id).set(data, SetOptions.merge()).await()
        recordAuditLog(
            action = if (session.id.isNotBlank()) "SESSION_UPDATED" else "SESSION_CREATED",
            targetType = "classSession",
            targetId = id,
            branchId = session.branchId,
            details = "Class session for ${session.batchName} on $sessionDate ($startTime-$endTime)"
        )
        id
    }

    suspend fun createClassSessionWithValidation(session: ClassSession): Result<String> =
        createOrUpdateClassSession(session)

    suspend fun rescheduleOrCancelSession(
        sessionId: String,
        newDate: String? = null,
        newStartTime: String? = null,
        newEndTime: String? = null,
        newStatus: String? = null,
        reason: String = ""
    ): Result<Unit> = runCatching {
        if (sessionId.isBlank()) throw IllegalArgumentException("sessionId is required.")
        val sessionSnap = db.collection("classSessions").document(sessionId).get().await()
        if (!sessionSnap.exists()) throw IllegalArgumentException("Session '$sessionId' not found.")
        val session = sessionSnap.toObject(ClassSession::class.java)
            ?: throw IllegalArgumentException("Failed to parse session.")

        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (!newStatus.isNullOrBlank()) {
            val normStatus = newStatus.lowercase().trim()
            if (normStatus !in listOf("scheduled", "in_progress", "completed", "cancelled")) {
                throw IllegalArgumentException("Invalid session status '$newStatus'.")
            }
            updates["status"] = normStatus
        }

        if (!newDate.isNullOrBlank() || !newStartTime.isNullOrBlank() || !newEndTime.isNullOrBlank()) {
            val date = newDate?.trim() ?: session.effectiveDate
            val start = newStartTime?.trim() ?: session.startTime
            val end = newEndTime?.trim() ?: session.endTime
            if (start.isNotBlank() && end.isNotBlank() && start >= end) {
                throw IllegalArgumentException("Rescheduled start time ($start) must be before end time ($end).")
            }
            updates["sessionDate"] = date
            updates["date"] = date
            updates["startTime"] = start
            updates["endTime"] = end
            updates["timeSlot"] = "$start - $end"
        }

        db.collection("classSessions").document(sessionId).update(updates).await()
        recordAuditLog(
            action = if (newStatus == "cancelled") "SESSION_CANCELLED" else "SESSION_RESCHEDULED",
            targetType = "classSession",
            targetId = sessionId,
            branchId = session.branchId,
            details = "Session modified. Status: ${updates["status"] ?: session.status}. Reason: $reason"
        )
    }

    // Attendance
    fun observeAttendance(
        studentUserId: String? = null,
        sessionId: String? = null,
        branchId: String? = null
    ): Flow<List<Attendance>> = callbackFlow {
        var query: Query = db.collection("attendance")
        if (!studentUserId.isNullOrBlank()) {
            query = query.whereEqualTo("studentUserId", studentUserId)
        } else if (!sessionId.isNullOrBlank()) {
            query = query.whereEqualTo("sessionId", sessionId)
        } else if (!branchId.isNullOrBlank()) {
            query = query.whereEqualTo("branchId", branchId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(Attendance::class.java) ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    suspend fun markAttendance(attendance: Attendance): Result<Unit> = runCatching {
        if (attendance.branchId.isBlank()) {
            throw IllegalArgumentException("Branch ID is required to record attendance.")
        }
        if (attendance.batchId.isBlank()) {
            throw IllegalArgumentException("Batch ID is required to record attendance.")
        }
        if (attendance.studentId.isBlank()) {
            throw IllegalArgumentException("Student ID is required to record attendance.")
        }
        val normalizedStatus = attendance.status.lowercase().trim()
        if (normalizedStatus !in listOf("present", "absent", "late", "excused")) {
            throw IllegalArgumentException("Invalid attendance status '$normalizedStatus'. Must be one of: present, absent, late, excused.")
        }

        // Validate batch belongs to branch
        val batchDoc = db.collection("batches").document(attendance.batchId).get().await()
        if (batchDoc.exists()) {
            val batchBranchId = batchDoc.getString("branchId") ?: ""
            if (batchBranchId.isNotBlank() && batchBranchId != attendance.branchId) {
                throw IllegalArgumentException("Batch '${attendance.batchId}' does not belong to branch '${attendance.branchId}'.")
            }
            val enrolledList = (batchDoc.get("studentIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (enrolledList.isNotEmpty() && !enrolledList.contains(attendance.studentId)) {
                throw IllegalArgumentException("Student '${attendance.studentId}' is not enrolled in batch '${attendance.batchId}'.")
            }
        }

        // Validate student membership in branch and batch
        val studentDoc = db.collection("students").document(attendance.studentId).get().await()
        if (studentDoc.exists()) {
            val studentBranchId = studentDoc.getString("branchId") ?: ""
            val studentBatchId = studentDoc.getString("batchId") ?: ""
            if (studentBranchId.isNotBlank() && studentBranchId != attendance.branchId) {
                throw IllegalArgumentException("Student '${attendance.studentId}' is not assigned to branch '${attendance.branchId}'.")
            }
            if (studentBatchId.isNotBlank() && studentBatchId != attendance.batchId) {
                throw IllegalArgumentException("Student '${attendance.studentId}' is not enrolled in batch '${attendance.batchId}'.")
            }
        }

        val id = if (attendance.id.isNotBlank()) attendance.id else "${attendance.sessionId}_${attendance.studentId}"
        val existingDoc = runCatching { db.collection("attendance").document(id).get().await() }.getOrNull()
        val correctionList = mutableListOf<Map<String, String>>()
        if (existingDoc != null && existingDoc.exists()) {
            val existingStatus = existingDoc.getString("status") ?: ""
            val previousCorrections = (existingDoc.get("correctionHistory") as? List<*>)?.filterIsInstance<Map<String, String>>() ?: emptyList()
            correctionList.addAll(previousCorrections)
            if (existingStatus.isNotBlank() && existingStatus != normalizedStatus) {
                val caller = auth.currentUser?.email ?: auth.currentUser?.uid ?: "Admin/Coach"
                correctionList.add(
                    mapOf(
                        "previousStatus" to existingStatus,
                        "newStatus" to normalizedStatus,
                        "correctedBy" to caller,
                        "correctedAt" to Timestamp.now().toDate().toString()
                    )
                )
                recordAuditLog(
                    action = "ATTENDANCE_CORRECTED",
                    targetType = "attendance",
                    targetId = id,
                    branchId = attendance.branchId,
                    details = "Corrected attendance status for student ${attendance.studentName} from $existingStatus to $normalizedStatus"
                )
            }
        }

        val data = mapOf(
            "branchId" to attendance.branchId,
            "sessionId" to attendance.sessionId,
            "batchId" to attendance.batchId,
            "studentId" to attendance.studentId,
            "studentUserId" to attendance.studentUserId,
            "studentName" to attendance.studentName,
            "date" to attendance.date,
            "status" to normalizedStatus,
            "markedBy" to (auth.currentUser?.email ?: auth.currentUser?.uid ?: "Coach"),
            "correctionHistory" to correctionList,
            "markedAt" to (attendance.markedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("attendance").document(id).set(data, SetOptions.merge()).await()
    }

    suspend fun markBatchAttendance(sessionId: String, records: List<Attendance>): Result<Unit> = runCatching {
        if (sessionId.isBlank()) throw IllegalArgumentException("sessionId is required.")
        for (rec in records) {
            val targetRec = if (rec.sessionId.isBlank()) rec.copy(sessionId = sessionId) else rec
            markAttendance(targetRec).getOrThrow()
        }
    }

    fun observeBranchAttendanceReport(
        branchId: String,
        startDate: String? = null,
        endDate: String? = null
    ): Flow<List<Attendance>> = callbackFlow {
        val query = db.collection("attendance").whereEqualTo("branchId", branchId)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(Attendance::class.java) ?: emptyList()
            val filtered = list.filter { att ->
                val attDate = att.date
                (startDate.isNullOrBlank() || attDate >= startDate) &&
                (endDate.isNullOrBlank() || attDate <= endDate)
            }
            trySend(filtered)
        }
        awaitClose { listener.remove() }
    }

    // Fee Plans
    fun observeFeePlans(
        branchId: String? = null,
        programId: String? = null,
        includeInactive: Boolean = false
    ): Flow<List<FeePlan>> = callbackFlow {
        var query: Query = db.collection("feePlans")
        if (!includeInactive) {
            query = query.whereEqualTo("active", true)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(FeePlan::class.java) ?: emptyList()
            val filtered = list.filter { plan ->
                (branchId.isNullOrBlank() || plan.branchId == branchId || plan.branchIds.contains(branchId) || plan.branchId.isBlank()) &&
                (programId.isNullOrBlank() || plan.programId == programId || plan.programId.isBlank())
            }
            trySend(filtered)
        }
        awaitClose { listener.remove() }
    }

    suspend fun getFeePlan(planId: String): Result<FeePlan?> = runCatching {
        if (planId.isBlank()) return@runCatching null
        val snap = db.collection("feePlans").document(planId).get().await()
        if (snap.exists()) snap.toObject(FeePlan::class.java) else null
    }

    suspend fun createOrUpdateFeePlan(plan: FeePlan): Result<String> = runCatching {
        val trimmedName = plan.effectiveName.trim()
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Fee plan name cannot be blank.")
        }
        if (plan.amountPaise <= 0L) {
            throw IllegalArgumentException("Fee plan amount must be greater than zero.")
        }
        // Validate branch if specified
        if (plan.branchId.isNotBlank()) {
            val branchSnap = db.collection("branches").document(plan.branchId).get().await()
            if (branchSnap.exists()) {
                val branchObj = branchSnap.toObject(Branch::class.java)
                if (branchObj != null && !branchObj.isBranchActive) {
                    throw IllegalArgumentException("Cannot associate fee plan with inactive branch '${branchObj.effectiveName}'.")
                }
            }
        }
        // Validate program if specified
        if (plan.programId.isNotBlank()) {
            val progSnap = db.collection("programs").document(plan.programId).get().await()
            if (progSnap.exists()) {
                val progObj = progSnap.toObject(Program::class.java)
                if (progObj != null && !progObj.isProgramActive) {
                    throw IllegalArgumentException("Cannot associate fee plan with inactive program '${progObj.effectiveName}'.")
                }
            }
        }

        val id = if (plan.id.isNotBlank()) plan.id else if (plan.planId.isNotBlank()) plan.planId else "fee_${UUID.randomUUID().toString().take(8)}"
        val data = mapOf(
            "planId" to id,
            "branchId" to plan.branchId,
            "branchName" to plan.branchName,
            "branchIds" to (if (plan.branchIds.isNotEmpty()) plan.branchIds else if (plan.branchId.isNotBlank()) listOf(plan.branchId) else emptyList()),
            "programId" to plan.programId,
            "programName" to plan.programName,
            "name" to trimmedName,
            "planName" to trimmedName,
            "description" to plan.description.trim(),
            "amountPaise" to plan.amountPaise,
            "billingPeriod" to plan.effectiveFrequency,
            "billingFrequency" to plan.effectiveFrequency,
            "dueDay" to plan.dueDay,
            "status" to plan.status.trim().ifBlank { "active" },
            "active" to (plan.status.lowercase() != "inactive" && plan.active),
            "createdAt" to (plan.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("feePlans").document(id).set(data, SetOptions.merge()).await()
        recordAuditLog(
            action = if (plan.id.isNotBlank()) "FEE_PLAN_UPDATED" else "FEE_PLAN_CREATED",
            targetType = "feePlan",
            targetId = id,
            branchId = plan.branchId,
            details = "Fee plan '$trimmedName' (₹${plan.amountPaise / 100.0} / ${plan.effectiveFrequency}) saved."
        )
        id
    }

    suspend fun setFeePlanStatus(planId: String, status: String): Result<Unit> = runCatching {
        val normalized = status.lowercase().trim()
        if (normalized !in listOf("active", "inactive")) {
            throw IllegalArgumentException("Invalid fee plan status '$status'. Must be active or inactive.")
        }
        val isActive = normalized == "active"
        db.collection("feePlans").document(planId).update(
            mapOf(
                "status" to normalized,
                "active" to isActive,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        recordAuditLog(
            action = "FEE_PLAN_STATUS_CHANGED",
            targetType = "feePlan",
            targetId = planId,
            branchId = "",
            details = "Fee plan status set to $normalized"
        )
    }

    // Fee Invoices
    fun observeInvoices(
        studentUserId: String? = null,
        studentId: String? = null,
        branchId: String? = null,
        status: String? = null
    ): Flow<List<FeeInvoice>> = callbackFlow {
        var query: Query = db.collection("feeInvoices")
        if (!studentUserId.isNullOrBlank()) {
            query = query.whereEqualTo("studentUserId", studentUserId)
        } else if (!studentId.isNullOrBlank()) {
            query = query.whereEqualTo("studentId", studentId)
        } else if (!branchId.isNullOrBlank()) {
            query = query.whereEqualTo("branchId", branchId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(FeeInvoice::class.java) ?: emptyList()
            val filtered = if (!status.isNullOrBlank()) {
                list.filter { it.effectiveStatus == status.lowercase().trim() }
            } else {
                list
            }
            trySend(filtered)
        }
        awaitClose { listener.remove() }
    }

    suspend fun getInvoice(invoiceId: String): Result<FeeInvoice?> = runCatching {
        if (invoiceId.isBlank()) return@runCatching null
        val snap = db.collection("feeInvoices").document(invoiceId).get().await()
        if (snap.exists()) snap.toObject(FeeInvoice::class.java) else null
    }

    suspend fun generateInvoiceWithValidation(invoice: FeeInvoice): Result<String> = runCatching {
        if (invoice.studentId.isBlank()) {
            throw IllegalArgumentException("Student ID is required to generate invoice.")
        }
        if (invoice.branchId.isBlank()) {
            throw IllegalArgumentException("Branch ID is required to generate invoice.")
        }
        val period = invoice.period.trim()
        if (period.isBlank()) {
            throw IllegalArgumentException("Billing period is required (e.g., 'November 2026').")
        }
        if (invoice.amountPaise <= 0L) {
            throw IllegalArgumentException("Invoice amount must be greater than zero.")
        }

        // Validate branch is active
        val branchDoc = db.collection("branches").document(invoice.branchId).get().await()
        if (branchDoc.exists()) {
            val branchObj = branchDoc.toObject(Branch::class.java)
            if (branchObj != null && !branchObj.isBranchActive) {
                throw IllegalArgumentException("Cannot generate invoice for student in inactive branch '${branchObj.effectiveName}'.")
            }
        }

        // Prevent duplicate invoices for the same student and period
        val existingInvoices = db.collection("feeInvoices")
            .whereEqualTo("studentId", invoice.studentId)
            .whereEqualTo("period", period)
            .get()
            .await()

        if (!existingInvoices.isEmpty) {
            val duplicate = existingInvoices.documents.firstOrNull { it.id != invoice.id && it.id != invoice.invoiceId }
            if (duplicate != null) {
                throw IllegalStateException("An invoice for student '${invoice.studentName.ifBlank { invoice.studentId }}' for period '$period' already exists (Invoice #${duplicate.id}).")
            }
        }

        val id = if (invoice.id.isNotBlank()) invoice.id else if (invoice.invoiceId.isNotBlank()) invoice.invoiceId else "inv_${invoice.studentId}_${period.replace(" ", "_").lowercase()}"
        val invNumber = invoice.invoiceNumber.ifBlank { "TF-INV-${System.currentTimeMillis() % 100000}" }

        val data = mapOf(
            "invoiceId" to id,
            "invoiceNumber" to invNumber,
            "branchId" to invoice.branchId,
            "branchName" to invoice.branchName,
            "studentId" to invoice.studentId,
            "studentUserId" to invoice.studentUserId,
            "studentName" to invoice.studentName,
            "feePlanId" to invoice.feePlanId,
            "feePlanName" to invoice.feePlanName,
            "period" to period,
            "itemizedItems" to invoice.itemizedItems.map { mapOf("title" to it.title, "amountPaise" to it.amountPaise) },
            "amountPaise" to invoice.amountPaise,
            "paidPaise" to invoice.paidPaise,
            "dueDate" to invoice.dueDate.trim().ifBlank { "2026-11-10" },
            "status" to invoice.effectiveStatus.ifBlank { "pending" },
            "paymentIds" to invoice.paymentIds,
            "notes" to invoice.notes.trim(),
            "createdAt" to (invoice.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("feeInvoices").document(id).set(data, SetOptions.merge()).await()
        recordAuditLog(
            action = if (invoice.id.isNotBlank()) "INVOICE_UPDATED" else "INVOICE_GENERATED",
            targetType = "feeInvoice",
            targetId = id,
            branchId = invoice.branchId,
            details = "Invoice $invNumber for ${invoice.studentName} ($period: ₹${invoice.amountPaise / 100.0}) generated."
        )
        id
    }

    suspend fun generateInvoice(invoice: FeeInvoice): Result<String> =
        generateInvoiceWithValidation(invoice)

    // Payments
    fun observePayments(
        studentUserId: String? = null,
        studentId: String? = null,
        branchId: String? = null,
        invoiceId: String? = null
    ): Flow<List<Payment>> = callbackFlow {
        var query: Query = db.collection("payments")
        if (!studentUserId.isNullOrBlank()) {
            query = query.whereEqualTo("studentUserId", studentUserId)
        } else if (!studentId.isNullOrBlank()) {
            query = query.whereEqualTo("studentId", studentId)
        } else if (!invoiceId.isNullOrBlank()) {
            query = query.whereEqualTo("invoiceId", invoiceId)
        } else if (!branchId.isNullOrBlank()) {
            query = query.whereEqualTo("branchId", branchId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(Payment::class.java) ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    suspend fun getPayment(paymentId: String): Result<Payment?> = runCatching {
        if (paymentId.isBlank()) return@runCatching null
        val snap = db.collection("payments").document(paymentId).get().await()
        if (snap.exists()) snap.toObject(Payment::class.java) else null
    }

    suspend fun recordPaymentWithValidation(
        payment: Payment,
        requireExistingInvoice: Boolean = false
    ): Result<String> = runCatching {
        if (payment.amountPaise <= 0L) {
            throw IllegalArgumentException("Payment amount must be greater than zero.")
        }
        val trimmedRef = payment.providerReference.trim()

        // Check for duplicate payment by providerReference
        if (trimmedRef.isNotBlank()) {
            val existingRefPayments = db.collection("payments")
                .whereEqualTo("providerReference", trimmedRef)
                .get()
                .await()
            if (!existingRefPayments.isEmpty) {
                val duplicate = existingRefPayments.documents.firstOrNull { it.id != payment.id && it.id != payment.paymentId }
                if (duplicate != null) {
                    throw IllegalStateException("A payment with reference '$trimmedRef' has already been recorded (Receipt #${duplicate.id}).")
                }
            }
        }

        // Validate invoice exists if required or if invoiceId is present
        var invoiceBranchId = payment.branchId
        var invoiceStudentName = payment.studentName
        if (payment.invoiceId.isNotBlank()) {
            val invRef = db.collection("feeInvoices").document(payment.invoiceId)
            val invSnap = invRef.get().await()
            if (requireExistingInvoice && !invSnap.exists()) {
                throw IllegalArgumentException("Invoice '${payment.invoiceId}' does not exist.")
            }
            if (invSnap.exists()) {
                val invoiceObj = invSnap.toObject(FeeInvoice::class.java)
                if (invoiceObj != null) {
                    if (payment.branchId.isNotBlank() && invoiceObj.branchId.isNotBlank() && invoiceObj.branchId != payment.branchId) {
                        throw IllegalArgumentException("Invoice belongs to branch '${invoiceObj.branchId}', not payment branch '${payment.branchId}'.")
                    }
                    invoiceBranchId = invoiceObj.branchId
                    if (invoiceStudentName.isBlank()) invoiceStudentName = invoiceObj.studentName
                    val currentPaid = invoiceObj.paidPaise
                    val totalAmount = invoiceObj.amountPaise
                    val newPaid = currentPaid + payment.amountPaise
                    val newStatus = if (newPaid >= totalAmount) "paid" else "partially_paid"
                    invRef.update(
                        mapOf(
                            "paidPaise" to newPaid,
                            "status" to newStatus,
                            "paymentIds" to FieldValue.arrayUnion(payment.id.ifBlank { payment.paymentId }),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }

        val id = if (payment.id.isNotBlank()) payment.id else if (payment.paymentId.isNotBlank()) payment.paymentId else "pay_${UUID.randomUUID().toString().take(8)}"
        val payNumber = payment.paymentNumber.ifBlank { "TF-REC-${System.currentTimeMillis() % 100000}" }
        val caller = auth.currentUser?.email ?: auth.currentUser?.uid ?: "Admin/Staff"
        val audit = payment.auditLog + mapOf(
            "action" to "PAYMENT_RECORDED",
            "amountPaise" to payment.amountPaise.toString(),
            "recordedBy" to caller,
            "timestamp" to Timestamp.now().toDate().toString()
        )

        val data = mapOf(
            "paymentId" to id,
            "paymentNumber" to payNumber,
            "branchId" to invoiceBranchId,
            "branchName" to payment.branchName,
            "invoiceId" to payment.invoiceId,
            "studentId" to payment.studentId,
            "studentUserId" to payment.studentUserId,
            "studentName" to invoiceStudentName,
            "amountPaise" to payment.amountPaise,
            "paymentDate" to payment.effectiveDate.ifBlank { "2026-11-01" },
            "method" to payment.method.lowercase().trim().ifBlank { "cash" },
            "providerReference" to trimmedRef,
            "status" to payment.status.lowercase().trim().ifBlank { "verified" },
            "recordedBy" to caller,
            "notes" to payment.notes.trim(),
            "auditLog" to audit,
            "createdAt" to (payment.createdAt ?: FieldValue.serverTimestamp()),
            "verifiedAt" to (payment.verifiedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("payments").document(id).set(data, SetOptions.merge()).await()
        recordAuditLog(
            action = "PAYMENT_RECORDED",
            targetType = "payment",
            targetId = id,
            branchId = invoiceBranchId,
            details = "Recorded ${payment.method} payment of ₹${payment.amountPaise / 100.0} for $invoiceStudentName (Ref: $trimmedRef)."
        )
        id
    }

    suspend fun recordPayment(payment: Payment): Result<String> =
        recordPaymentWithValidation(payment, requireExistingInvoice = false)

    suspend fun refundPayment(paymentId: String, reason: String = ""): Result<Unit> = runCatching {
        if (paymentId.isBlank()) throw IllegalArgumentException("paymentId is required for refund.")
        val payRef = db.collection("payments").document(paymentId)
        val paySnap = payRef.get().await()
        if (!paySnap.exists()) {
            throw IllegalArgumentException("Payment '$paymentId' not found.")
        }
        val payment = paySnap.toObject(Payment::class.java)
            ?: throw IllegalArgumentException("Failed to parse payment '$paymentId'.")

        if (payment.status.lowercase() == "refunded") {
            throw IllegalStateException("Payment '$paymentId' is already marked as refunded.")
        }

        val caller = auth.currentUser?.email ?: auth.currentUser?.uid ?: "Admin"
        val updatedAudit = payment.auditLog + mapOf(
            "action" to "PAYMENT_REFUNDED",
            "reason" to reason,
            "refundedBy" to caller,
            "timestamp" to Timestamp.now().toDate().toString()
        )

        payRef.update(
            mapOf(
                "status" to "refunded",
                "notes" to if (reason.isNotBlank()) "${payment.notes} [Refunded: $reason]".trim() else payment.notes,
                "auditLog" to updatedAudit,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Adjust corresponding invoice paid amount
        if (payment.invoiceId.isNotBlank()) {
            val invRef = db.collection("feeInvoices").document(payment.invoiceId)
            val invSnap = invRef.get().await()
            if (invSnap.exists()) {
                val inv = invSnap.toObject(FeeInvoice::class.java)
                if (inv != null) {
                    val newPaid = (inv.paidPaise - payment.amountPaise).coerceAtLeast(0L)
                    val newStatus = when {
                        newPaid <= 0L -> "pending"
                        newPaid < inv.amountPaise -> "partially_paid"
                        else -> "paid"
                    }
                    invRef.update(
                        mapOf(
                            "paidPaise" to newPaid,
                            "status" to newStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }

        recordAuditLog(
            action = "PAYMENT_REFUNDED",
            targetType = "payment",
            targetId = paymentId,
            branchId = payment.branchId,
            details = "Refunded payment $paymentId (₹${payment.amountPaise / 100.0}). Reason: $reason"
        )
    }

    suspend fun markOverdueInvoices(currentDate: String): Result<Int> = runCatching {
        val invoices = db.collection("feeInvoices").get().await().toObjects(FeeInvoice::class.java)
        var count = 0
        for (inv in invoices) {
            if (inv.effectiveStatus in listOf("pending", "partially_paid", "unpaid") && inv.dueDate.isNotBlank() && inv.dueDate < currentDate) {
                db.collection("feeInvoices").document(inv.effectiveId).update(
                    mapOf(
                        "status" to "overdue",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                count++
            }
        }
        count
    }

    // Progress Records
    fun observeProgress(studentUserId: String? = null): Flow<List<ProgressRecord>> = callbackFlow {
        var query: Query = db.collection("progressRecords")
        if (!studentUserId.isNullOrBlank()) {
            query = query.whereEqualTo("studentUserId", studentUserId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(ProgressRecord::class.java) ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    suspend fun addProgressRecord(record: ProgressRecord): Result<String> = runCatching {
        val id = if (record.id.isNotBlank()) record.id else "prog_${UUID.randomUUID().toString().take(8)}"
        val data = mapOf(
            "branchId" to record.branchId,
            "studentId" to record.studentId,
            "studentUserId" to record.studentUserId,
            "studentName" to record.studentName,
            "discipline" to record.discipline,
            "rank" to record.rank,
            "assessmentDate" to record.assessmentDate,
            "skillNotes" to record.skillNotes,
            "coachId" to (auth.currentUser?.uid ?: ""),
            "coachName" to (auth.currentUser?.displayName ?: "Coach"),
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("progressRecords").document(id).set(data).await()

        // Also update student current belt
        if (record.studentId.isNotBlank()) {
            db.collection("students").document(record.studentId)
                .update("currentBelt", record.rank, "updatedAt", FieldValue.serverTimestamp())
                .await()
        }
        id
    }

    // Certificates
    fun observeCertificates(studentUserId: String? = null): Flow<List<Certificate>> = callbackFlow {
        var query: Query = db.collection("certificates")
        if (!studentUserId.isNullOrBlank()) {
            query = query.whereEqualTo("studentUserId", studentUserId)
        }
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(Certificate::class.java) ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    suspend fun issueCertificate(certificate: Certificate): Result<String> = runCatching {
        val id = if (certificate.id.isNotBlank()) certificate.id else "cert_${UUID.randomUUID().toString().take(8)}"
        val data = mapOf(
            "branchId" to certificate.branchId,
            "studentId" to certificate.studentId,
            "studentUserId" to certificate.studentUserId,
            "studentName" to certificate.studentName,
            "title" to certificate.title,
            "discipline" to certificate.discipline,
            "grade" to certificate.grade,
            "storagePath" to certificate.storagePath,
            "issuedAt" to certificate.issuedAt,
            "issuedBy" to (auth.currentUser?.displayName ?: "Head Master"),
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("certificates").document(id).set(data).await()
        id
    }

    // Videos
    fun observeVideos(): Flow<List<Video>> = callbackFlow {
        val listener = db.collection("videos")
            .whereEqualTo("published", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Video::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // Announcements
    fun observeAnnouncements(): Flow<List<Announcement>> = callbackFlow {
        val listener = db.collection("announcements")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Announcement::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun createAnnouncement(announcement: Announcement): Result<String> = runCatching {
        val id = if (announcement.id.isNotBlank()) announcement.id else "ann_${UUID.randomUUID().toString().take(8)}"
        val data = mapOf(
            "title" to announcement.title,
            "message" to announcement.message,
            "priority" to announcement.priority,
            "branchIds" to announcement.branchIds,
            "targetBatchIds" to announcement.targetBatchIds,
            "publishedByName" to (auth.currentUser?.displayName ?: "Academy Admin"),
            "publishedAt" to FieldValue.serverTimestamp()
        )
        db.collection("announcements").document(id).set(data).await()
        id
    }

    private suspend fun recordAuditLog(
        action: String,
        targetType: String,
        targetId: String,
        branchId: String,
        details: String
    ) {
        try {
            val logId = "audit_${UUID.randomUUID().toString().take(12)}"
            val actorUid = auth.currentUser?.uid ?: "system"
            val actorEmailOrPhone = auth.currentUser?.email ?: auth.currentUser?.phoneNumber ?: actorUid
            db.collection("auditLogs").document(logId).set(
                mapOf(
                    "action" to action,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "branchId" to branchId,
                    "actorUid" to actorUid,
                    "actor" to actorEmailOrPhone,
                    "details" to details,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Audit log write skipped: ${e.message}")
        }
    }

    suspend fun getStudentDetails(studentId: String): Result<Student> = runCatching {
        if (studentId.isBlank()) throw IllegalArgumentException("Student ID is required.")
        val doc = db.collection("students").document(studentId).get().await()
        if (!doc.exists()) throw IllegalArgumentException("Student record not found.")
        doc.toObject(Student::class.java)
            ?: throw IllegalStateException("Unable to parse student record.")
    }

    // Admission Requests
    fun observeAdmissionRequests(): Flow<List<AdmissionRequest>> = callbackFlow {
        val listener = db.collection("admissionRequests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(AdmissionRequest::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun getAdmissionRequestDetails(requestId: String): Result<AdmissionRequest> = runCatching {
        if (requestId.isBlank()) throw IllegalArgumentException("Admission request ID is required.")
        val doc = db.collection("admissionRequests").document(requestId).get().await()
        if (!doc.exists()) throw IllegalArgumentException("Admission request not found.")
        doc.toObject(AdmissionRequest::class.java)
            ?: throw IllegalStateException("Unable to parse admission request.")
    }

    suspend fun submitAdmissionRequest(request: AdmissionRequest): Result<String> = runCatching {
        if (request.fullName.isBlank()) {
            throw IllegalArgumentException("Applicant full name is required.")
        }
        if (request.phoneNumber.isBlank()) {
            throw IllegalArgumentException("A verified phone number is required.")
        }
        if (request.preferredBranchId.isBlank()) {
            throw IllegalArgumentException("A valid preferred branch ID is required.")
        }
        if (request.programId.isBlank() && request.programName.isBlank()) {
            throw IllegalArgumentException("A martial arts program selection is required.")
        }
        if (request.isMinor) {
            if (request.guardianName.isBlank() || request.guardianPhone.isBlank()) {
                throw IllegalArgumentException("Guardian name and contact phone are required when the applicant is a minor.")
            }
            if (!request.guardianConsent) {
                throw IllegalArgumentException("Guardian consent is required when the applicant is a minor.")
            }
        }

        // Verify branch status if the branch record exists in Firestore
        val branchDoc = db.collection("branches").document(request.preferredBranchId).get().await()
        if (branchDoc.exists()) {
            val branch = branchDoc.toObject(Branch::class.java)
            if (branch != null && !branch.isBranchActive) {
                throw IllegalArgumentException("Branch '${branch.effectiveName}' is currently inactive and cannot accept admission requests.")
            }
        }

        val resolvedUserId = request.userId.ifBlank { auth.currentUser?.uid ?: "" }
        val normalizedPhone = request.phoneNumber.trim()
        val deterministicKey = resolvedUserId.ifBlank { normalizedPhone.filter { it.isLetterOrDigit() } }
        val id = if (request.id.isNotBlank()) {
            request.id
        } else if (deterministicKey.isNotBlank()) {
            "adm_$deterministicKey"
        } else {
            "adm_${UUID.randomUUID().toString().take(8)}"
        }

        // Prevent duplicate or repeated active/pending submissions
        val existingDoc = db.collection("admissionRequests").document(id).get().await()
        if (existingDoc.exists()) {
            val existingStatus = existingDoc.getString("status")?.lowercase() ?: "pending"
            if (existingStatus == "pending" || existingStatus == "approved") {
                throw IllegalStateException("An admission request for this applicant is already $existingStatus.")
            }
        }

        val data = mapOf(
            "userId" to resolvedUserId,
            "fullName" to request.fullName.trim(),
            "email" to request.email.trim(),
            "phoneNumber" to request.phoneNumber.trim(),
            "dateOfBirth" to request.dateOfBirth.trim(),
            "isMinor" to request.isMinor,
            "programId" to request.programId,
            "programName" to request.programName,
            "preferredBranchId" to request.preferredBranchId,
            "preferredBranchName" to request.preferredBranchName,
            "preferredBatchId" to request.preferredBatchId,
            "guardianName" to request.guardianName.trim(),
            "guardianPhone" to request.guardianPhone.trim(),
            "guardianConsent" to request.guardianConsent,
            "status" to "pending",
            "reviewedBy" to "",
            "rejectionReason" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("admissionRequests").document(id).set(data).await()
        id
    }

    suspend fun reviewAdmission(
        requestId: String,
        status: String,
        reviewedBy: String,
        rejectionReason: String = ""
    ): Result<Unit> = runCatching {
        val normalizedStatus = status.lowercase().trim()
        if (normalizedStatus !in listOf("approved", "rejected", "pending")) {
            throw IllegalArgumentException("Invalid admission status '$status'.")
        }
        val requestDoc = db.collection("admissionRequests").document(requestId).get().await()
        if (!requestDoc.exists()) {
            throw IllegalArgumentException("Admission request '$requestId' not found.")
        }
        val req = requestDoc.toObject(AdmissionRequest::class.java)
            ?: throw IllegalStateException("Invalid admission request record.")

        if (normalizedStatus == "approved" && req.preferredBranchId.isNotBlank()) {
            val branchDoc = db.collection("branches").document(req.preferredBranchId).get().await()
            if (branchDoc.exists()) {
                val branch = branchDoc.toObject(Branch::class.java)
                if (branch != null && !branch.isBranchActive) {
                    throw IllegalStateException("Cannot approve admission into an inactive branch '${branch.effectiveName}'.")
                }
            }
        }

        db.collection("admissionRequests").document(requestId).update(
            mapOf(
                "status" to normalizedStatus,
                "reviewedBy" to reviewedBy,
                "rejectionReason" to if (normalizedStatus == "rejected") rejectionReason.trim() else "",
                "reviewedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        var matchedUserId = req.userId
        val phone = req.phoneNumber.trim()

        if (matchedUserId.isNotBlank()) {
            val userDocRef = db.collection("users").document(matchedUserId)
            val userSnap = userDocRef.get().await()
            if (userSnap.exists()) {
                val currentBranches = (userSnap.get("branchIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val updatedBranches = if (req.preferredBranchId.isNotBlank() && !currentBranches.contains(req.preferredBranchId)) {
                    currentBranches + req.preferredBranchId
                } else currentBranches
                val isApproved = normalizedStatus == "approved"
                userDocRef.update(
                    mapOf(
                        "status" to normalizedStatus,
                        "active" to isApproved,
                        "preferredBranchId" to req.preferredBranchId,
                        "preferredBranchName" to req.preferredBranchName,
                        "branchIds" to if (isApproved) updatedBranches else currentBranches,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } else if (phone.isNotBlank()) {
            val usersSnap = db.collection("users")
                .whereEqualTo("phoneNumber", phone)
                .get().await()
            for (doc in usersSnap.documents) {
                matchedUserId = doc.id
                val currentBranches = (doc.get("branchIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val updatedBranches = if (req.preferredBranchId.isNotBlank() && !currentBranches.contains(req.preferredBranchId)) {
                    currentBranches + req.preferredBranchId
                } else currentBranches
                val isApproved = normalizedStatus == "approved"

                doc.reference.update(
                    mapOf(
                        "status" to normalizedStatus,
                        "active" to isApproved,
                        "preferredBranchId" to req.preferredBranchId,
                        "preferredBranchName" to req.preferredBranchName,
                        "branchIds" to if (isApproved) updatedBranches else currentBranches,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        if (normalizedStatus == "approved") {
            // Idempotent Student profile & enrollment creation:
            // Reuse any existing student document for this userId or deterministic ID "student_{userId}"
            val resolvedStudentUserId = matchedUserId.ifBlank { "user_$requestId" }
            val deterministicStudentId = "student_${matchedUserId.ifBlank { requestId }}"
            val existingByUserSnap = if (matchedUserId.isNotBlank()) {
                db.collection("students").whereEqualTo("userId", matchedUserId).limit(1).get().await()
            } else null
            val targetStudentId = existingByUserSnap?.documents?.firstOrNull()?.id ?: deterministicStudentId
            val existingStudentDoc = db.collection("students").document(targetStudentId).get().await()
            val existingStudent = if (existingStudentDoc.exists()) existingStudentDoc.toObject(Student::class.java) else null

            val enrolledStudent = Student(
                id = targetStudentId,
                userId = resolvedStudentUserId,
                branchId = req.preferredBranchId,
                branchName = req.preferredBranchName.ifBlank { existingStudent?.branchName ?: "True Fighters Central Dojo" },
                batchId = req.preferredBatchId.ifBlank { existingStudent?.batchId ?: "batch_morning_karate" },
                batchName = existingStudent?.batchName?.ifBlank { "Morning Warriors" } ?: "Morning Warriors",
                programId = req.programId.ifBlank { existingStudent?.programId ?: "prog_karate" },
                programName = req.programName.ifBlank { existingStudent?.programName ?: "Traditional Karate" },
                fullName = req.fullName,
                phoneNumber = req.phoneNumber,
                email = req.email,
                dateOfBirth = req.dateOfBirth.ifBlank { existingStudent?.dateOfBirth ?: "" },
                isMinor = req.isMinor || (existingStudent?.isMinor == true),
                guardianName = req.guardianName.ifBlank { existingStudent?.guardianName ?: "" },
                guardianPhone = req.guardianPhone.ifBlank { existingStudent?.guardianPhone ?: "" },
                guardianConsent = req.guardianConsent || (existingStudent?.guardianConsent == true),
                currentBelt = existingStudent?.currentBelt?.ifBlank { "White Belt" } ?: "White Belt",
                joinDate = existingStudent?.joinDate?.ifBlank { "2025-03-01" } ?: "2025-03-01",
                status = "active"
            )
            createOrUpdateStudent(enrolledStudent).getOrThrow()
        }

        recordAuditLog(
            action = "ADMISSION_${normalizedStatus.uppercase()}",
            targetType = "admissionRequest",
            targetId = requestId,
            branchId = req.preferredBranchId,
            details = "Admission request $normalizedStatus by $reviewedBy" +
                if (normalizedStatus == "rejected" && rejectionReason.isNotBlank()) " (Reason: ${rejectionReason.trim()})" else ""
        )
    }

    // Academy Seed Data (initializes on clean databases)
    suspend fun seedAcademyDefaultsIfNeeded() {
        try {
            val branchesSnap = db.collection("branches").limit(1).get().await()
            if (branchesSnap.isEmpty) {
                Log.d(TAG, "Seeding True Fighters Academy initial data...")
                // Branches
                val branches = listOf(
                    Branch(
                        id = "branch_main",
                        branchId = "branch_main",
                        name = "True Fighters Central Dojo",
                        branchName = "True Fighters Central Dojo",
                        code = "TF-HQ",
                        branchCode = "TF-HQ",
                        address = "Sector 14, Main Hub",
                        city = "New Delhi",
                        state = "Delhi",
                        postalCode = "110001",
                        phone = "+91 98110 12345",
                        contactPhone = "+91 98110 12345",
                        status = "active",
                        active = true,
                        createdBy = "admin"
                    ),
                    Branch(
                        id = "branch_west",
                        branchId = "branch_west",
                        name = "True Fighters West Arena",
                        branchName = "True Fighters West Arena",
                        code = "TF-WST",
                        address = "Ring Road, West Campus",
                        city = "New Delhi",
                        state = "Delhi",
                        postalCode = "110027",
                        phone = "+91 98110 54321",
                        contactPhone = "+91 98110 54321",
                        status = "active",
                        active = true,
                        createdBy = "admin"
                    ),
                    Branch(
                        id = "branch_south",
                        branchId = "branch_south",
                        name = "True Fighters South Elite",
                        branchName = "True Fighters South Elite",
                        code = "TF-STH",
                        address = "South Extension Part 2",
                        city = "New Delhi",
                        state = "Delhi",
                        postalCode = "110049",
                        phone = "+91 98110 67890",
                        contactPhone = "+91 98110 67890",
                        status = "active",
                        active = true,
                        createdBy = "admin"
                    )
                )
                branches.forEach { b ->
                    db.collection("branches").document(b.id).set(
                        mapOf(
                            "branchId" to b.id,
                            "name" to b.name,
                            "branchName" to b.name,
                            "code" to b.code,
                            "branchCode" to b.code,
                            "address" to b.address,
                            "city" to b.city,
                            "state" to b.state,
                            "postalCode" to b.postalCode,
                            "phone" to b.phone,
                            "contactPhone" to b.contactPhone,
                            "status" to b.status,
                            "active" to b.active,
                            "createdBy" to b.createdBy,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                // Programs
                val programs = listOf(
                    Program(id = "prog_karate", programId = "prog_karate", name = "Traditional Karate", programName = "Traditional Karate", description = "Shotokan & Goju-ryu forms, katas, discipline, and kumite", discipline = "Karate", level = "All Levels", branchIds = listOf("branch_main", "branch_west", "branch_south"), status = "active", active = true),
                    Program(id = "prog_taekwondo", programId = "prog_taekwondo", name = "Olympic Taekwondo", programName = "Olympic Taekwondo", description = "High-speed kicking techniques, sparring, and Olympic drills", discipline = "Taekwondo", level = "All Levels", branchIds = listOf("branch_main", "branch_west", "branch_south"), status = "active", active = true),
                    Program(id = "prog_kickboxing", programId = "prog_kickboxing", name = "K-1 Kickboxing & Muay Thai", programName = "K-1 Kickboxing & Muay Thai", description = "Striking power, Dutch drills, clinch work, and conditioning", discipline = "Kickboxing", level = "Intermediate / Pro", branchIds = listOf("branch_main", "branch_west", "branch_south"), status = "active", active = true),
                    Program(id = "prog_mma", programId = "prog_mma", name = "Mixed Martial Arts (MMA)", programName = "Mixed Martial Arts (MMA)", description = "Grappling, takedowns, submissions, ground and pound", discipline = "MMA", level = "Advanced", branchIds = listOf("branch_main", "branch_west", "branch_south"), status = "active", active = true),
                    Program(id = "prog_selfdefense", programId = "prog_selfdefense", name = "Practical Self-Defense", programName = "Practical Self-Defense", description = "Street awareness, neutralization techniques, weapon defense", discipline = "Self Defense", level = "Open", branchIds = listOf("branch_main", "branch_west", "branch_south"), status = "active", active = true)
                )
                programs.forEach { p ->
                    db.collection("programs").document(p.id).set(
                        mapOf(
                            "programId" to p.id,
                            "name" to p.name,
                            "programName" to p.programName,
                            "description" to p.description,
                            "discipline" to p.discipline,
                            "level" to p.level,
                            "branchIds" to p.branchIds,
                            "status" to p.status,
                            "active" to p.active,
                            "createdBy" to "Master Arvind Singh",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                // Batches
                val batches = listOf(
                    Batch(id = "batch_morning_karate", batchId = "batch_morning_karate", branchId = "branch_main", branchName = "True Fighters Central Dojo", programId = "prog_karate", programName = "Traditional Karate", coachId = "coach_master_singh", coachName = "Master Arvind Singh (5th Dan)", coachIds = listOf("coach_master_singh"), coachNames = listOf("Master Arvind Singh (5th Dan)"), name = "Morning Warriors", batchName = "Morning Warriors", schedule = "Mon, Wed, Fri - 6:30 AM to 8:00 AM", capacity = 25, status = "active", active = true),
                    Batch(id = "batch_evening_kickbox", batchId = "batch_evening_kickbox", branchId = "branch_main", branchName = "True Fighters Central Dojo", programId = "prog_kickboxing", programName = "K-1 Kickboxing", coachId = "coach_master_singh", coachName = "Master Arvind Singh (5th Dan)", coachIds = listOf("coach_master_singh"), coachNames = listOf("Master Arvind Singh (5th Dan)"), name = "Evening Strike Squad", batchName = "Evening Strike Squad", schedule = "Tue, Thu, Sat - 5:30 PM to 7:00 PM", capacity = 30, status = "active", active = true),
                    Batch(id = "batch_youth_taekwondo", batchId = "batch_youth_taekwondo", branchId = "branch_west", branchName = "True Fighters West Arena", programId = "prog_taekwondo", programName = "Olympic Taekwondo", coachId = "coach_master_singh", coachName = "Master Arvind Singh (5th Dan)", coachIds = listOf("coach_master_singh"), coachNames = listOf("Master Arvind Singh (5th Dan)"), name = "Youth Tigers", batchName = "Youth Tigers", schedule = "Mon to Fri - 4:00 PM to 5:30 PM", capacity = 35, status = "active", active = true),
                    Batch(id = "batch_mma_pro", batchId = "batch_mma_pro", branchId = "branch_south", branchName = "True Fighters South Elite", programId = "prog_mma", programName = "Mixed Martial Arts", coachId = "coach_master_singh", coachName = "Master Arvind Singh (5th Dan)", coachIds = listOf("coach_master_singh"), coachNames = listOf("Master Arvind Singh (5th Dan)"), name = "Pro Cage Combat", batchName = "Pro Cage Combat", schedule = "Mon, Wed, Fri - 7:30 PM to 9:30 PM", capacity = 20, status = "active", active = true)
                )
                batches.forEach { batch ->
                    db.collection("batches").document(batch.id).set(
                        mapOf(
                            "batchId" to batch.id,
                            "branchId" to batch.branchId,
                            "branchName" to batch.branchName,
                            "programId" to batch.programId,
                            "programName" to batch.programName,
                            "coachId" to batch.coachId,
                            "coachName" to batch.coachName,
                            "coachIds" to batch.coachIds,
                            "coachNames" to batch.coachNames,
                            "studentIds" to batch.studentIds,
                            "name" to batch.name,
                            "batchName" to batch.batchName,
                            "schedule" to batch.schedule,
                            "capacity" to batch.capacity,
                            "status" to batch.status,
                            "active" to batch.active,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                // Fee Plans
                val feePlans = listOf(
                    FeePlan(id = "fee_karate_monthly", planId = "fee_karate_monthly", branchId = "branch_main", branchName = "True Fighters Central Dojo", branchIds = listOf("branch_main"), programId = "prog_karate", programName = "Traditional Karate", name = "Standard Monthly Dojo Fee", planName = "Standard Monthly Dojo Fee", amountPaise = 250000L, billingPeriod = "Monthly", billingFrequency = "Monthly", dueDay = 10, status = "active", active = true),
                    FeePlan(id = "fee_mma_quarterly", planId = "fee_mma_quarterly", branchId = "branch_main", branchName = "True Fighters Central Dojo", branchIds = listOf("branch_main", "branch_south"), programId = "prog_mma", programName = "Mixed Martial Arts (MMA)", name = "Quarterly Fight Club Access", planName = "Quarterly Fight Club Access", amountPaise = 650000L, billingPeriod = "Quarterly", billingFrequency = "Quarterly", dueDay = 5, status = "active", active = true),
                    FeePlan(id = "fee_taekwondo_monthly", planId = "fee_taekwondo_monthly", branchId = "branch_west", branchName = "True Fighters West Arena", branchIds = listOf("branch_west"), programId = "prog_taekwondo", programName = "Olympic Taekwondo", name = "West Arena Youth Taekwondo", planName = "West Arena Youth Taekwondo", amountPaise = 220000L, billingPeriod = "Monthly", billingFrequency = "Monthly", dueDay = 10, status = "active", active = true)
                )
                feePlans.forEach { f ->
                    db.collection("feePlans").document(f.id).set(
                        mapOf(
                            "planId" to f.id,
                            "branchId" to f.branchId,
                            "branchName" to f.branchName,
                            "branchIds" to f.branchIds,
                            "programId" to f.programId,
                            "programName" to f.programName,
                            "name" to f.name,
                            "planName" to f.planName,
                            "amountPaise" to f.amountPaise,
                            "billingPeriod" to f.billingPeriod,
                            "billingFrequency" to f.billingFrequency,
                            "dueDay" to f.dueDay,
                            "status" to f.status,
                            "active" to f.active,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }

                // Videos
                val videos = listOf(
                    Video(id = "vid_roundhouse", title = "Perfecting the Mawashi Geri (Roundhouse Kick)", description = "Detailed foot pivot, chambering, hip drive, and recoil drills.", category = "Techniques", videoUrl = "https://www.truefighters.in/training/roundhouse", durationText = "12 mins", accessLevel = "All", published = true),
                    Video(id = "vid_warmup", title = "Dynamic Fighter Warmup & Hip Mobility Routine", description = "Essential 10-minute flow to prevent hamstring and groin injuries.", category = "Warmups", videoUrl = "https://www.truefighters.in/training/warmup", durationText = "10 mins", accessLevel = "All", published = true),
                    Video(id = "vid_boxing_combos", title = "Fluid 4-Strike Boxing Combinations & Head Movement", description = "Jab - Cross - Slip - Left Hook - Low Right roundhouse finish.", category = "Techniques", videoUrl = "https://www.truefighters.in/training/boxing-combos", durationText = "15 mins", accessLevel = "Intermediate", published = true),
                    Video(id = "vid_selfdefense_wrist", title = "Street Wrist-Grab & Collar Choke Escapes", description = "Biomechanical leverage principles for instant escape and counter.", category = "Self Defense", videoUrl = "https://www.truefighters.in/training/self-defense", durationText = "8 mins", accessLevel = "All", published = true)
                )
                videos.forEach { v ->
                    db.collection("videos").document(v.id).set(
                        mapOf(
                            "title" to v.title,
                            "description" to v.description,
                            "category" to v.category,
                            "videoUrl" to v.videoUrl,
                            "durationText" to v.durationText,
                            "branchIds" to listOf("branch_main", "branch_west", "branch_south"),
                            "accessLevel" to v.accessLevel,
                            "published" to v.published,
                            "createdBy" to "Master Arvind Singh",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                // Announcements
                val ann = Announcement(
                    id = "ann_annual_championship",
                    title = "All-India True Fighters Inter-Dojo Championship 2026",
                    message = "Registrations are now open for Kata & Kumite categories. Belts Yellow and above can apply via their head coach before 15th November.",
                    priority = "Important",
                    branchIds = listOf("branch_main", "branch_west", "branch_south"),
                    publishedByName = "Master Arvind Singh (Chief Examiner)"
                )
                db.collection("announcements").document(ann.id).set(
                    mapOf(
                        "title" to ann.title,
                        "message" to ann.message,
                        "priority" to ann.priority,
                        "branchIds" to ann.branchIds,
                        "publishedByName" to ann.publishedByName,
                        "publishedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Seed data check completed: ${e.message}")
        }
    }
}
