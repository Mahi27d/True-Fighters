package com.example.features.authentication

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdmissionRequest
import com.example.data.model.Branch
import com.example.data.model.Program
import com.example.data.model.RoleValidationResult
import com.example.data.repository.TrueFightersRepository
import com.example.ui.admission.AdmissionDialog
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedDark
import com.example.ui.theme.RedPrimary
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    branches: List<Branch>,
    programs: List<Program>,
    repository: TrueFightersRepository,
    authService: FirebaseAuthService = remember { FirebaseAuthService() },
    onAuthSuccess: (roleOverride: String?) -> Unit,
    onSubmitAdmission: (AdmissionRequest) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen State: null = Role Selection Screen; "student", "coach", "admin" = Phone OTP Screen
    var selectedRole by remember { mutableStateOf<String?>(null) }

    // Phone OTP state
    var selectedCountryCode by remember { mutableStateOf("+91") }
    var phoneNumberInput by remember { mutableStateOf("") }
    var otpCodeInput by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isCodeSent by remember { mutableStateOf(false) }

    // Cooldown timer state (60 seconds default)
    var cooldownSeconds by remember { mutableStateOf(0) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Admission dialog
    var showAdmissionDialog by remember { mutableStateOf(false) }

    // Student Registration & Account Status States
    var pendingApprovalProfile by remember { mutableStateOf<com.example.data.model.UserProfile?>(null) }
    var registrationUserId by remember { mutableStateOf<String?>(null) }
    var registrationPhoneNumber by remember { mutableStateOf<String?>(null) }
    var suspendedNoticeMessage by remember { mutableStateOf<String?>(null) }

    // Cooldown countdown effect
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000L)
            cooldownSeconds -= 1
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Academy Crest
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(RedDark)
                    .border(3.dp, GoldBelt, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsMartialArts,
                    contentDescription = "True Fighters Martial Arts Academy",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "TRUE FIGHTERS",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = RedPrimary
            )
            Text(
                text = "MARTIAL ARTS ACADEMY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Discipline • Strength • Honor",
                style = MaterialTheme.typography.bodySmall,
                color = GoldBelt,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(22.dp))

            when {
                suspendedNoticeMessage != null -> {
                    AccountSuspendedCard(
                        message = suspendedNoticeMessage ?: "",
                        onSignOut = {
                            Firebase.auth.signOut()
                            suspendedNoticeMessage = null
                            selectedRole = null
                            isCodeSent = false
                            otpCodeInput = ""
                        }
                    )
                }
                pendingApprovalProfile != null -> {
                    RegistrationPendingCard(
                        profile = pendingApprovalProfile!!,
                        isLoading = isLoading,
                        onCheckStatus = {
                            val user = Firebase.auth.currentUser
                            if (user != null) {
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val fullPhone = pendingApprovalProfile!!.phoneNumber
                                    val res = repository.validateAndResolveUserRole(user.uid, "student", fullPhone)
                                    isLoading = false
                                    when (res) {
                                        is RoleValidationResult.Success -> {
                                            pendingApprovalProfile = null
                                            onAuthSuccess(res.effectiveRole)
                                        }
                                        is RoleValidationResult.RegistrationPending -> {
                                            statusMessage = "Status: Still pending academy review. Please check back shortly."
                                        }
                                        else -> {
                                            errorMessage = "Account status updated. Please sign in again."
                                            pendingApprovalProfile = null
                                            Firebase.auth.signOut()
                                        }
                                    }
                                }
                            }
                        },
                        onSignOut = {
                            Firebase.auth.signOut()
                            pendingApprovalProfile = null
                            selectedRole = null
                            isCodeSent = false
                            otpCodeInput = ""
                        }
                    )
                }
                registrationUserId != null -> {
                    StudentRegistrationFormCard(
                        userId = registrationUserId!!,
                        phoneNumber = registrationPhoneNumber ?: "",
                        branches = branches,
                        programs = programs,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onSubmit = { fullName, dob, isMinor, guardianName, guardianPhone, guardianConsent, branchId, branchName, programName ->
                            isLoading = true
                            errorMessage = null
                            coroutineScope.launch {
                                val result = repository.submitStudentRegistration(
                                    userId = registrationUserId!!,
                                    phoneNumber = registrationPhoneNumber ?: "",
                                    fullName = fullName,
                                    dateOfBirth = dob,
                                    isMinor = isMinor,
                                    guardianName = guardianName,
                                    guardianPhone = guardianPhone,
                                    guardianConsent = guardianConsent,
                                    preferredBranchId = branchId,
                                    preferredBranchName = branchName,
                                    programName = programName
                                )
                                isLoading = false
                                if (result.isSuccess) {
                                    val createdProfile = result.getOrThrow()
                                    registrationUserId = null
                                    pendingApprovalProfile = createdProfile
                                    statusMessage = "Registration request submitted! Awaiting academy review."
                                } else {
                                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to submit registration request."
                                }
                            }
                        },
                        onCancel = {
                            Firebase.auth.signOut()
                            registrationUserId = null
                            registrationPhoneNumber = null
                            selectedRole = null
                            isCodeSent = false
                            otpCodeInput = ""
                        }
                    )
                }
                selectedRole == null -> {
                    // ==========================================
                    // 1. ROLE SELECTION SCREEN (Requirement 2)
                    // ==========================================
                    Text(
                        text = "SELECT YOUR PORTAL",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose your role to sign in securely with your mobile phone OTP.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                                .testTag("auth_error_message")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (statusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Text(
                                text = statusMessage ?: "",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // --- STUDENT CARD ---
                    RoleLoginCard(
                        roleName = "STUDENT",
                        roleDescription = "View classes, attendance, fees, and belt progress.",
                        buttonText = "Student Login",
                        buttonTestTag = "student_role_login_button",
                        icon = Icons.Default.Person,
                        badgeColor = GoldBelt,
                        onRoleSelected = {
                            selectedRole = "student"
                            errorMessage = null
                            statusMessage = null
                            phoneNumberInput = ""
                            otpCodeInput = ""
                            isCodeSent = false
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // --- COACH CARD ---
                    RoleLoginCard(
                        roleName = "COACH",
                        roleDescription = "Manage assigned classes, attendance, and student progress.",
                        buttonText = "Coach Login",
                        buttonTestTag = "coach_role_login_button",
                        icon = Icons.Default.SportsMartialArts,
                        badgeColor = RedPrimary,
                        onRoleSelected = {
                            selectedRole = "coach"
                            errorMessage = null
                            statusMessage = null
                            phoneNumberInput = ""
                            otpCodeInput = ""
                            isCodeSent = false
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // --- ADMIN CARD ---
                    RoleLoginCard(
                        roleName = "ADMIN",
                        roleDescription = "Manage branches, students, schedules, fees, and reports.",
                        buttonText = "Admin Login",
                        buttonTestTag = "admin_role_login_button",
                        icon = Icons.Default.AdminPanelSettings,
                        badgeColor = RedDark,
                        onRoleSelected = {
                            selectedRole = "admin"
                            errorMessage = null
                            statusMessage = null
                            phoneNumberInput = ""
                            otpCodeInput = ""
                            isCodeSent = false
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Apply for Academy Admission Button
                    OutlinedButton(
                        onClick = { showAdmissionDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("apply_admission_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HowToReg,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Student? Apply for Academy Admission",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                else -> {
                    // ==========================================
                    // 2. PHONE OTP SCREEN (Requirement 3 & 4)
                    // ==========================================
                val currentRole = selectedRole!!
                val roleTitle = currentRole.uppercase()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Role Header with Back Navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    selectedRole = null
                                    errorMessage = null
                                    statusMessage = null
                                    isCodeSent = false
                                    otpCodeInput = ""
                                },
                                modifier = Modifier.testTag("back_to_role_selection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Role Selection",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Selected Role Badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = when (currentRole) {
                                    "admin" -> RedDark
                                    "coach" -> RedPrimary
                                    else -> GoldBelt.copy(alpha = 0.25f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (currentRole) {
                                            "admin" -> Icons.Default.AdminPanelSettings
                                            "coach" -> Icons.Default.SportsMartialArts
                                            else -> Icons.Default.Person
                                        },
                                        contentDescription = null,
                                        tint = if (currentRole == "student") GoldBelt else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$roleTitle PORTAL",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (currentRole == "student") MaterialTheme.colorScheme.onSurface else Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (!isCodeSent) "Mobile Phone Login" else "Enter OTP Code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (!isCodeSent)
                                "Enter your registered phone number. We will send a 6-digit OTP code."
                            else
                                "Enter the 6-digit code sent to $selectedCountryCode $phoneNumberInput",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        if (errorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                                    .testTag("auth_error_message")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMessage ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (statusMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            ) {
                                Text(
                                    text = statusMessage ?: "",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (!isCodeSent) {
                            // --- Step A: Phone Number Input ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CountryCodeDropdown(
                                    selectedCode = selectedCountryCode,
                                    onCodeSelected = { selectedCountryCode = it },
                                    enabled = !isLoading
                                )

                                OutlinedTextField(
                                    value = phoneNumberInput,
                                    onValueChange = { input ->
                                        // Allow digits only, up to 10 characters
                                        val digitsOnly = input.filter { it.isDigit() }
                                        if (digitsOnly.length <= 10) {
                                            phoneNumberInput = digitsOnly
                                        }
                                    },
                                    label = { Text("Mobile Number") },
                                    placeholder = { Text("9876543210") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("phone_number_input"),
                                    enabled = !isLoading
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    val cleanNumber = phoneNumberInput.trim()
                                    if (cleanNumber.length < 10) {
                                        errorMessage = "Please enter a valid 10-digit mobile number."
                                        return@Button
                                    }

                                    val fullE164Phone = "$selectedCountryCode$cleanNumber"
                                    isLoading = true
                                    errorMessage = null
                                    statusMessage = null

                                    authService.startPhoneVerification(
                                        activity = context as Activity,
                                        phoneNumber = fullE164Phone,
                                        resendToken = resendToken,
                                        onCodeSent = { id, token ->
                                            verificationId = id
                                            resendToken = token
                                            isCodeSent = true
                                            isLoading = false
                                            cooldownSeconds = 60
                                            statusMessage = "OTP sent successfully to $fullE164Phone"
                                        },
                                        onVerificationCompleted = { credential ->
                                            // Auto-retrieval completed
                                            isLoading = false
                                            coroutineScope.launch {
                                                try {
                                                    val authResult = Firebase.auth.signInWithCredential(credential).await()
                                                    val user = authResult.user
                                                    if (user != null) {
                                                        handleRoleValidation(
                                                            user = user,
                                                            selectedRole = currentRole,
                                                            phone = fullE164Phone,
                                                            repository = repository,
                                                            onSuccess = { onAuthSuccess(it) },
                                                            onNeedsRegistration = { uid, pNum ->
                                                                registrationUserId = uid
                                                                registrationPhoneNumber = pNum
                                                            },
                                                            onRegistrationPending = { prof ->
                                                                pendingApprovalProfile = prof
                                                            },
                                                            onSuspended = { msg ->
                                                                suspendedNoticeMessage = msg
                                                                Firebase.auth.signOut()
                                                            },
                                                            onError = { err ->
                                                                errorMessage = err
                                                                Firebase.auth.signOut()
                                                            }
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = e.localizedMessage ?: "Verification failed."
                                                }
                                            }
                                        },
                                        onVerificationFailed = { errorText ->
                                            isLoading = false
                                            errorMessage = errorText
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("send_otp_button"),
                                enabled = !isLoading && phoneNumberInput.length >= 10,
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Send OTP Code", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else {
                            // --- Step B: OTP Verification ---
                            OutlinedTextField(
                                value = otpCodeInput,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }
                                    if (digits.length <= 6) {
                                        otpCodeInput = digits
                                    }
                                },
                                label = { Text("6-Digit OTP Code") },
                                placeholder = { Text("• • • • • •") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_input"),
                                enabled = !isLoading
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Verify and Login Button
                            Button(
                                onClick = {
                                    val vId = verificationId
                                    val code = otpCodeInput.trim()
                                    if (vId != null && code.length == 6) {
                                        isLoading = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            val result = authService.verifyOtpAndSignIn(vId, code)
                                            isLoading = false
                                            if (result.isSuccess) {
                                                val user = result.getOrThrow()
                                                val fullE164Phone = "$selectedCountryCode${phoneNumberInput.trim()}"
                                                handleRoleValidation(
                                                    user = user,
                                                    selectedRole = currentRole,
                                                    phone = fullE164Phone,
                                                    repository = repository,
                                                    onSuccess = { effectiveRole ->
                                                        onAuthSuccess(effectiveRole)
                                                    },
                                                    onNeedsRegistration = { uid, pNum ->
                                                        registrationUserId = uid
                                                        registrationPhoneNumber = pNum
                                                    },
                                                    onRegistrationPending = { prof ->
                                                        pendingApprovalProfile = prof
                                                    },
                                                    onSuspended = { msg ->
                                                        suspendedNoticeMessage = msg
                                                        Firebase.auth.signOut()
                                                    },
                                                    onError = { err ->
                                                        errorMessage = err
                                                        Firebase.auth.signOut()
                                                    }
                                                )
                                            } else {
                                                errorMessage = "Invalid or expired OTP code. Please enter the correct code."
                                            }
                                        }
                                    } else {
                                        errorMessage = "Please enter the complete 6-digit OTP code."
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("verify_login_button"),
                                enabled = !isLoading && otpCodeInput.length == 6,
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Verify & Login", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Resend OTP Section with Cooldown
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        isCodeSent = false
                                        otpCodeInput = ""
                                        errorMessage = null
                                    },
                                    enabled = !isLoading
                                ) {
                                    Text("Change Number", fontSize = 12.sp)
                                }

                                if (cooldownSeconds > 0) {
                                    Text(
                                        text = "Resend OTP in ${cooldownSeconds}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    TextButton(
                                        onClick = {
                                            val fullE164Phone = "$selectedCountryCode${phoneNumberInput.trim()}"
                                            isLoading = true
                                            errorMessage = null
                                            authService.startPhoneVerification(
                                                activity = context as Activity,
                                                phoneNumber = fullE164Phone,
                                                resendToken = resendToken,
                                                onCodeSent = { id, token ->
                                                    verificationId = id
                                                    resendToken = token
                                                    isLoading = false
                                                    cooldownSeconds = 60
                                                    statusMessage = "New OTP sent to $fullE164Phone"
                                                },
                                                onVerificationCompleted = { cred ->
                                                    isLoading = false
                                                },
                                                onVerificationFailed = { err ->
                                                    isLoading = false
                                                    errorMessage = err
                                                }
                                            )
                                        },
                                        enabled = !isLoading,
                                        modifier = Modifier.testTag("resend_otp_button")
                                    ) {
                                        Text("Resend OTP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Official Website: https://www.truefighters.in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Admission Dialog
    if (showAdmissionDialog) {
        AdmissionDialog(
            branches = branches,
            programs = programs,
            onDismiss = { showAdmissionDialog = false },
            onSubmit = { req ->
                showAdmissionDialog = false
                onSubmitAdmission(req)
                statusMessage = "Thank you ${req.fullName}! Your admission application for ${req.programName} at ${req.preferredBranchName} has been submitted for review."
            }
        )
    }
}

/**
 * Reusable Role Login Card component (Student, Coach, Admin)
 */
@Composable
private fun RoleLoginCard(
    roleName: String,
    roleDescription: String,
    buttonText: String,
    buttonTestTag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    onRoleSelected: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = roleName,
                    tint = badgeColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = roleName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = roleDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onRoleSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag(buttonTestTag),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (roleName) {
                            "ADMIN" -> RedDark
                            "COACH" -> RedPrimary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * Dropdown for Country Code Selection with standard defaults
 */
@Composable
private fun CountryCodeDropdown(
    selectedCode: String,
    onCodeSelected: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val countryCodes = listOf(
        "+91" to "India (+91)",
        "+1" to "USA/Canada (+1)",
        "+44" to "UK (+44)",
        "+971" to "UAE (+971)",
        "+61" to "Australia (+61)",
        "+65" to "Singapore (+65)"
    )

    Box {
        Surface(
            modifier = Modifier
                .height(56.dp)
                .clickable(enabled = enabled) { expanded = true }
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                ),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedCode,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Country Code"
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            countryCodes.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = {
                        onCodeSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Secure Role Validation handler executing backend profile check
 */
private suspend fun handleRoleValidation(
    user: com.google.firebase.auth.FirebaseUser,
    selectedRole: String,
    phone: String,
    repository: TrueFightersRepository,
    onSuccess: (String) -> Unit,
    onNeedsRegistration: (userId: String, phone: String) -> Unit,
    onRegistrationPending: (com.example.data.model.UserProfile) -> Unit,
    onSuspended: (String) -> Unit,
    onError: (String) -> Unit
) {
    val validation = repository.validateAndResolveUserRole(
        userId = user.uid,
        selectedRole = selectedRole,
        phoneNumber = phone
    )

    when (validation) {
        is RoleValidationResult.Success -> {
            onSuccess(validation.effectiveRole)
        }
        is RoleValidationResult.NeedsRegistration -> {
            onNeedsRegistration(validation.userId, validation.phoneNumber)
        }
        is RoleValidationResult.RegistrationPending -> {
            onRegistrationPending(validation.profile)
        }
        is RoleValidationResult.AccountSuspended -> {
            onSuspended(validation.message)
        }
        is RoleValidationResult.RoleMismatch -> {
            // Strict compliance with Requirement 4
            onError("This phone number is not authorized for the selected role. Please select your assigned role.")
        }
        is RoleValidationResult.AccountInactive -> {
            onError(validation.message)
        }
        is RoleValidationResult.Unregistered -> {
            onError("This phone number is not registered with True Fighters Martial Arts Academy. Please submit an admission application or contact the head coach.")
        }
        is RoleValidationResult.Error -> {
            onError(validation.message)
        }
    }
}

/**
 * Student Registration Form Card (shown when verified phone has no student profile yet)
 */
@Composable
private fun StudentRegistrationFormCard(
    userId: String,
    phoneNumber: String,
    branches: List<Branch>,
    programs: List<Program>,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: (
        fullName: String,
        dob: String,
        isMinor: Boolean,
        guardianName: String,
        guardianPhone: String,
        guardianConsent: Boolean,
        branchId: String,
        branchName: String,
        programName: String
    ) -> Unit,
    onCancel: () -> Unit
) {
    val activeBranches = remember(branches) { branches.filter { it.isBranchActive } }
    var fullName by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var isMinor by remember { mutableStateOf(false) }
    var guardianName by remember { mutableStateOf("") }
    var guardianPhone by remember { mutableStateOf("") }
    var guardianConsent by remember { mutableStateOf(false) }
    var selectedBranchId by remember(activeBranches) { mutableStateOf(activeBranches.firstOrNull()?.effectiveId ?: "branch_main") }
    var selectedProgram by remember { mutableStateOf("Traditional Karate") }
    var localError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GoldBelt.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.HowToReg, contentDescription = null, tint = GoldBelt)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Student Registration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Verified Phone: $phoneNumber", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Complete your academy enrollment details. Once submitted, your registration request will be reviewed by the academy administration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val displayError = localError ?: errorMessage
            if (displayError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().testTag("student_registration_error")
                ) {
                    Text(
                        text = displayError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Student Full Name *") },
                modifier = Modifier.fillMaxWidth().testTag("student_registration_name_input"),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = dob,
                onValueChange = { dob = it },
                label = { Text("Date of Birth (e.g. DD/MM/YYYY) *") },
                modifier = Modifier.fillMaxWidth().testTag("student_registration_dob_input"),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Minor Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Student is a Minor (Under 18)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Requires parent/guardian details & consent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isMinor,
                    onCheckedChange = { isMinor = it },
                    modifier = Modifier.testTag("student_registration_minor_toggle"),
                    enabled = !isLoading
                )
            }

            if (isMinor) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Parent / Guardian Information", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RedPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = guardianName,
                            onValueChange = { guardianName = it },
                            label = { Text("Guardian Full Name *") },
                            modifier = Modifier.fillMaxWidth().testTag("student_registration_guardian_name_input"),
                            singleLine = true,
                            enabled = !isLoading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = guardianPhone,
                            onValueChange = { guardianPhone = it },
                            label = { Text("Guardian Emergency Contact *") },
                            modifier = Modifier.fillMaxWidth().testTag("student_registration_guardian_phone_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            enabled = !isLoading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = guardianConsent,
                                onCheckedChange = { guardianConsent = it },
                                modifier = Modifier.testTag("student_registration_guardian_consent_checkbox"),
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I, as the parent/guardian, give consent for martial arts training, discipline, and academy safety regulations.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preferred Active Branch Selection
            Text("Preferred Active Dojo Branch *", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            activeBranches.forEach { b ->
                val bId = b.effectiveId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { selectedBranchId = bId }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(selected = selectedBranchId == bId, onClick = { selectedBranchId = bId })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = b.effectiveName, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preferred Program Selection
            Text("Select Martial Arts Discipline *", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val disciplineOptions = listOf("Traditional Karate", "Taekwondo", "Kickboxing", "MMA & Grappling", "Boxing", "Women's Self Defense")
            var programExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { programExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedProgram, fontSize = 13.sp)
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(expanded = programExpanded, onDismissRequest = { programExpanded = false }) {
                    disciplineOptions.forEach { disc ->
                        DropdownMenuItem(
                            text = { Text(disc, fontSize = 13.sp) },
                            onClick = {
                                selectedProgram = disc
                                programExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    if (fullName.isBlank()) {
                        localError = "Please enter student's full name."
                        return@Button
                    }
                    if (dob.isBlank()) {
                        localError = "Please enter student's date of birth."
                        return@Button
                    }
                    if (selectedBranchId.isBlank()) {
                        localError = "Please select a preferred active branch."
                        return@Button
                    }
                    if (isMinor) {
                        if (guardianName.isBlank() || guardianPhone.isBlank()) {
                            localError = "Guardian name and phone are required for minors."
                            return@Button
                        }
                        if (!guardianConsent) {
                            localError = "Guardian consent is required for minor registration."
                            return@Button
                        }
                    }
                    localError = null
                    val branchName = activeBranches.find { it.effectiveId == selectedBranchId }?.effectiveName ?: "True Fighters Central Dojo"
                    onSubmit(
                        fullName.trim(),
                        dob.trim(),
                        isMinor,
                        guardianName.trim(),
                        guardianPhone.trim(),
                        guardianConsent,
                        selectedBranchId,
                        branchName,
                        selectedProgram
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("student_registration_submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Submit Registration Request", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag("student_registration_cancel_button"),
                enabled = !isLoading
            ) {
                Text("Cancel & Sign Out", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Registration Pending Approval Screen
 */
@Composable
private fun RegistrationPendingCard(
    profile: com.example.data.model.UserProfile,
    isLoading: Boolean,
    onCheckStatus: () -> Unit,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldBelt.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(GoldBelt.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = "Pending Approval",
                    tint = GoldBelt,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Registration Request Pending Approval",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your student registration request for True Fighters Martial Arts Academy has been submitted and is currently pending academy approval. Our team will verify and activate your student account shortly.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Student Name:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.displayName.ifBlank { "Student Member" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Registered Phone:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.phoneNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Preferred Dojo:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.preferredBranchName.ifBlank { "Central Dojo" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GoldBelt.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "PENDING APPROVAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onCheckStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pending_approval_refresh_button"),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check Approval Status", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("pending_approval_logout_button")
            ) {
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Account Suspended Screen
 */
@Composable
private fun AccountSuspendedCard(
    message: String,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Suspended",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Account Suspended",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Academy Administration Contact:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Email: admin@truefighters.in", fontSize = 11.sp)
                    Text("Phone: +91 98765 00000", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("suspended_account_logout_button")
            ) {
                Text("Return to Role Selection", fontWeight = FontWeight.Bold)
            }
        }
    }
}
