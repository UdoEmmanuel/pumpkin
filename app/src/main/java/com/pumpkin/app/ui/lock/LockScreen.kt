package com.pumpkin.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.pumpkin.app.R
import com.pumpkin.app.lock.BiometricLockManager
import com.pumpkin.app.lock.PinManager

// PRD 4.2: biometric primary, PIN/password fallback. Shown before any chat
// content renders — this composable is the nav graph's start-after-calculator
// destination, so nothing chat-related is reachable without passing it.
@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val pinManager = remember { PinManager(activity) }
    val biometricManager = remember { BiometricLockManager(activity) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var settingUpPin by remember { mutableStateOf(!pinManager.isPinSet()) }

    LaunchedEffect(Unit) {
        if (!settingUpPin && pinManager.biometricEnabled && biometricManager.canUseBiometrics()) {
            biometricManager.prompt(
                onSuccess = onUnlocked,
                onError = { /* falls back silently to PIN entry below */ }
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = activity.getString(R.string.lock_title))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it; error = null },
                label = {
                    Text(
                        activity.getString(
                            if (settingUpPin) R.string.lock_pin_set_hint else R.string.lock_pin_hint
                        )
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.padding(top = 16.dp)
            )
            error?.let { Text(it) }
            Button(
                onClick = {
                    if (settingUpPin) {
                        pinManager.setPin(pin)
                        onUnlocked()
                    } else if (pinManager.verifyPin(pin)) {
                        onUnlocked()
                    } else {
                        error = activity.getString(R.string.lock_error_generic)
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(activity.getString(R.string.lock_title))
            }
        }
    }
}
