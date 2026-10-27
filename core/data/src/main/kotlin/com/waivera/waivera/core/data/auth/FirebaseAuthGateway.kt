package com.waivera.waivera.core.data.auth

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.waivera.waivera.core.data.BuildConfig
import com.waivera.waivera.core.network.error.WaiveraError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Firebase values from Gradle properties or local.properties (never google-services.json). */
data class FirebaseConfig(val apiKey: String, val appId: String, val projectId: String, val senderId: String) {
    val missingKeys: List<String>
        get() = listOfNotNull(
            "waivera.firebase.apiKey".takeIf { apiKey.isBlank() },
            "waivera.firebase.appId".takeIf { appId.isBlank() },
            "waivera.firebase.projectId".takeIf { projectId.isBlank() },
        )

    companion object {
        fun fromBuildConfig() = FirebaseConfig(
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID,
        )
    }
}

/**
 * [AuthGateway] over the Firebase Auth SDK. The default FirebaseApp is initialised here from
 * [FirebaseConfig]; there is no google-services plugin. With missing values nothing is
 * initialised and every call fails with [WaiveraError.Configuration] instead of crashing.
 */
@Singleton
class FirebaseAuthGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthGateway {
    private val config = FirebaseConfig.fromBuildConfig()
    override val missingKeys: List<String> = config.missingKeys
    override val isConfigured: Boolean = missingKeys.isEmpty()

    private val _user = MutableStateFlow<AuthUser?>(null)
    override val user: StateFlow<AuthUser?> = _user.asStateFlow()

    private val auth: FirebaseAuth? by lazy {
        if (!isConfigured) return@lazy null
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(config.apiKey)
                    .setApplicationId(config.appId)
                    .setProjectId(config.projectId)
                    .apply { if (config.senderId.isNotBlank()) setGcmSenderId(config.senderId) }
                    .build(),
            )
        FirebaseAuth.getInstance(app).also { firebase ->
            // currentUser is restored synchronously from disk, so the first value is authoritative.
            _user.value = firebase.currentUser?.let { AuthUser(it.uid, it.email) }
            firebase.addAuthStateListener { current ->
                _user.value = current.currentUser?.let { AuthUser(it.uid, it.email) }
            }
        }
    }

    init {
        // Touch the lazy so the auth-state listener restores a persisted user at startup.
        auth
    }

    private fun requireAuth(): FirebaseAuth = auth ?: throw WaiveraError.Configuration(missingKeys)

    override suspend fun signIn(email: String, password: String) {
        mapped { requireAuth().signInWithEmailAndPassword(email.trim(), password).await() }
    }

    override fun signOut() {
        auth?.signOut()
        _user.value = null
    }

    override suspend fun sendPasswordReset(email: String) {
        mapped { requireAuth().sendPasswordResetEmail(email.trim()).await() }
    }

    override suspend fun reauthenticateAndUpdatePassword(currentPassword: String, newPassword: String) {
        mapped {
            val user = requireAuth().currentUser ?: throw WaiveraError.NotSignedIn()
            val email = user.email ?: throw WaiveraError.NotSignedIn()
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
            user.updatePassword(newPassword).await()
        }
    }

    override fun idTokenBlocking(forceRefresh: Boolean): String? {
        val user = auth?.currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(forceRefresh), TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS).token
        } catch (_: ExecutionException) {
            null
        } catch (_: TimeoutException) {
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseException) {
        throw map(e)
    }

    private fun map(e: FirebaseException): WaiveraError = when (e) {
        is FirebaseAuthWeakPasswordException -> WaiveraError.WeakPassword()
        is FirebaseAuthInvalidCredentialsException -> WaiveraError.InvalidCredentials()
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") WaiveraError.AccountDisabled() else WaiveraError.InvalidCredentials()
        is FirebaseAuthRecentLoginRequiredException -> WaiveraError.RecentLoginRequired()
        is FirebaseTooManyRequestsException -> WaiveraError.TooManyAttempts()
        is FirebaseNetworkException -> WaiveraError.Network(e)
        else -> WaiveraError.Unexpected(cause = e)
    }

    private companion object {
        const val TOKEN_TIMEOUT_SECONDS = 15L
    }
}
