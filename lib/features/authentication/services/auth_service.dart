import 'dart:async';
import 'package:firebase_auth/firebase_auth.dart';

/// Exception thrown when an authentication error occurs in [AuthService].
class AuthFailure implements Exception {
  final String message;
  final String? code;

  const AuthFailure(this.message, {this.code});

  @override
  String toString() => 'AuthFailure(code: $code, message: $message)';
}

/// Service handling Firebase Authentication for True Fighters Martial Arts Academy.
/// Follows feature-first clean architecture pattern.
class AuthService {
  final FirebaseAuth _firebaseAuth;

  AuthService({FirebaseAuth? firebaseAuth})
      : _firebaseAuth = firebaseAuth ?? FirebaseAuth.instance;

  /// Stream of [User] changes to observe current session state.
  Stream<User?> get authStateChanges => _firebaseAuth.authStateChanges();

  /// Currently authenticated [User], or null if unauthenticated.
  User? get currentUser => _firebaseAuth.currentUser;

  /// Whether a user is currently signed in.
  bool get isAuthenticated => _firebaseAuth.currentUser != null;

  /// Current user's unique identifier.
  String? get currentUserId => _firebaseAuth.currentUser?.uid;

  /// Current user's email address.
  String? get currentUserEmail => _firebaseAuth.currentUser?.email;

  // ===========================================================================
  // 1. Email and Password Authentication
  // ===========================================================================

  /// Signs in an existing student, coach, or administrator using email and password.
  Future<UserCredential> signInWithEmail({
    required String email,
    required String password,
  }) async {
    try {
      final credential = await _firebaseAuth.signInWithEmailAndPassword(
        email: email.trim(),
        password: password,
      );
      return credential;
    } on FirebaseAuthException catch (e) {
      throw _handleFirebaseAuthException(e);
    } catch (e) {
      throw AuthFailure('An unexpected error occurred during email sign-in: $e');
    }
  }

  /// Registers a new user account with email and password.
  Future<UserCredential> registerWithEmail({
    required String email,
    required String password,
    String? displayName,
  }) async {
    try {
      final credential = await _firebaseAuth.createUserWithEmailAndPassword(
        email: email.trim(),
        password: password,
      );

      if (displayName != null && displayName.isNotEmpty) {
        await credential.user?.updateDisplayName(displayName.trim());
      }

      return credential;
    } on FirebaseAuthException catch (e) {
      throw _handleFirebaseAuthException(e);
    } catch (e) {
      throw AuthFailure('An unexpected error occurred during account registration: $e');
    }
  }

  /// Sends a password reset link to the given [email].
  Future<void> sendPasswordResetEmail({required String email}) async {
    try {
      await _firebaseAuth.sendPasswordResetEmail(email: email.trim());
    } on FirebaseAuthException catch (e) {
      throw _handleFirebaseAuthException(e);
    } catch (e) {
      throw AuthFailure('Failed to dispatch password recovery email: $e');
    }
  }

  // ===========================================================================
  // 2. Phone OTP Authentication
  // ===========================================================================

  /// Initiates phone verification by dispatching an SMS OTP code.
  Future<void> verifyPhoneNumber({
    required String phoneNumber,
    required void Function(String verificationId, int? resendToken) onCodeSent,
    required void Function(FirebaseAuthException e) onVerificationFailed,
    required void Function(PhoneAuthCredential credential) onVerificationCompleted,
    void Function(String verificationId)? onCodeAutoRetrievalTimeout,
    Duration timeout = const Duration(seconds: 60),
    int? forceResendingToken,
  }) async {
    try {
      await _firebaseAuth.verifyPhoneNumber(
        phoneNumber: phoneNumber.trim(),
        timeout: timeout,
        forceResendingToken: forceResendingToken,
        verificationCompleted: onVerificationCompleted,
        verificationFailed: (FirebaseAuthException e) {
          onVerificationFailed(e);
        },
        codeSent: (String verificationId, int? resendToken) {
          onCodeSent(verificationId, resendToken);
        },
        codeAutoRetrievalTimeout: (String verificationId) {
          if (onCodeAutoRetrievalTimeout != null) {
            onCodeAutoRetrievalTimeout(verificationId);
          }
        },
      );
    } on FirebaseAuthException catch (e) {
      throw _handleFirebaseAuthException(e);
    } catch (e) {
      throw AuthFailure('Phone number verification failed to initiate: $e');
    }
  }

  /// Verifies the 6-digit SMS [otpCode] against the [verificationId] and signs in.
  Future<UserCredential> signInWithOtp({
    required String verificationId,
    required String otpCode,
  }) async {
    try {
      final credential = PhoneAuthProvider.credential(
        verificationId: verificationId,
        smsCode: otpCode.trim(),
      );
      return await _firebaseAuth.signInWithCredential(credential);
    } on FirebaseAuthException catch (e) {
      throw _handleFirebaseAuthException(e);
    } catch (e) {
      throw AuthFailure('Failed to verify OTP code: $e');
    }
  }

  // ===========================================================================
  // 3. Session Management
  // ===========================================================================

  /// Signs out the currently authenticated user session.
  Future<void> signOut() async {
    try {
      await _firebaseAuth.signOut();
    } catch (e) {
      throw AuthFailure('Sign out failed: $e');
    }
  }

  // ===========================================================================
  // 4. Exception Mapping
  // ===========================================================================

  AuthFailure _handleFirebaseAuthException(FirebaseAuthException e) {
    switch (e.code) {
      case 'user-not-found':
        return AuthFailure('No True Fighters account found with this email.', code: e.code);
      case 'wrong-password':
      case 'invalid-credential':
        return AuthFailure('Invalid credentials entered. Please try again.', code: e.code);
      case 'email-already-in-use':
        return AuthFailure('This email is already registered with the academy.', code: e.code);
      case 'invalid-email':
        return AuthFailure('Please provide a valid email address.', code: e.code);
      case 'weak-password':
        return AuthFailure('Password should be at least 6 characters.', code: e.code);
      case 'invalid-verification-code':
        return AuthFailure('The SMS OTP verification code entered is invalid.', code: e.code);
      case 'invalid-verification-id':
        return AuthFailure('The OTP session has expired. Please request a new code.', code: e.code);
      case 'too-many-requests':
        return AuthFailure('Too many attempts. Please wait a few moments before retrying.', code: e.code);
      default:
        return AuthFailure(e.message ?? 'Authentication operation failed.', code: e.code);
    }
  }
}
