package com.pumpkin.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.pumpkin.app.data.model.User
import com.pumpkin.app.data.remote.NetworkModule
import com.pumpkin.app.data.remote.api.ChatApi
import com.pumpkin.app.data.remote.api.dto.SyncUserRequest
import com.pumpkin.app.data.remote.api.dto.UpdateDisplayNameRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Wraps Firebase Auth (PRD 6) for identity only — sign-in/sign-up/token
 * issuance. Profile data (displayName, email lookup) used to live in
 * Firestore's `users` collection, updated via [ensureUserDocument] here;
 * that collection kept exhausting the Firestore Spark plan's daily write
 * quota during testing even at low volume, so it moved to the self-hosted
 * server's MongoDB (see server/src/routes/users.js) alongside chats/messages.
 * Firebase Authentication itself is a separate product from the Firestore
 * database and isn't subject to that quota, which is why sign-in/out still
 * goes through it here unchanged.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val api: ChatApi = NetworkModule.chatApi
) {
    val currentUserId: String? get() = auth.currentUser?.uid

    // AuthRepository is created once in PumpkinNavHost and outlives any single
    // screen. syncUser's write MUST run on this scope, not the caller's (e.g.
    // AuthViewModel.viewModelScope) — the moment Firebase Auth confirms
    // sign-in, PumpkinNavHost pops the auth route, which clears AuthViewModel
    // and cancels its scope. That was cancelling the profile-sync write
    // mid-flight before it ever reached the server.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Emits the current user immediately, then on every sign-in/sign-out. */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
        syncUserAsync(displayName)
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
        syncUserAsync(displayName = "")
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        syncUserAsync(displayName = "")
    }

    fun signOut() = auth.signOut()

    suspend fun getUser(uid: String): User? = runCatching {
        val dto = api.getCurrentUser()
        User(id = dto.id, email = dto.email, displayName = dto.displayName)
    }.getOrNull()

    /** The actual "Edit name" save — also cascades to every chat's denormalized name server-side. */
    suspend fun updateDisplayName(newName: String) {
        api.updateDisplayName(UpdateDisplayNameRequest(newName))
    }

    private fun syncUserAsync(displayName: String) {
        repositoryScope.launch {
            runCatching { api.syncUser(SyncUserRequest(displayName)) }
        }
    }
}
