package com.yumzy.partner.auth

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

class EmailAuthClient {

    private val auth = Firebase.auth

    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): SignInResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            SignInResult(
                data = user?.let {
                    UserData(
                        userId = it.uid,
                        username = it.displayName ?: email.substringBefore("@"),
                        profilePictureUrl = it.photoUrl?.toString()
                    )
                },
                errorMessage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e

            SignInResult(
                data = null,
                errorMessage = when {
                    e.message?.contains("invalid-email") == true -> "Invalid email format"
                    e.message?.contains("user-not-found") == true -> "No account found with this email"
                    e.message?.contains("wrong-password") == true -> "Incorrect password"
                    e.message?.contains("invalid-credential") == true -> "Invalid email or password"
                    e.message?.contains("user-disabled") == true -> "This account has been disabled"
                    e.message?.contains("network") == true -> "Network error. Please check your connection"
                    else -> e.message ?: "Sign in failed"
                }
            )
        }
    }

    /**
     * Create a new account with email and password
     */
    suspend fun signUpWithEmail(email: String, password: String, name: String): SignInResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user

            // Update the user's display name
            user?.let {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                it.updateProfile(profileUpdates).await()
            }

            SignInResult(
                data = user?.let {
                    UserData(
                        userId = it.uid,
                        username = name,
                        profilePictureUrl = it.photoUrl?.toString()
                    )
                },
                errorMessage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e

            SignInResult(
                data = null,
                errorMessage = when {
                    e.message?.contains("invalid-email") == true -> "Invalid email format"
                    e.message?.contains("email-already-in-use") == true -> "An account with this email already exists"
                    e.message?.contains("weak-password") == true -> "Password is too weak"
                    e.message?.contains("network") == true -> "Network error. Please check your connection"
                    else -> e.message ?: "Sign up failed"
                }
            )
        }
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): PasswordResetResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            PasswordResetResult(
                success = true,
                errorMessage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e

            PasswordResetResult(
                success = false,
                errorMessage = when {
                    e.message?.contains("invalid-email") == true -> "Invalid email format"
                    e.message?.contains("user-not-found") == true -> "No account found with this email"
                    e.message?.contains("network") == true -> "Network error. Please check your connection"
                    else -> e.message ?: "Failed to send reset email"
                }
            )
        }
    }

    /**
     * Sign out the current user
     */
    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e
        }
    }

    /**
     * Get the currently signed-in user
     */
    fun getSignedInUser(): UserData? = auth.currentUser?.let {
        UserData(
            userId = it.uid,
            username = it.displayName ?: it.email?.substringBefore("@"),
            profilePictureUrl = it.photoUrl?.toString()
        )
    }

    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean = auth.currentUser != null

    /**
     * Send email verification to current user
     */
    suspend fun sendEmailVerification(): EmailVerificationResult {
        return try {
            val user = auth.currentUser
            if (user != null) {
                user.sendEmailVerification().await()
                EmailVerificationResult(
                    success = true,
                    errorMessage = null
                )
            } else {
                EmailVerificationResult(
                    success = false,
                    errorMessage = "No user is currently signed in"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is CancellationException) throw e

            EmailVerificationResult(
                success = false,
                errorMessage = e.message ?: "Failed to send verification email"
            )
        }
    }

    /**
     * Check if current user's email is verified
     */
    fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified ?: false
}

data class PasswordResetResult(
    val success: Boolean,
    val errorMessage: String?
)

data class EmailVerificationResult(
    val success: Boolean,
    val errorMessage: String?
)