package com.pumpkin.app.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pumpkin.app.R

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val webClientId = stringResource(R.string.google_web_client_id)

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                .getResult(ApiException::class.java)
            account.idToken?.let { viewModel.signInWithGoogle(it) }
                ?: run { googleError = "Google Sign-In returned no ID token" }
        } catch (e: ApiException) {
            googleError = "Google Sign-In failed (code ${e.statusCode})"
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (isSignUp) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.auth_display_name_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            (state as? AuthUiState.Error)?.let { Text(it.message) }
            googleError?.let { Text(it) }

            Button(
                onClick = {
                    if (isSignUp) viewModel.signUp(email, password, displayName)
                    else viewModel.signIn(email, password)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(stringResource(if (isSignUp) R.string.auth_sign_up else R.string.auth_sign_in))
            }
            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(
                    stringResource(
                        if (isSignUp) R.string.auth_switch_to_sign_in
                        else R.string.auth_switch_to_sign_up
                    )
                )
            }

            OutlinedButton(
                enabled = webClientId.isNotBlank(),
                onClick = {
                    googleError = null
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                    googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                Text(
                    stringResource(
                        if (webClientId.isBlank()) R.string.auth_google_not_configured
                        else R.string.auth_google_sign_in
                    )
                )
            }
        }
    }
}
