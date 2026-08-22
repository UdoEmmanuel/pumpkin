package com.pumpkin.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pumpkin.app.R

/** Lets a signed-in user correct their display name — reachable from the chat list's top bar. */
@Composable
fun EditNameScreen(viewModel: EditNameViewModel, onDone: () -> Unit) {
    val currentName by viewModel.currentName.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val error by viewModel.error.collectAsState()

    var name by remember { mutableStateOf("") }
    // currentName loads asynchronously (a Firestore read) — seed the editable
    // field once it arrives, but don't stomp on text the user has already
    // started typing if it arrives late.
    LaunchedEffect(currentName) {
        if (name.isEmpty()) name = currentName
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.edit_name_title))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.edit_name_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved) Text(stringResource(R.string.edit_name_success))
            Button(
                onClick = { viewModel.save(name) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.edit_name_save))
            }
            TextButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.edit_name_back))
            }
        }
    }
}
