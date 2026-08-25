package com.pumpkin.app.ui.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpkin.app.R
import com.pumpkin.app.ui.theme.AppColorTheme
import com.pumpkin.app.ui.theme.Avatar
import com.pumpkin.app.ui.theme.ThemeStore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenChat: (String) -> Unit,
    onSignOut: () -> Unit,
    onChangePin: () -> Unit,
    onEditName: () -> Unit
) {
    val chatItems by viewModel.chatItems.collectAsState()
    val newChatError by viewModel.newChatError.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val bannerUpdate by viewModel.bannerUpdate.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.checkForUpdateBanner(context) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var partnerEmail by remember { mutableStateOf("") }
    var chatPendingDelete by remember { mutableStateOf<ChatListItem?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val unknownPartnerLabel = stringResource(R.string.chatlist_unknown_partner)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chatlist_title)) },
                actions = {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chatlist_edit_name_action)) },
                            onClick = {
                                showOverflowMenu = false
                                onEditName()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chatlist_change_pin_action)) },
                            onClick = {
                                showOverflowMenu = false
                                onChangePin()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.auth_sign_out)) },
                            onClick = {
                                showOverflowMenu = false
                                onSignOut()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chatlist_update_app_action)) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.checkForUpdate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chatlist_app_theme_action)) },
                            onClick = {
                                showOverflowMenu = false
                                showThemeDialog = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chatlist_new_chat_title))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        bannerUpdate?.let { info ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chatlist_update_banner_text, info.version),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.downloadAndInstallUpdate(context) }) {
                    Text(stringResource(R.string.chatlist_update_action))
                }
                IconButton(onClick = { viewModel.dismissUpdateBanner(context) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chatlist_update_banner_dismiss),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        deleteError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (chatItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.chatlist_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                items(chatItems, key = { it.chat.id }) { item ->
                    val displayName = item.chat.otherParticipantName(viewModel.currentUserId)
                        ?: unknownPartnerLabel
                    // Privacy choice per PRD's spirit: never surface message
                    // content here, only whether there's something unread —
                    // the draft indicator below follows the same rule, it
                    // shows THAT a draft exists, never what it says.
                    val statusText = if (item.unreadCount > 0) {
                        stringResource(R.string.chatlist_new_message_count, item.unreadCount)
                    } else {
                        stringResource(R.string.chatlist_no_new_message)
                    }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .combinedClickable(
                                onClick = { onOpenChat(item.chat.id) },
                                onLongClick = { chatPendingDelete = item }
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(name = displayName)
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false)) {
                                Text(text = displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (item.hasDraft) {
                                    Text(
                                        text = stringResource(R.string.chatlist_draft),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Text(
                                        text = statusText,
                                        color = if (item.unreadCount > 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewChatDialog = false
                viewModel.clearNewChatError()
            },
            title = { Text(stringResource(R.string.chatlist_new_chat_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.chatlist_new_chat_hint_text))
                    OutlinedTextField(
                        value = partnerEmail,
                        onValueChange = {
                            partnerEmail = it
                            viewModel.clearNewChatError()
                        },
                        label = { Text(stringResource(R.string.chatlist_new_chat_email_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    newChatError?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.startChat(partnerEmail) { chatId ->
                        showNewChatDialog = false
                        partnerEmail = ""
                        onOpenChat(chatId)
                    }
                }) {
                    Text(stringResource(R.string.chatlist_new_chat_start))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewChatDialog = false
                    viewModel.clearNewChatError()
                }) {
                    Text(stringResource(R.string.chatlist_new_chat_cancel))
                }
            }
        )
    }

    chatPendingDelete?.let { item ->
        val partnerName = item.chat.otherParticipantName(viewModel.currentUserId) ?: unknownPartnerLabel
        AlertDialog(
            onDismissRequest = {
                chatPendingDelete = null
                viewModel.clearDeleteError()
            },
            title = { Text(stringResource(R.string.chatlist_delete_chat)) },
            text = {
                Column {
                    Text(stringResource(R.string.chatlist_delete_chat_confirm, partnerName))
                    deleteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(item.chat.id)
                    chatPendingDelete = null
                }) {
                    Text(stringResource(R.string.chatlist_delete_chat), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    chatPendingDelete = null
                    viewModel.clearDeleteError()
                }) {
                    Text(stringResource(R.string.chatlist_new_chat_cancel))
                }
            }
        )
    }

    when (val state = updateState) {
        is UpdateState.Idle -> Unit
        is UpdateState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.chatlist_update_checking)) },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )
        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.chatlist_update_up_to_date)) },
            text = {},
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text(stringResource(R.string.chatlist_update_ok))
                }
            }
        )
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.chatlist_update_available, state.version)) },
            text = { if (state.notes.isNotBlank()) Text(state.notes) },
            confirmButton = {
                TextButton(onClick = { viewModel.downloadAndInstallUpdate(context) }) {
                    Text(stringResource(R.string.chatlist_update_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text(stringResource(R.string.chatlist_new_chat_cancel))
                }
            }
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.chatlist_update_downloading)) },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )
        is UpdateState.Error -> AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.chatlist_update_error)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text(stringResource(R.string.chatlist_update_ok))
                }
            }
        )
    }

    if (showThemeDialog) {
        val selected = ThemeStore.selected.value
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.chatlist_app_theme_action)) },
            text = {
                Column {
                    AppColorTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeStore.select(context, theme)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(theme.accent, androidx.compose.foundation.shape.CircleShape)
                            )
                            Text(
                                text = theme.displayName,
                                modifier = Modifier.padding(start = 12.dp).weight(1f)
                            )
                            if (theme == selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = theme.accent)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.chatlist_update_ok))
                }
            }
        )
    }
}
