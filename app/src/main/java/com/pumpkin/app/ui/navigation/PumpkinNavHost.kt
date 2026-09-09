package com.pumpkin.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pumpkin.app.PumpkinApp
import com.pumpkin.app.data.repository.AuthRepository
import com.pumpkin.app.data.repository.ChatRepository
import com.pumpkin.app.ui.auth.AuthScreen
import com.pumpkin.app.ui.auth.AuthViewModel
import com.pumpkin.app.ui.calculator.CalculatorScreen
import com.pumpkin.app.ui.chat.ChatScreen
import com.pumpkin.app.ui.chat.ChatViewModel
import com.pumpkin.app.ui.chatlist.ChatListScreen
import com.pumpkin.app.ui.chatlist.ChatListViewModel
import com.pumpkin.app.ui.lock.ChangePinScreen
import com.pumpkin.app.ui.lock.LockScreen
import com.pumpkin.app.ui.profile.EditNameScreen
import com.pumpkin.app.ui.profile.EditNameViewModel

private object Routes {
    const val CALCULATOR = "calculator"
    const val LOCK = "lock"
    const val AUTH = "auth"
    const val CHAT_LIST = "chatlist"
    const val CHANGE_PIN = "change_pin"
    const val EDIT_NAME = "edit_name"
    const val CHAT = "chat/{chatId}"
    fun chat(chatId: String) = "chat/$chatId"
}

@Composable
fun PumpkinNavHost(activity: FragmentActivity) {
    val navController: NavHostController = rememberNavController()
    val repository = remember(activity) {
        val db = (activity.application as PumpkinApp).database
        ChatRepository(db.chatDao(), db.messageDao(), db.draftDao())
    }
    val authRepository = remember { AuthRepository() }
    // Single source of truth for "is someone signed in" — both the post-lock
    // routing decision and the auth screen's auto-advance read this.
    //
    // The `remember` here is load-bearing, not stylistic: observeAuthState()
    // builds a fresh callbackFlow (and registers a new FirebaseAuth
    // listener) every time it's called. Without remember, PumpkinNavHost
    // recomposing for any reason — even unrelated to auth — created a brand
    // new subscription and briefly reset currentUser to null (the initial
    // value) until the new listener's first callback landed. If a chat
    // screen's ViewModel factory happened to run during exactly one of those
    // windows, it permanently captured an EMPTY user id (viewModel() only
    // invokes the factory once per backstack entry) — which then crashed the
    // app the moment it tried to write a Firestore update with that empty id
    // as part of a field path (see ChatRepository.updateTimestampField).
    // `authStateResolved` disambiguates collectAsState's initial `null`
    // placeholder from a REAL "signed out" emission from Firebase. Right
    // after a cold start (the app process was killed in the background and
    // just relaunched — routine on Android), FirebaseAuth's persisted
    // session takes a moment to restore from disk, so `currentUser` can
    // briefly read null even though the user is actually still signed in.
    // Users were being "knocked out" — routed to the login screen right
    // after unlocking — specifically because the LOCK screen below used to
    // make its routing decision off that transient null instead of waiting
    // for the listener's first real callback (see onEach below).
    var authStateResolved by remember { mutableStateOf(false) }
    val currentUser by remember {
        authRepository.observeAuthState().onEach { authStateResolved = true }
    }.collectAsState(initial = null)

    // Registers this install's FCM token on every cold start with an
    // already-signed-in user (persisted Firebase session), not just right
    // after an explicit sign-in — otherwise a user who signed in once and
    // just reopens the app later would never get a token registered.
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            authRepository.registerFcmTokenAsync()
        }
    }

    NavHost(navController = navController, startDestination = Routes.CALCULATOR) {
        composable(Routes.CALCULATOR) {
            // Fixed palette regardless of the user's chosen app theme — the
            // decoy shouldn't visibly change look based on an in-app
            // preference only reachable after unlocking past it.
            androidx.compose.material3.MaterialTheme(colorScheme = com.pumpkin.app.ui.theme.CalculatorColors) {
                CalculatorScreen(
                    onSecretSequenceEntered = { navController.navigate(Routes.LOCK) }
                )
            }
        }
        composable(Routes.LOCK) {
            var unlocked by remember { mutableStateOf(false) }
            // A 3s safety valve — observeAuthState()'s listener is expected
            // to always fire at least once, but if it somehow never does,
            // this stops the user from being stuck on the lock screen
            // forever after entering a correct PIN/biometric.
            LaunchedEffect(Unit) {
                delay(3_000)
                authStateResolved = true
            }
            // Waits for both: the PIN/biometric to pass, AND the real
            // (not-placeholder) auth state to be known — see the
            // authStateResolved kdoc above for why the latter matters.
            LaunchedEffect(unlocked, authStateResolved) {
                if (unlocked && authStateResolved) {
                    val destination = if (currentUser != null) Routes.CHAT_LIST else Routes.AUTH
                    navController.navigate(destination) {
                        popUpTo(Routes.CALCULATOR) { inclusive = false }
                    }
                }
            }
            LockScreen(
                activity = activity,
                onUnlocked = { unlocked = true }
            )
        }
        composable(Routes.AUTH) {
            val viewModel: AuthViewModel = viewModel(
                factory = viewModelFactory { initializer { AuthViewModel(authRepository) } }
            )
            // Firebase Auth sign-in/sign-up/Google all funnel through
            // AuthRepository; once currentUser flips non-null here, advance
            // past this screen automatically.
            LaunchedEffect(currentUser) {
                if (currentUser != null) {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            }
            AuthScreen(viewModel = viewModel)
        }
        composable(Routes.CHAT_LIST) {
            val userId = currentUser?.uid.orEmpty()
            val viewModel: ChatListViewModel = viewModel(
                factory = viewModelFactory { initializer { ChatListViewModel(repository, userId) } }
            )
            ChatListScreen(
                viewModel = viewModel,
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onSignOut = {
                    authRepository.signOut()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.CHAT_LIST) { inclusive = true }
                    }
                },
                onChangePin = { navController.navigate(Routes.CHANGE_PIN) },
                onEditName = { navController.navigate(Routes.EDIT_NAME) }
            )
        }
        composable(Routes.CHANGE_PIN) {
            ChangePinScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.EDIT_NAME) {
            val userId = currentUser?.uid.orEmpty()
            val viewModel: EditNameViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { EditNameViewModel(authRepository, userId) }
                }
            )
            EditNameScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val userId = currentUser?.uid.orEmpty()
            val viewModel: ChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ChatViewModel(repository, chatId, userId) }
                }
            )
            ChatScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
