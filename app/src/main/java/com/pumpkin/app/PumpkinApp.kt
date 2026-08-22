package com.pumpkin.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.pumpkin.app.data.local.AppDatabase

class PumpkinApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        // Firebase Auth + the Firestore `users` collection are still used
        // for identity — only chats/messages moved to the self-hosted
        // server/ (see ChatRepository). The client-side TTL sweep
        // (AutoDeleteManager) was removed for the same reason: the server
        // now runs its own equivalent cron job (server/src/ttlSweep.js)
        // against the real message store.
        FirebaseApp.initializeApp(this)
        database = AppDatabase.getInstance(this)
    }
}
