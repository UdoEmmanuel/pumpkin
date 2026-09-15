package com.pumpkin.app.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import com.pumpkin.app.R
import com.pumpkin.app.data.local.NotificationToneStore
import com.pumpkin.app.data.local.VoiceRecorder
import com.pumpkin.app.data.model.Message
import com.pumpkin.app.data.model.MessageStatus
import com.pumpkin.app.ui.theme.Avatar
import com.pumpkin.app.ui.theme.ReadReceiptBlue
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsState()
    val otherParticipantId by viewModel.otherParticipantId.collectAsState()
    val otherParticipantName by viewModel.otherParticipantName.collectAsState()
    val isPartnerTyping by viewModel.isPartnerTyping.collectAsState()
    val partnerPresence by viewModel.partnerPresence.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val input by viewModel.input.collectAsState()
    val currentUserId = viewModel.currentUserId
    val currentNickname by viewModel.currentNickname.collectAsState()
    val myNickname by viewModel.myNickname.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val nicknameError by viewModel.nicknameError.collectAsState()
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf("") }
    var contextMenuTarget by remember { mutableStateOf<MessageContextMenuTarget?>(null) }
    var showFullEmojiPicker by remember { mutableStateOf(false) }
    var deletePendingMessage by remember { mutableStateOf<Message?>(null) }

    val context = LocalContext.current
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val toneStore = remember { NotificationToneStore(context) }
    val toneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        toneStore.set(viewModel.chatId, uri?.toString())
    }

    val voiceRecorder = remember { VoiceRecorder(context) }
    // HELD: finger down, recording, not locked yet — release sends immediately
    // (the original quick behavior). LOCKED: recording continues hands-free,
    // with cancel/pause/stop controls. REVIEW: recording finalized but not
    // yet sent — can be previewed, deleted, or sent.
    var recordingPhase by remember { mutableStateOf(RecordingPhase.IDLE) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var isRecordingPaused by remember { mutableStateOf(false) }
    // Rolling amplitude samples (oldest first, newest at the end) driving the
    // live waveform — capped at WAVEFORM_SAMPLE_COUNT so it reads as bars
    // scrolling right-to-left as new ones arrive, same as WhatsApp's.
    val waveformSamples = remember { mutableStateListOf<Float>() }
    // Every sample for the CURRENT recording, uncapped — waveformSamples above
    // only ever holds the most recent few seconds (by design, for the live
    // scrolling view), so this is what gets downsampled into the fixed-size
    // waveform actually sent with the message (see WAVEFORM_SAMPLE_COUNT).
    val fullWaveformSamples = remember { mutableStateListOf<Float>() }
    var reviewFile by remember { mutableStateOf<File?>(null) }
    var reviewDurationMs by remember { mutableStateOf(0L) }
    var reviewWaveform by remember { mutableStateOf<List<Float>>(emptyList()) }
    var isReviewPlaying by remember { mutableStateOf(false) }
    var reviewPositionMs by remember { mutableStateOf(0) }
    val reviewPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasRecordPermission = granted
    }

    fun cancelRecording() {
        voiceRecorder.cancel()
        recordingPhase = RecordingPhase.IDLE
        isRecordingPaused = false
    }

    /** Release without locking/cancelling — the original quick "release to send" behavior. */
    fun sendRecordingDirectly() {
        val result = voiceRecorder.stop()
        recordingPhase = RecordingPhase.IDLE
        isRecordingPaused = false
        if (result == null) return
        val (file, durationMs) = result
        // Anything under ~600ms is almost certainly an accidental tap, not
        // an intended note — discard rather than send a near-silent blip.
        if (durationMs < 600) {
            file.delete()
            return
        }
        val waveform = downsampleWaveform(fullWaveformSamples, WAVEFORM_SAMPLE_COUNT)
        val base64 = file.readBytes().let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        file.delete()
        viewModel.sendVoiceNote(base64, durationMs, waveform)
    }

    /** Stops recording (locked flow) but doesn't send — moves to REVIEW so it can be listened to first. */
    fun stopRecordingToReview() {
        val result = voiceRecorder.stop()
        isRecordingPaused = false
        if (result == null) {
            recordingPhase = RecordingPhase.IDLE
            return
        }
        val (file, durationMs) = result
        if (durationMs < 600) {
            file.delete()
            recordingPhase = RecordingPhase.IDLE
            return
        }
        reviewFile = file
        reviewDurationMs = durationMs
        reviewWaveform = downsampleWaveform(fullWaveformSamples, WAVEFORM_SAMPLE_COUNT)
        recordingPhase = RecordingPhase.REVIEW
    }

    fun releaseReviewPlayer() {
        reviewPlayerRef.value?.release()
        reviewPlayerRef.value = null
        isReviewPlaying = false
        reviewPositionMs = 0
    }

    fun discardReview() {
        releaseReviewPlayer()
        reviewFile?.delete()
        reviewFile = null
        reviewWaveform = emptyList()
        recordingPhase = RecordingPhase.IDLE
    }

    fun sendReview() {
        val file = reviewFile ?: return
        releaseReviewPlayer()
        val base64 = file.readBytes().let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        val durationMs = reviewDurationMs
        val waveform = reviewWaveform
        file.delete()
        reviewFile = null
        recordingPhase = RecordingPhase.IDLE
        viewModel.sendVoiceNote(base64, durationMs, waveform)
    }

    fun toggleReviewPlayback(coroutineScope: kotlinx.coroutines.CoroutineScope) {
        val file = reviewFile ?: return
        val current = reviewPlayerRef.value
        if (isReviewPlaying) {
            current?.pause()
            isReviewPlaying = false
            return
        }
        val player = current ?: try {
            android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    isReviewPlaying = false
                    reviewPositionMs = 0
                }
            }.also { reviewPlayerRef.value = it }
        } catch (e: Exception) {
            // Same reasoning as VoiceNoteBubble.ensurePlayer() — don't crash
            // over a preview-playback failure, just leave the review UI as
            // is (the delete/send buttons still work) and log it.
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            return
        }
        if (reviewPositionMs > 0) player.seekTo(reviewPositionMs)
        player.start()
        isReviewPlaying = true
        coroutineScope.launch {
            while (isReviewPlaying) {
                reviewPositionMs = reviewPlayerRef.value?.currentPosition ?: 0
                delay(200)
            }
        }
    }

    // Leaving the chat screen mid-recording (back button, app backgrounded)
    // shouldn't leave MediaRecorder/MediaPlayer instances running or an
    // orphaned temp file behind.
    DisposableEffect(Unit) {
        onDispose {
            if (recordingPhase == RecordingPhase.HELD || recordingPhase == RecordingPhase.LOCKED) {
                voiceRecorder.cancel()
            }
            reviewFile?.delete()
            reviewPlayerRef.value?.release()
        }
    }

    // Keyed only on the phase (not on isRecordingPaused) — pause/resume just
    // gates whether this tick increments the counter, read live off current
    // state on every iteration, rather than tearing the ticker down and
    // rebuilding it on every pause toggle.
    LaunchedEffect(recordingPhase) {
        if (recordingPhase != RecordingPhase.HELD && recordingPhase != RecordingPhase.LOCKED) {
            recordingSeconds = 0
            return@LaunchedEffect
        }
        while (recordingPhase == RecordingPhase.HELD || recordingPhase == RecordingPhase.LOCKED) {
            delay(1000)
            if (!isRecordingPaused) {
                recordingSeconds++
                if (recordingSeconds * 1000L >= VoiceRecorder.MAX_DURATION_MS) {
                    stopRecordingToReview()
                }
            }
        }
    }

    // Live waveform — polls MediaRecorder's own peak-amplitude counter (there's
    // no raw PCM access with this MediaRecorder-based setup, but this is the
    // same source WhatsApp-style waveforms are typically driven from) every
    // 100ms and appends a normalized sample, capped to WAVEFORM_SAMPLE_COUNT
    // so older bars fall off the left as new ones arrive on the right. Frozen
    // (no new samples) while paused, same as the seconds counter above.
    LaunchedEffect(recordingPhase) {
        if (recordingPhase != RecordingPhase.HELD && recordingPhase != RecordingPhase.LOCKED) {
            return@LaunchedEffect
        }
        while (recordingPhase == RecordingPhase.HELD || recordingPhase == RecordingPhase.LOCKED) {
            delay(100)
            if (!isRecordingPaused) {
                val level = (voiceRecorder.getMaxAmplitude() / 32767f).coerceIn(0f, 1f)
                waveformSamples.add(level)
                if (waveformSamples.size > WAVEFORM_SAMPLE_COUNT) waveformSamples.removeAt(0)
                fullWaveformSamples.add(level)
            }
        }
    }

    // PRD 4.4: mark every unread-by-me message as read as soon as it's
    // visible on screen. The `!it.readAt.containsKey(currentUserId)` guard is
    // load-bearing, not an optimization: without it, this fires on every
    // recomposition of `messages` — including the recomposition caused by
    // the markRead write itself echoing back through the Firestore listener
    // into Room. That created an unthrottled read -> write -> read loop that
    // never settled, hammering Firestore continuously until the app ran out
    // of memory and crashed (confirmed via a captured OutOfMemoryError).
    LaunchedEffect(messages) {
        messages
            .filter { it.senderId != currentUserId && !it.readAt.containsKey(currentUserId) }
            .forEach { viewModel.onMessageRead(it.id) }
    }

    // Track every message present during this visit — including our own
    // sent ones — and fire the "exited" event for all of them exactly once
    // when this screen truly leaves composition. Own messages are included
    // deliberately: the server requires EVERY participant (sender included)
    // to have exited before a message can be deleted, not just the
    // recipient — otherwise a message could vanish while its own sender is
    // still sitting in the chat, just because the recipient read and left
    // first (see server/src/messageEligibility.js).
    //
    // This is keyed on Unit, not on `messages`: keying on the message list
    // meant every new message tore down and recreated the effect, firing
    // "exited" immediately instead of on actual navigation-away. That
    // satisfied the "read AND exited" condition right away, deleting
    // messages the instant they were read — and re-firing markExitedAfterRead
    // on an already-deleted message id, which crashed the app.
    val seenMessageIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(messages) {
        messages.forEach {
            if (it.id !in seenMessageIds) seenMessageIds.add(it.id)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            seenMessageIds.forEach { viewModel.onExitAfterRead(it) }
        }
    }
    // The above only fires on in-app navigation away from this screen —
    // backgrounding the app (home button, switching apps, the OS killing the
    // process later) leaves this composable in place, so "exited" never got
    // reported and auto-delete could never trigger for a chat left open like
    // that. ON_STOP covers exactly that case: the moment the app stops being
    // visible, whatever's been read so far on this screen counts as exited,
    // same as actually navigating back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                seenMessageIds.forEach { viewModel.onExitAfterRead(it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A Box (rather than Scaffold being the root) so the iOS-style long-press
    // context menu below can render as a full-screen overlay above every
    // other element on this screen, including the TopAppBar and input row.
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = otherParticipantName ?: "?", size = 32)
                        Column(
                            // weight(fill = false) + the Text's own maxLines/overflow
                            // below are both needed: without a bounded width a long
                            // name (these are currently email addresses) just wraps
                            // instead of truncating, stretching the whole header.
                            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false)
                        ) {
                            Text(
                                text = otherParticipantName ?: "",
                                // fontSize/lineHeight alone still leave visible gap from
                                // Android's legacy font padding (ascent/descent reserved
                                // beyond the nominal em box) — includeFontPadding = false
                                // plus a trimmed LineHeightStyle is what actually collapses
                                // it, matching the tight two-line header this was meant to be.
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    lineHeight = 16.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both
                                    )
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Same slot, one at a time: typing takes priority
                            // over online/last-seen, matching how every other
                            // chat app treats this — you don't need to know
                            // "online since when" when they're typing to you
                            // right now.
                            val statusText = when {
                                isPartnerTyping -> stringResource(R.string.chat_typing)
                                partnerPresence?.online == true -> stringResource(R.string.chat_online)
                                partnerPresence?.lastSeenAt != null ->
                                    formatLastSeen(partnerPresence!!.lastSeenAt!!)
                                else -> null
                            }
                            statusText?.let {
                                Text(
                                    text = it,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both
                                        )
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showHeaderMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = showHeaderMenu, onDismissRequest = { showHeaderMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_nickname_menu)) },
                            onClick = {
                                showHeaderMenu = false
                                nicknameInput = currentNickname ?: ""
                                showNicknameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_notification_tone_menu)) },
                            onClick = {
                                showHeaderMenu = false
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    val existing = toneStore.get(viewModel.chatId)
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        if (existing != null) android.net.Uri.parse(existing) else null
                                    )
                                }
                                toneLauncher.launch(intent)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            myNickname?.let {
                Text(
                    text = stringResource(R.string.chat_my_nickname_banner, it),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            fun scrollToBottom() {
                coroutineScope.launch {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                }
            }
            // Without this, a new message (yours or theirs) lands off-screen
            // at the bottom of the list with no indication it arrived —
            // you'd have to know to scroll down manually to see it.
            LaunchedEffect(messages.size) { scrollToBottom() }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                items(messages, key = { it.id }) { message: Message ->
                    MessageRow(
                        message = message,
                        currentUserId = currentUserId,
                        otherParticipantId = otherParticipantId,
                        otherParticipantName = otherParticipantName,
                        // Every OTHER bubble blurs while the context menu is
                        // open, so the long-pressed one — left completely
                        // alone, still the real list item — is what reads as
                        // "selected" rather than a copy laid on top of it.
                        isBlurred = contextMenuTarget != null && contextMenuTarget?.message?.id != message.id,
                        onReply = { viewModel.startReply(message) },
                        onLongPress = { bounds ->
                            if (message.deletedAt == null) {
                                contextMenuTarget = MessageContextMenuTarget(
                                    message = message,
                                    bounds = bounds,
                                    isOwnMessage = message.senderId == currentUserId
                                )
                            }
                        }
                    )
                }
            }
            sendError?.let {
                Text(
                    text = stringResource(R.string.chat_send_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            replyingTo?.let { reply ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (reply.senderId == currentUserId) {
                                stringResource(R.string.chat_reply_to_self)
                            } else {
                                otherParticipantName ?: ""
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = reply.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    }
                    IconButton(onClick = { viewModel.cancelReply() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_reply_cancel))
                    }
                }
            }
            if (editingMessage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.chat_editing_message),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.cancelEdit() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_reply_cancel))
                    }
                }
            }
            // Whether the mic button's press-and-hold gesture area is the
            // thing on screen right now — true both before locking (IDLE,
            // no typed text) and during HELD. This condition must stay
            // identical across that IDLE->HELD transition so the same
            // pointerInput node (and its in-flight awaitEachGesture
            // coroutine tracking the drag) survives the recomposition that
            // flips recordingPhase — swapping to a differently-shaped
            // branch here would tear the gesture down mid-touch.
            val showMicGestureArea =
                recordingPhase == RecordingPhase.HELD ||
                    (recordingPhase == RecordingPhase.IDLE && input.isBlank() && editingMessage == null)
            val lockThresholdPx = with(density) { 64.dp.toPx() }
            val cancelThresholdPx = with(density) { 96.dp.toPx() }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (recordingPhase) {
                    RecordingPhase.IDLE -> {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                viewModel.onInputChanged(it)
                            },
                            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            // Tapping the field is what opens the keyboard, so this is
                            // the actual moment it's about to cover part of the
                            // screen — jump to the latest message right then instead
                            // of making the user scroll down manually to see it.
                            modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) scrollToBottom() }
                        )
                    }
                    RecordingPhase.HELD -> {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                            Text(
                                text = stringResource(
                                    R.string.chat_recording_duration,
                                    recordingSeconds / 60, recordingSeconds % 60
                                ),
                                modifier = Modifier.padding(start = 10.dp)
                            )
                            VoiceWaveform(
                                samples = waveformSamples,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .padding(start = 12.dp)
                            )
                        }
                    }
                    RecordingPhase.LOCKED -> {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { cancelRecording() }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.chat_recording_cancel),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (isRecordingPaused) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = stringResource(
                                    R.string.chat_recording_duration,
                                    recordingSeconds / 60, recordingSeconds % 60
                                ),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            VoiceWaveform(
                                samples = waveformSamples,
                                color = if (isRecordingPaused) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = {
                                if (isRecordingPaused) {
                                    voiceRecorder.resume()
                                    isRecordingPaused = false
                                } else {
                                    voiceRecorder.pause()
                                    isRecordingPaused = true
                                }
                            }) {
                                Icon(
                                    if (isRecordingPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = stringResource(
                                        if (isRecordingPaused) R.string.chat_recording_resume
                                        else R.string.chat_recording_pause
                                    )
                                )
                            }
                        }
                    }
                    RecordingPhase.REVIEW -> {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { discardReview() }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.chat_recording_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { toggleReviewPlayback(coroutineScope) }) {
                                Icon(
                                    if (isReviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(
                                        if (isReviewPlaying) R.string.chat_voice_note_pause
                                        else R.string.chat_voice_note_play
                                    )
                                )
                            }
                            val displaySeconds =
                                (if (isReviewPlaying || reviewPositionMs > 0) reviewPositionMs else reviewDurationMs.toInt()) / 1000
                            Text(
                                text = stringResource(
                                    R.string.chat_recording_duration,
                                    displaySeconds / 60, displaySeconds % 60
                                ),
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (recordingPhase == RecordingPhase.HELD) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        recordingPhase == RecordingPhase.LOCKED -> {
                            IconButton(onClick = { stopRecordingToReview() }) {
                                Icon(
                                    Icons.Filled.Done,
                                    contentDescription = stringResource(R.string.chat_recording_confirm),
                                    tint = Color.White
                                )
                            }
                        }
                        recordingPhase == RecordingPhase.REVIEW -> {
                            IconButton(onClick = { sendReview() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.chat_recording_send),
                                    tint = Color.White
                                )
                            }
                        }
                        showMicGestureArea -> {
                            // Press-and-hold to record, release to send. Slide
                            // up past the threshold to lock into hands-free
                            // recording; slide left to cancel — same gesture
                            // vocabulary as WhatsApp/iMessage voice notes.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(hasRecordPermission) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (!hasRecordPermission) {
                                                down.consume()
                                                waitForUpOrCancellation()
                                                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                return@awaitEachGesture
                                            }
                                            if (voiceRecorder.start() == null) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    R.string.chat_recording_start_failed,
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                return@awaitEachGesture
                                            }
                                            waveformSamples.clear()
                                            fullWaveformSamples.clear()
                                            recordingPhase = RecordingPhase.HELD
                                            isRecordingPaused = false
                                            var dx = 0f
                                            var dy = 0f
                                            var locked = false
                                            down.consume()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) {
                                                    change.consume()
                                                    break
                                                }
                                                val delta = change.positionChange()
                                                dx += delta.x
                                                dy += delta.y
                                                change.consume()
                                                if (!locked && dy < -lockThresholdPx) {
                                                    locked = true
                                                    recordingPhase = RecordingPhase.LOCKED
                                                }
                                            }
                                            if (!locked) {
                                                if (dx < -cancelThresholdPx) {
                                                    cancelRecording()
                                                } else {
                                                    sendRecordingDirectly()
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = stringResource(R.string.chat_record_voice_note),
                                    tint = Color.White
                                )
                            }
                        }
                        else -> {
                            IconButton(onClick = { viewModel.onSendClicked(input) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.chat_send),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = {
                showNicknameDialog = false
                viewModel.clearNicknameError()
            },
            title = { Text(stringResource(R.string.chat_nickname_menu)) },
            text = {
                Column {
                    Text(stringResource(R.string.chat_nickname_hint_text))
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        label = { Text(stringResource(R.string.chat_nickname_field_label)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    nicknameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNickname(nicknameInput)
                    showNicknameDialog = false
                }) {
                    Text(stringResource(R.string.chat_nickname_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNicknameDialog = false
                    viewModel.clearNicknameError()
                }) {
                    Text(stringResource(R.string.chatlist_new_chat_cancel))
                }
            }
        )
    }

    if (showFullEmojiPicker) {
        val target = contextMenuTarget?.message
        AlertDialog(
            onDismissRequest = {
                showFullEmojiPicker = false
                contextMenuTarget = null
            },
            title = { Text(stringResource(R.string.chat_reaction_more)) },
            text = {
                LazyVerticalGridEmojiPicker(emojis = FULL_EMOJI_SET) { emoji ->
                    if (target != null) viewModel.onReact(target.id, emoji)
                    showFullEmojiPicker = false
                    contextMenuTarget = null
                }
            },
            confirmButton = {}
        )
    }

    deletePendingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { deletePendingMessage = null },
            title = { Text(stringResource(R.string.chat_delete_message)) },
            text = { Text(stringResource(R.string.chat_delete_message_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(target)
                    deletePendingMessage = null
                }) {
                    Text(stringResource(R.string.chat_delete_message), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePendingMessage = null }) {
                    Text(stringResource(R.string.chatlist_new_chat_cancel))
                }
            }
        )
    }

    // Rendered last (topmost z-order) inside the wrapping Box — the
    // iOS/WhatsApp-style long-press context menu: quick reactions + "+" and
    // Reply/Copy/Delete (Edit too, for own messages), anchored to wherever
    // the long-pressed bubble actually is on screen.
    contextMenuTarget?.let { target ->
        MessageContextMenu(
            target = target,
            onReact = { emoji ->
                viewModel.onReact(target.message.id, emoji)
                contextMenuTarget = null
            },
            onMoreReactions = { showFullEmojiPicker = true },
            onReply = {
                viewModel.startReply(target.message)
                contextMenuTarget = null
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(target.message.text))
                contextMenuTarget = null
            },
            onDelete = {
                deletePendingMessage = target.message
                contextMenuTarget = null
            },
            onEdit = if (target.message.senderId == currentUserId) {
                {
                    viewModel.startEdit(target.message)
                    contextMenuTarget = null
                }
            } else null,
            onDismiss = { contextMenuTarget = null }
        )
    }
    } // closes the root Box wrapping Scaffold (see top of this function)
}

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

