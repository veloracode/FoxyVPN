package com.vauth.foxyvpn.ui.screens

import com.vauth.foxyvpn.ui.l10n.s

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.isUnconfirmedSession
import com.vauth.foxyvpn.data.model.BatchImportItemResult
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: FxaAuthRepository,
    onSignedIn: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // ------------------------------------------------------------------
    // Primary sign-in: the official Mozilla page in an in-app browser
    // (upstream v1.0.8 flow). The legacy email/password form below it is
    // kept as a secondary section; it posts to /account/login, the
    // endpoint Mozilla's bot protection challenges hardest.
    // ------------------------------------------------------------------
    var browserStartUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var completingBrowserLogin by rememberSaveable { mutableStateOf(false) }
    var browserError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun startBrowserLogin() {
        if (completingBrowserLogin) return
        browserError = null
        browserStartUrl = authRepository.getBrowserLoginUrl()
    }

    fun handleBrowserSession(sessionToken: String) {
        if (completingBrowserLogin) return
        completingBrowserLogin = true
        browserError = null
        scope.launch {
            val result = authRepository.completeBrowserLogin(sessionToken)
            completingBrowserLogin = false
            result.onSuccess {
                browserStartUrl = null
                browserError = null
                onSignedIn()
            }.onFailure {
                if (!it.isUnconfirmedSession()) {
                    browserError = it.message ?: "Browser sign-in failed. Please try again."
                }
            }
        }
    }

    if (browserStartUrl != null) {
        BrowserLoginScreen(
            startUrl = browserStartUrl!!,
            busy = completingBrowserLogin,
            errorMessage = browserError,
            onSessionCaptured = ::handleBrowserSession,
            onCanceled = {
                browserStartUrl = null
                completingBrowserLogin = false
                browserError = null
            },
        )
        return
    }

    // ------------------------------------------------------------------
    // Secondary sign-in: email + password (with the emailed confirmation
    // code step), plus batch import. A sign-in that was interrupted at
    // the code step is resumed right here: the pending session is
    // persisted in the repository, not just in memory, so the section
    // starts expanded whenever a pending login exists.
    // ------------------------------------------------------------------
    var legacyExpanded by rememberSaveable {
        mutableStateOf(authRepository.pendingLogin() != null)
    }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var awaitingTwoFactor by remember { mutableStateOf(authRepository.pendingLogin() != null) }
    var pendingEmail by remember {
        mutableStateOf(authRepository.pendingLogin()?.email?.takeIf { it.isNotBlank() })
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    fun doSubmitLogin() {
        val cleanEmail = FxaAuthRepository.normalizeEmail(email)
        if (cleanEmail.isBlank() || password.isBlank() || isLoading) return
        errorMessage = null
        notice = null
        isLoading = true
        scope.launch {
            val result = authRepository.startLogin(cleanEmail, password)
            isLoading = false
            result.onSuccess { needsVerification ->
                if (needsVerification) {
                    pendingEmail = cleanEmail
                    awaitingTwoFactor = true
                } else {
                    onSignedIn()
                }
            }.onFailure { errorMessage = it.message ?: "Sign-in failed" }
        }
    }

    fun doSubmitTwoFactor() {
        val cleanCode = FxaAuthRepository.sanitizeCode(code)
        if (cleanCode.isBlank() || isLoading) return
        errorMessage = null
        isLoading = true
        scope.launch {
            val result = authRepository.submitTwoFactorCode(cleanCode)
            isLoading = false
            result.onSuccess { onSignedIn() }
                .onFailure { errorMessage = it.message ?: "Verification failed" }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(s("Sign in with Firefox"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.padding(top = 8.dp))
        Text(
            "FoxyVPN runs on the free 50 GB of monthly VPN traffic Mozilla includes with a Firefox account. No subscription is required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(top = 24.dp))

        Button(
            onClick = { startBrowserLogin() },
            enabled = !completingBrowserLogin,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s("Sign in with Firefox"))
        }
        Text(
            s("Opens the official Mozilla sign-in page in an in-app browser and returns automatically once you are signed in."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        browserError?.let {
            Spacer(Modifier.padding(top = 12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }

        Spacer(Modifier.padding(top = 20.dp))
        HorizontalDivider()
        Spacer(Modifier.padding(top = 20.dp))

        if (!legacyExpanded) {
            OutlinedButton(
                onClick = { legacyExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s("Sign in with email and password"))
            }
        } else {
            Text(
                s("Sign in with email and password"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.padding(top = 16.dp))

            if (!awaitingTwoFactor) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.replace(Regex("[\\p{Z}\\u200B-\\u200D\\uFEFF\\u00A0\\s]+"), "").lowercase()
                    },
                    label = { Text(s("Firefox account email")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it.replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
                    },
                    label = { Text(s("Password")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            doSubmitLogin()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 20.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        doSubmitLogin()
                    },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text(s("Continue"))
                }
                Spacer(Modifier.padding(top = 8.dp))
                AccountHelpRow()
            } else {
                Text(s("Enter the confirmation code sent to your email"), style = MaterialTheme.typography.bodyMedium)
                pendingEmail?.let {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.replace(Regex("[\\p{Z}\\u200B-\\u200D\\uFEFF\\u00A0\\s]+"), "")
                    },
                    label = { Text(s("Confirmation code")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            doSubmitTwoFactor()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 20.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        doSubmitTwoFactor()
                    },
                    enabled = !isLoading && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text(s("Verify"))
                }
                Spacer(Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            scope.launch {
                                authRepository.resendPendingCode()
                                    .onSuccess { notice = "A new code was sent." }
                                    .onFailure { errorMessage = it.message ?: "Could not resend the code" }
                            }
                        },
                        enabled = !isLoading,
                    ) {
                        Text(s("Resend code"))
                    }
                    OutlinedButton(
                        onClick = {
                            authRepository.abandonPendingLogin()
                            awaitingTwoFactor = false
                            pendingEmail = null
                            code = ""
                            errorMessage = null
                            notice = null
                        },
                        enabled = !isLoading,
                    ) {
                        Text(s("Start over"))
                    }
                }
            }

            notice?.let {
                Spacer(Modifier.padding(top = 12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }

            errorMessage?.let {
                Spacer(Modifier.padding(top = 12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }

        Spacer(Modifier.padding(top = 20.dp))
        BatchImportSection(
            authRepository = authRepository,
            onImported = onSignedIn,
        )
    }

        // Settings must be reachable before sign-in: the connection
        // options used when Mozilla cannot be reached live there, and a
        // user blocked at sign-in has no other way to them.
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
            )
        }
    }
}

@Composable
private fun BatchImportSection(
    authRepository: FxaAuthRepository,
    onImported: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    if (!expanded) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s("Batch import accounts"))
        }
        return
    }

    Text(
        "Batch import",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.padding(top = 8.dp))
    GuidedBatchImport(
        authRepository = authRepository,
        onImported = onImported,
    )
}
