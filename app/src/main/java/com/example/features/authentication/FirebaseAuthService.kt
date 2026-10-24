package com.example.features.authentication

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class FirebaseAuthService(
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val TAG = "FirebaseAuthService"

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    // 1. Google Sign-In via Credential Manager
    fun signInWithGoogle(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        onSuccess(user)
                    } else {
                        onError("Failed to obtain signed-in user.")
                    }
                } else {
                    onError("Unexpected credential format received.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled: ${e.message}", e)
                onCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    // 2. Phone Number OTP Verification
    fun startPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onVerificationCompleted: (PhoneAuthCredential) -> Unit,
        onVerificationFailed: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "Phone auto-verification completed")
                onVerificationCompleted(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed: ${e.message}", e)
                val friendlyMessage = when {
                    e.message?.contains("quota", ignoreCase = true) == true ->
                        "SMS quota exceeded. Please use a test phone number configured in Firebase Console."
                    e.message?.contains("format", ignoreCase = true) == true ->
                        "Invalid phone number format. Please check the country code and number."
                    e.message?.contains("app-not-authorized", ignoreCase = true) == true ||
                    e.message?.contains("Play Integrity", ignoreCase = true) == true ||
                    e.message?.contains("SHA-1", ignoreCase = true) == true ->
                        "Firebase Phone Auth setup required: Add SHA-1/SHA-256 fingerprint in Firebase Console settings."
                    e.message?.contains("too-many-requests", ignoreCase = true) == true ->
                        "Too many OTP requests. Please wait a few minutes and try again."
                    else ->
                        e.localizedMessage ?: "Phone verification failed. Ensure Phone Authentication is enabled in Firebase Console."
                }
                onVerificationFailed(friendlyMessage)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "OTP code sent successfully: $verificationId")
                onCodeSent(verificationId, token)
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (resendToken != null) {
            builder.setForceResendingToken(resendToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    // 3. Confirm Phone OTP and Sign In
    suspend fun verifyOtpAndSignIn(verificationId: String, otpCode: String): Result<FirebaseUser> = runCatching {
        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
        val authResult = auth.signInWithCredential(credential).await()
        checkNotNull(authResult.user) { "Phone authentication returned null user profile." }
    }

    // 4. Password Reset Email
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    // 5. Sign Out
    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        auth.signOut()
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing credential state: ${e.message}")
            } finally {
                onComplete()
            }
        }
    }
}