private val FULL_EMOJI_SET = listOf(
    "👍", "👎", "❤️", "😂", "😮", "😢", "🙏", "🔥", "🎉", "😍",
    "😘", "😊", "😉", "😢", "😡", "🤔", "😴", "🤗", "👏", "🙌",
    "💯", "✅", "❌", "⭐", "💔", "😱", "🥳", "🤝", "👀", "💀"
)

@Composable
private fun LazyVerticalGridEmojiPicker(emojis: List<String>, onPick: (String) -> Unit) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
        modifier = Modifier.fillMaxWidth()
    ) {
        gridItems(emojis) { emoji ->
            Text(
                text = emoji,
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable { onPick(emoji) }
                    .padding(8.dp)
            )
        }
    }
}

/**
 * HELD: finger down, recording, not locked — release sends immediately (the
 * original quick behavior). LOCKED: slid up past the lock threshold, keeps
 * recording hands-free with cancel/pause/stop controls. REVIEW: recording
 * finalized but not yet sent — can be previewed, deleted, or sent.
 */
private enum class RecordingPhase { IDLE, HELD, LOCKED, REVIEW }

// Fixed window of bars shown at once — new samples appended on the right push
// the oldest ones off the left, giving the "scrolling" look.
private const val WAVEFORM_SAMPLE_COUNT = 40

