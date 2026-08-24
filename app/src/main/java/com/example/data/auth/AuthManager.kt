package com.example.data.auth

import android.content.Context
import android.util.Patterns
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.await(): T {
    if (isComplete) {
        val e = exception
        return if (e == null) {
            if (isCanceled) {
                throw CancellationException("Task $this was cancelled.")
            } else {
                result
            }
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val e = task.exception
            if (e == null) {
                if (task.isCanceled) {
                    cont.cancel()
                } else {
                    cont.resume(task.result)
                }
            } else {
                cont.resumeWithException(e)
            }
        }
    }
}

class AuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("hisab_auth_prefs", Context.MODE_PRIVATE)
    
    private val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    private val _currentUser = MutableStateFlow<User?>(getFirebaseCurrentUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        firebaseAuth?.addAuthStateListener { auth ->
            val fbUser = auth.currentUser
            if (fbUser != null) {
                val savedName = prefs.getString("user_name_${fbUser.uid}", null)
                val name = fbUser.displayName?.takeIf { it.isNotBlank() }
                    ?: savedName
                    ?: fbUser.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: "ব্যবহারকারী"
                _currentUser.value = User(
                    uid = fbUser.uid,
                    name = name,
                    email = fbUser.email ?: "",
                    photoUrl = fbUser.photoUrl?.toString(),
                    isPro = true
                )
            } else {
                _currentUser.value = null
            }
        }
    }

    private fun getFirebaseCurrentUser(): User? {
        val fbUser = firebaseAuth?.currentUser ?: return null
        val savedName = prefs.getString("user_name_${fbUser.uid}", null)
        val name = fbUser.displayName?.takeIf { it.isNotBlank() }
            ?: savedName
            ?: fbUser.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "ব্যবহারকারী"
        return User(
            uid = fbUser.uid,
            name = name,
            email = fbUser.email ?: "",
            photoUrl = fbUser.photoUrl?.toString(),
            isPro = true
        )
    }

    suspend fun loginWithEmail(email: String, pass: String): Result<User> {
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()

        if (trimmedEmail.isBlank() || trimmedPass.isBlank()) {
            return Result.failure(Exception("ইমেইল ও পাসওয়ার্ড সঠিকভাবে প্রদান করুন।"))
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(Exception("সঠিক ইমেইল ঠিকানা প্রদান করুন (যেমন: example@gmail.com)।"))
        }
        if (trimmedPass.length < 6) {
            return Result.failure(Exception("পাসওয়ার্ড অন্তত ৬ অক্ষরের হতে হবে।"))
        }

        val auth = firebaseAuth
            ?: return Result.failure(Exception("Firebase Authentication সেবা অনুপলব্ধ। অনুগ্রহ করে পরে চেষ্টা করুন।"))

        return try {
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPass).await()
            val fbUser = authResult.user
                ?: return Result.failure(Exception("প্রমাণীকরণ ব্যর্থ হয়েছে: কোনো ইউজার পাওয়া যায়নি।"))

            val savedName = prefs.getString("user_name_${fbUser.uid}", null)
            val name = fbUser.displayName?.takeIf { it.isNotBlank() }
                ?: savedName
                ?: trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

            val user = User(
                uid = fbUser.uid,
                name = name,
                email = fbUser.email ?: trimmedEmail,
                photoUrl = fbUser.photoUrl?.toString(),
                isPro = true
            )
            saveUserSession(user)
            Result.success(user)
        } catch (e: Exception) {
            val errorMsg = mapFirebaseAuthException(e)
            Result.failure(Exception(errorMsg))
        }
    }

    suspend fun signUpWithEmail(name: String, email: String, pass: String, confirmPass: String): Result<User> {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()
        val trimmedConfirmPass = confirmPass.trim()

        if (trimmedName.isBlank() || trimmedEmail.isBlank() || trimmedPass.isBlank()) {
            return Result.failure(Exception("সকল প্রয়োজনীয় তথ্য প্রদান করুন।"))
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(Exception("সঠিক ইমেইল ঠিকানা প্রদান করুন (যেমন: example@gmail.com)।"))
        }
        if (trimmedPass != trimmedConfirmPass) {
            return Result.failure(Exception("পাসওয়ার্ড দুটি মিলছে না।"))
        }
        if (trimmedPass.length < 6) {
            return Result.failure(Exception("পাসওয়ার্ড অন্তত ৬ অক্ষরের হতে হবে।"))
        }

        val auth = firebaseAuth
            ?: return Result.failure(Exception("Firebase Authentication সেবা অনুপলব্ধ। অনুগ্রহ করে পরে চেষ্টা করুন।"))

        return try {
            val authResult = auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPass).await()
            val fbUser = authResult.user
                ?: return Result.failure(Exception("অ্যাকাউন্ট তৈরি ব্যর্থ হয়েছে।"))

            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                fbUser.updateProfile(profileUpdates).await()
            } catch (_: Exception) {
                // Non-fatal
            }

            val user = User(
                uid = fbUser.uid,
                name = trimmedName.ifEmpty { fbUser.displayName ?: trimmedEmail.substringBefore("@") },
                email = fbUser.email ?: trimmedEmail,
                photoUrl = fbUser.photoUrl?.toString(),
                isPro = true
            )
            saveUserSession(user)
            Result.success(user)
        } catch (e: Exception) {
            val errorMsg = mapFirebaseAuthException(e)
            Result.failure(Exception(errorMsg))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(Exception("সঠিক ইমেইল ঠিকানা প্রদান করুন।"))
        }
        val auth = firebaseAuth
            ?: return Result.failure(Exception("Firebase Authentication সেবা অনুপলব্ধ। অনুগ্রহ করে পরে চেষ্টা করুন।"))

        return try {
            auth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = mapFirebaseAuthException(e)
            Result.failure(Exception(errorMsg))
        }
    }

    suspend fun signInWithGoogle(): Result<User> {
        return Result.failure(Exception("Google সাইন-ইন কনফিগারেশন প্রয়োজন। অনুগ্রহ করে আপনার ইমেইল ও পাসওয়ার্ড দিয়ে লগইন করুন।"))
    }

    suspend fun signInWithFacebook(): Result<User> {
        return Result.failure(Exception("Facebook সাইন-ইন কনফিগারেশন প্রয়োজন। অনুগ্রহ করে আপনার ইমেইল ও পাসওয়ার্ড দিয়ে লগইন করুন।"))
    }

    private fun saveUserSession(user: User) {
        prefs.edit()
            .putString("user_name_${user.uid}", user.name)
            .apply()
        _currentUser.value = user
    }

    fun logout() {
        prefs.edit().clear().apply()
        try {
            firebaseAuth?.signOut()
        } catch (_: Exception) {}
        _currentUser.value = null
    }

    private fun mapFirebaseAuthException(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException -> "এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি। অনুগ্রহ করে সঠিক ইমেইল দিন অথবা সাইন আপ করুন।"
            is FirebaseAuthInvalidCredentialsException -> "ভুল ইমেইল অথবা পাসওয়ার্ড প্রদান করা হয়েছে।"
            is FirebaseAuthUserCollisionException -> "এই ইমেইলটি দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট রয়েছে। অনুগ্রহ করে লগইন করুন।"
            is FirebaseAuthWeakPasswordException -> "পাসওয়ার্ড অন্তত ৬ অক্ষরের শক্তিশালী হতে হবে।"
            is FirebaseNetworkException -> "ইন্টারনেট সংযোগ নেই। অনুগ্রহ করে নেটওয়ার্ক চেক করে আবার চেষ্টা করুন।"
            is FirebaseAuthException -> e.localizedMessage ?: "Firebase প্রমাণীকরণ ব্যর্থ হয়েছে।"
            else -> e.localizedMessage ?: "লগইন প্রক্রিয়া ব্যর্থ হয়েছে।"
        }
    }
}
