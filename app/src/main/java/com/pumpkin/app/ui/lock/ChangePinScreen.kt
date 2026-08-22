package com.pumpkin.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pumpkin.app.R
import com.pumpkin.app.lock.PinManager

/** Lets an already-unlocked user rotate their local PIN — reachable from the chat list's top bar. */
@Composable
fun ChangePinScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val pinManager = remember { PinManager(context) }

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.change_pin_title))
            OutlinedTextField(
                value = currentPin,
                onValueChange = { currentPin = it; error = null; success = false },
                label = { Text(stringResource(R.string.change_pin_current_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it; error = null; success = false },
                label = { Text(stringResource(R.string.change_pin_new_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            error?.let { Text(it) }
            if (success) Text(stringResource(R.string.change_pin_success))
            Button(
                onClick = {
                    if (!pinManager.verifyPin(currentPin)) {
                        error = context.getString(R.string.change_pin_wrong_current)
                        return@Button
                    }
                    pinManager.setPin(newPin)
                    currentPin = ""
                    newPin = ""
                    success = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.change_pin_button))
            }
            TextButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.change_pin_back))
            }
        }
    }
}