/**
 * Live recording waveform: one vertical bar per amplitude sample, newest at
 * the right edge. [samples] is oldest-first and already capped by the
 * caller; bar height is a fraction of the available height so it reads as a
 * real-time level meter rather than a fixed decoration.
 */
@Composable
private fun VoiceWaveform(samples: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val slot = size.width / WAVEFORM_SAMPLE_COUNT
        // Right-align: the newest sample always sits at the right edge, so a
        // partially-filled window (start of a recording) leaves empty space
        // on the left instead of stretching the few bars there are.
        val startIndex = WAVEFORM_SAMPLE_COUNT - samples.size
        val barWidth = slot * 0.5f
        val minHeight = size.height * 0.08f
        samples.forEachIndexed { i, level ->
            val x = (startIndex + i) * slot + (slot - barWidth) / 2f
            val barHeight = (level * size.height).coerceAtLeast(minHeight)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Reduces a (potentially long) list of raw amplitude samples down to a fixed
 * [targetCount] by averaging each of [targetCount] equal-sized buckets —
 * this is what turns a whole recording's worth of 100ms ticks into the
 * fixed-size waveform actually stored with the message (see
 * WAVEFORM_SAMPLE_COUNT). Returns as-is if there's already fewer samples
 * than the target.
 */
private fun downsampleWaveform(samples: List<Float>, targetCount: Int): List<Float> {
    if (samples.isEmpty()) return emptyList()
    if (samples.size <= targetCount) return samples.toList()
    val bucketSize = samples.size.toDouble() / targetCount
    return (0 until targetCount).map { i ->
        val start = (i * bucketSize).toInt()
        val end = (((i + 1) * bucketSize).toInt()).coerceAtMost(samples.size).coerceAtLeast(start + 1)
        samples.subList(start, end).average().toFloat()
    }
}

/**
 * Deterministic per-message placeholder waveform for voice notes sent before
 * this feature existed (so `message.waveform` is empty) — varied enough to
 * not look like a rendering bug, stable across recompositions since it's
 * seeded from the message id rather than actually random.
 */
private fun syntheticWaveform(seed: String): List<Float> {
    val random = java.util.Random(seed.hashCode().toLong())
    return List(WAVEFORM_SAMPLE_COUNT) { 0.25f + random.nextFloat() * 0.65f }
}

/**
 * Static playback waveform: one bar per stored amplitude sample, colored by
 * [progress] (0f..1f, current position / duration) — bars before the
 * playhead use [playedColor], the rest [unplayedColor] — plus a small round
 * scrubber thumb at the playhead itself. This is the WhatsApp-style bar
 * meter shown on a sent/received voice note, as opposed to [VoiceWaveform]
 * above which is the live level meter shown while actively recording.
 */
@Composable
private fun PlaybackWaveform(
    samples: List<Float>,
    progress: Float,
    playedColor: Color,
    unplayedColor: Color,
    modifier: Modifier = Modifier,
    // Fraction (0f..1f) of the tap/drag position along the waveform's
    // width — null disables seeking (e.g. nothing to seek in yet). Fires on
    // press and continuously while dragging, so a plain tap seeks and a
    // drag scrubs, regardless of whether playback is currently running.
    onSeek: ((Float) -> Unit)? = null
) {
    Canvas(
        modifier = modifier.then(
            if (onSeek != null) {
                Modifier.pointerInput(onSeek) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onSeek((down.position.x / size.width).coerceIn(0f, 1f))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                            change.consume()
                        }
                    }
                }
            } else {
                Modifier
            }
        )
    ) {
        if (samples.isEmpty()) return@Canvas
        val count = samples.size
        val slot = size.width / count
        val barWidth = slot * 0.55f
        val minHeight = size.height * 0.15f
        val progressX = size.width * progress.coerceIn(0f, 1f)
        samples.forEachIndexed { i, level ->
            val x = i * slot + (slot - barWidth) / 2f
            val barHeight = (level * size.height).coerceAtLeast(minHeight)
            drawRoundRect(
                color = if (x <= progressX) playedColor else unplayedColor,
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
        drawCircle(
            color = playedColor,
            radius = size.height * 0.24f,
            center = Offset(progressX.coerceIn(0f, size.width), size.height / 2f)
        )
    }
}

private data class MessageContextMenuTarget(
    val message: Message,
    val bounds: Rect,
    val isOwnMessage: Boolean
)

/**
 * The long-press context menu: a quick-reaction pill row above the
 * long-pressed bubble and a Reply/Copy/[Edit]/Delete menu below it, anchored
 * to [target]'s actual on-screen position, matching the iOS/WhatsApp
 * long-press UI rather than a centered modal dialog.
 *
 * Deliberately doesn't render any copy of the bubble itself — the real list
 * item stays exactly where it is and is the thing the user sees; every
 * OTHER row blurs (see the `isBlurred` param threaded into MessageRow from
 * ChatScreen) so the selected one reads as "picked" by contrast alone,
 * without a second, easy-to-misalign copy of it drawn on top.
 */
@Composable
private fun MessageContextMenu(
    target: MessageContextMenuTarget,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val menuWidthPx = with(density) { 190.dp.toPx() }
        val pillHeightEstimatePx = with(density) { 52.dp.toPx() }
        val menuHeightEstimatePx = with(density) {
            (52 * (if (onEdit != null) 4 else 3)).dp.toPx()
        }

        // Anchor x: same left edge the bubble itself uses, but for own
        // (right-aligned) messages anchor from the bubble's right edge
        // instead so the popup hugs the same side of the screen the
        // message does — then clamp so neither the pill nor the menu ever
        // runs off either edge.
        val anchorLeftPx = if (target.isOwnMessage) {
            (target.bounds.right - menuWidthPx).coerceAtLeast(gapPx)
        } else {
            target.bounds.left
        }.coerceIn(gapPx, (maxWidthPx - menuWidthPx - gapPx).coerceAtLeast(gapPx))

        val pillTopPx = (target.bounds.top - pillHeightEstimatePx - gapPx).coerceAtLeast(gapPx)
        val menuTopPx = (target.bounds.top + target.bounds.height + gapPx)
            .coerceAtMost((maxHeightPx - menuHeightEstimatePx - gapPx).coerceAtLeast(gapPx))

        // Transparent tap-catcher, not a visible scrim — the blurred/dimmed
        // siblings behind the real bubble are what carry the "dimmed
        // background" look now, so this is purely for tap-outside-to-dismiss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )

        Row(
            modifier = Modifier
                .offset { IntOffset(anchorLeftPx.roundToInt(), pillTopPx.roundToInt()) }
                .width(with(density) { menuWidthPx.toDp() })
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable { onReact(emoji) }
                        .padding(2.dp)
                )
            }
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.chat_reaction_more),
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onMoreReactions() }
            )
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(anchorLeftPx.roundToInt(), menuTopPx.roundToInt()) }
                .width(with(density) { menuWidthPx.toDp() })
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
        ) {
            ContextMenuRow(
                label = stringResource(R.string.chat_reply_action),
                icon = Icons.AutoMirrored.Filled.Reply,
                onClick = onReply
            )
            Divider()
            ContextMenuRow(
                label = stringResource(R.string.chat_copy_message),
                icon = Icons.Filled.ContentCopy,
                onClick = onCopy
            )
            if (onEdit != null) {
                Divider()
                ContextMenuRow(
                    label = stringResource(R.string.chat_edit_message),
                    icon = Icons.Filled.Edit,
                    onClick = onEdit
                )
            }
            Divider()
            ContextMenuRow(
                label = stringResource(R.string.chat_delete_message),
                icon = Icons.Filled.Delete,
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ContextMenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    // Unspecified left the icon rendering in the vector's own raw color
    // (effectively black) against the menu's dark surface — barely visible.
    // Delete already passed an explicit error tint and read fine; Reply/
    // Copy/Edit need the same explicit-color treatment.
    tint: Color = Color.Unspecified
) {
    val resolvedTint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = resolvedTint)
        Icon(icon, contentDescription = null, tint = resolvedTint, modifier = Modifier.size(20.dp))
    }
}

