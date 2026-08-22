package com.pumpkin.app.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches "Authorization: Bearer <Firebase ID token>" to every request to
 * the self-hosted server, which verifies it via firebase-admin (see
 * server/src/middleware/auth.js) instead of trusting a client-supplied uid.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val user = FirebaseAuth.getInstance().currentUser
        val token = user?.let {
            // Interceptor.intercept runs on OkHttp's own background thread,
            // so blocking here (rather than threading coroutines through
            // OkHttp) is the standard, safe approach.
            runCatching { Tasks.await(it.getIdToken(false)).token }.getOrNull()
        }
        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}
