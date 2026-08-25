package com.pumpkin.app.data.remote

/**
 * Which chat (if any) is the currently-open chat screen, updated by
 * ChatViewModel's init/onCleared. PumpkinMessagingService reads this to
 * decide whether to play a background-message tone: the app being
 * foregrounded is NOT enough to suppress it on its own — being foregrounded
 * on the chat LIST, the decoy calculator, or the lock screen still means the
 * user has no live view of a new message arriving, so it should still make
 * a sound. Only actually being inside the chat the message belongs to
 * (visibly reflected on screen in real time) should suppress it.
 */
object CurrentChatTracker {
    @Volatile
    var openChatId: String? = null
}