// iOS/WhatsApp-style bubbles wrap their content instead of stretching edge to
// edge — capped at roughly three-quarters of the screen so only long text
// actually reaches that width, matching the reference screenshot.
private const val BUBBLE_MAX_WIDTH_FRACTION = 0.78f

@Composable
private fun MessageRow(
    message: Message,
    currentUserId: String,
    otherParticipantId: String?,
    otherParticipantName: String?,
    isBlurred: Boolean,
    onReply: () -> Unit,
    onLongPress: (Rect) -> Unit
) {
    val isOwnMessage = message.senderId == currentUserId
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val replyThresholdPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 84.dp.toPx() }
    // Updated on every layout pass so a long-press always reports where the
    // bubble is *right now* — read (not written) inside the gesture handler.
    var bubbleBounds by remember { mutableStateOf(Rect.Zero) }

    Column(
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            // Every other bubble blurs (and dims slightly) while the
            // long-press menu is open on this one — that contrast is what
            // makes the selected bubble read as "picked", not a duplicate
            // laid on top of it (see MessageContextMenu).
            .then(if (isBlurred) Modifier.blur(28.dp).alpha(0.5f) else Modifier)
            .onGloballyPositioned { bubbleBounds = it.boundsInRoot() }
            // Swipe right anywhere on a bubble to reply — snaps back
            // afterward rather than actually moving the message, same
            // interaction as WhatsApp/Telegram's reply gesture.
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value > replyThresholdPx) onReply()
                            offsetX.animateTo(0f)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, maxDragPx))
                        }
                    }
                )
            }
            // Separate pointerInput for long-press-to-react — the drag
            // detector above consumes move events once a horizontal drag is
            // recognized, which is what keeps a swipe-to-reply gesture from
            // also triggering this long-press (no significant movement means
            // this fires instead). A deleted message has nothing left to
            // react/reply/copy/delete, so it doesn't offer the menu at all.
            .pointerInput(message.id) {
                if (message.deletedAt == null) {
                    detectTapGestures(onLongPress = { onLongPress(bubbleBounds) })
                }
            }
    ) {
        Box {
            if (message.deletedAt != null) {
                DeletedBubbleContent(isOwnMessage = isOwnMessage)
            } else {
                BubbleContent(
                    message = message,
                    isOwnMessage = isOwnMessage,
                    currentUserId = currentUserId,
                    otherParticipantName = otherParticipantName
                )
            }
            if (message.reactions.isNotEmpty()) {
                val grouped = message.reactions.values.groupingBy { it }.eachCount()
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 10.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    grouped.forEach { (emoji, count) ->
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp)
        ) {
            Text(
                text = formatMessageTime(message.sentAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message.editedAt != null && message.deletedAt == null) {
                Text(
                    text = stringResource(R.string.chat_edited_label),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            // A read receipt only makes sense on messages *I* sent — it should
            // reflect whether the *recipient* has read it, not my own read
            // state (previously this always checked currentUserId, so an
            // outgoing message could never show anything but "Sent").
            if (isOwnMessage && otherParticipantId != null) {
                StatusTicks(status = message.statusFor(otherParticipantId))
            }
        }
    }
}

/**
 * The actual bubble box — background, reply quote, text/voice content.
 * Pulled out of [MessageRow] so [MessageContextMenu] can render an identical
 * (non-dimmed) copy of it over the long-pressed message.
 */
@Composable
private fun BubbleContent(
    message: Message,
    isOwnMessage: Boolean,
    currentUserId: String,
    otherParticipantName: String?
) {
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    Box(
        modifier = Modifier
            .widthIn(max = (screenWidthDp * BUBBLE_MAX_WIDTH_FRACTION).dp)
            .background(
                if (isOwnMessage) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            if (message.replyToText != null) {
                val replySenderLabel = if (message.replyToSenderId == currentUserId) {
                    stringResource(R.string.chat_reply_to_self)
                } else {
                    otherParticipantName ?: ""
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            (if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                .copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(6.dp)
                ) {
                    Text(
                        text = replySenderLabel,
                        fontSize = 11.sp,
                        color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = message.replyToText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (message.isVoiceNote) {
                VoiceNoteBubble(message = message, isOwnMessage = isOwnMessage)
            } else {
                Text(
                    text = message.text,
                    color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Placeholder shown in place of a message's content once it's been deleted for everyone. */
@Composable
private fun DeletedBubbleContent(isOwnMessage: Boolean) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.chat_message_deleted),
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// WhatsApp-style voice note bubble width — wide enough for a legible
// waveform, narrow enough to still read as a chat bubble rather than a
// media player docked in the conversation.
private val VOICE_NOTE_BUBBLE_WIDTH = 220.dp

@Composable
private fun VoiceNoteBubble(message: Message, isOwnMessage: Boolean) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var positionMs by remember(message.id) { mutableStateOf(0) }
    val playerRef = remember(message.id) { mutableStateOf<android.media.MediaPlayer?>(null) }
    val durationMs = (message.audioDurationMs ?: 0L).toInt()
    // Older messages sent before this feature existed have no stored
    // waveform — fall back to a deterministic per-message placeholder rather
    // than rendering an empty bar.
    val samples = remember(message.id, message.waveform) {
        message.waveform.ifEmpty { syntheticWaveform(message.id) }
    }

    DisposableEffect(message.id) {
        onDispose {
            playerRef.value?.release()
            playerRef.value = null
        }
    }

    val tint = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val dimTint = tint.copy(alpha = 0.35f)
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    // Lazily decodes the base64 audio to a cache file and creates the
    // MediaPlayer on first use (by either the play button or a seek), but
    // never starts it — separated out of the play button's onClick so
    // seeking works identically whether or not playback has started yet.
    // Returns null (instead of crashing) if the audio can't actually be
    // played — a corrupted/truncated base64 payload, a bad file write, or a
    // format MediaPlayer rejects all throw here, and since messages sync to
    // every device in the chat, one bad voice note used to be able to crash
    // every participant the moment any of them tapped play on it.
    fun ensurePlayer(): android.media.MediaPlayer? {
        playerRef.value?.let { return it }
        val audioData = message.audioData ?: return null
        return try {
            val bytes = android.util.Base64.decode(audioData, android.util.Base64.NO_WRAP)
            val dir = java.io.File(context.cacheDir, "voice_in").apply { mkdirs() }
            val file = java.io.File(dir, "${message.id}.m4a")
            if (!file.exists()) file.writeBytes(bytes)
            android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                }
            }.also { playerRef.value = it }
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    // Tap-or-drag-to-seek on the waveform — works whether playback is
    // running or paused, matching a normal media player's scrubber rather
    // than requiring playback to already be in progress.
    fun seekToFraction(fraction: Float) {
        if (durationMs <= 0) return
        val player = ensurePlayer() ?: return
        val target = (fraction * durationMs).toInt().coerceIn(0, durationMs)
        player.seekTo(target)
        positionMs = target
    }

    Column(modifier = Modifier.width(VOICE_NOTE_BUBBLE_WIDTH)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = {
                    if (isPlaying) {
                        playerRef.value?.pause()
                        isPlaying = false
                        return@IconButton
                    }
                    val player = ensurePlayer() ?: return@IconButton
                    if (positionMs > 0) player.seekTo(positionMs)
                    player.start()
                    isPlaying = true
                    coroutineScope.launch {
                        while (isPlaying) {
                            positionMs = playerRef.value?.currentPosition ?: 0
                            delay(200)
                        }
                    }
                }
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.chat_voice_note_pause else R.string.chat_voice_note_play
                    ),
                    tint = tint
                )
            }
            PlaybackWaveform(
                samples = samples,
                progress = progress,
                playedColor = tint,
                unplayedColor = dimTint,
                modifier = Modifier
                    .weight(1f)
                    .height(27.dp)
                    .padding(start = 2.dp, end = 6.dp),
                onSeek = { fraction -> seekToFraction(fraction) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 6.dp, top = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val elapsedSeconds = positionMs / 1000
            Text(
                text = stringResource(R.string.chat_recording_duration, elapsedSeconds / 60, elapsedSeconds % 60),
                fontSize = 11.sp,
                color = tint.copy(alpha = 0.85f)
            )
            val totalSeconds = durationMs / 1000
            Text(
                text = stringResource(R.string.chat_recording_duration, totalSeconds / 60, totalSeconds % 60),
                fontSize = 11.sp,
                color = tint.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun StatusTicks(status: MessageStatus) {
    val (icon, tint) = when (status) {
        MessageStatus.SENT -> Icons.Filled.Done to MaterialTheme.colorScheme.onSurfaceVariant
        MessageStatus.DELIVERED -> Icons.Filled.DoneAll to MaterialTheme.colorScheme.onSurfaceVariant
        MessageStatus.READ -> Icons.Filled.DoneAll to ReadReceiptBlue
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(
            when (status) {
                MessageStatus.SENT -> R.string.chat_status_sent
                MessageStatus.DELIVERED -> R.string.chat_status_delivered
                MessageStatus.READ -> R.string.chat_status_read
            }
        ),
        tint = tint,
        modifier = Modifier.padding(start = 4.dp).size(14.dp)
    )
}

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

private fun formatMessageTime(epochMillis: Long): String =
    if (epochMillis == 0L) "" else timeFormatter.format(Date(epochMillis))

@Composable
private fun formatLastSeen(lastSeenAt: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - lastSeenAt
    val nowCal = Calendar.getInstance()
    val thenCal = Calendar.getInstance().apply { timeInMillis = lastSeenAt }

    return when {
        diff < 60_000L -> stringResource(R.string.chat_last_seen_just_now)
        isSameDay(nowCal, thenCal) ->
            stringResource(R.string.chat_last_seen_today, timeFormatter.format(Date(lastSeenAt)))
        isYesterday(nowCal, thenCal) ->
            stringResource(R.string.chat_last_seen_yesterday, timeFormatter.format(Date(lastSeenAt)))
        diff < 7 * 24 * 60 * 60 * 1000L ->
            stringResource(R.string.chat_last_seen_days_ago, (diff / (24 * 60 * 60 * 1000L)).toInt())
        diff < 30 * 24 * 60 * 60 * 1000L ->
            stringResource(R.string.chat_last_seen_weeks_ago, (diff / (7 * 24 * 60 * 60 * 1000L)).toInt())
        else -> stringResource(R.string.chat_last_seen_on_date, dateFormatter.format(Date(lastSeenAt)))
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(now: Calendar, then: Calendar): Boolean {
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return isSameDay(yesterday, then)
}
