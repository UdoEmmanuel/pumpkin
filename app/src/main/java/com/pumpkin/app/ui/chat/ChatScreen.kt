package com.pumpkin.app.ui.chat

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.pumpkin.app.R
import com.pumpkin.app.data.local.NotificationToneStore
import com.pumpkin.app.data.model.Message
import com.pumpkin.app.data.model.MessageStatus
import com.pumpkin.app.ui.theme.Avatar
import com.pumpkin.app.ui.theme.ReadReceiptBlue
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
    val replyingTo by viewModel.replyingTo.collectAsState()
    val nicknameError by viewModel.nicknameError.collectAsState()
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val toneStore = remember { NotificationToneStore(context) }
    val toneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        toneStore.set(viewModel.chatId, uri?.toString())
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
                        onReply = { viewModel.startReply(message) }
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { viewModel.send(input) }) {
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
}

@Composable
private fun MessageRow(
    message: Message,
    currentUserId: String,
    otherParticipantId: String?,
    otherParticipantName: String?,
    onReply: () -> Unit
) {
    val isOwnMessage = message.senderId == currentUserId
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val replyThresholdPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 84.dp.toPx() }

    Column(
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
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
    ) {
        Box(
            modifier = Modifier
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
                Text(
                    text = message.text,
                    color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
