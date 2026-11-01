package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.AccountRepository
import com.vauth.foxyvpn.data.AccountStore
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.ui.LoginViewModel
import com.vauth.foxyvpn.ui.l10n.s
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: FxaAuthRepository,
    onSignedIn: () -> Unit,
    // Fork additions (optional so previews/tests keep the upstream shape):
    // the pre-login settings gear and the account layer for batch import.
    onOpenSettings: (() -> Unit)? = null,
    accountRepository: AccountRepository? = null,
    accountStore: AccountStore? = null,
) {
    val model: LoginViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = remember(authRepository) { LoginViewModel.factory(authRepository) }
        )
    val uiState by model.uiState.collectAsStateWithLifecycle()
    val browserStartUrl = uiState.browserStartUrl
    val completingBrowserLogin = uiState.isLoading
    val errorMessage = uiState.errorMessage
    val storageError by authRepository.storageError.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(uiState.signedIn) { if (uiState.signedIn) onSignedIn() }

    if (browserStartUrl != null) {
        BrowserLoginScreen(
            startUrl = browserStartUrl,
            busy = completingBrowserLogin,
            authRepository = authRepository,
            errorMessage = errorMessage,
            onTokensCaptured = model::completeBrowserLoginWithTokens,
            onSessionCaptured = model::completeBrowserLogin,
            onCanceled = model::cancelVerification,
        )
        return
    }

    // ------------------------------------------------------------------
    // Fork additions: secondary sign-in (email + password with the
    // emailed confirmation-code step) plus batch import. A sign-in
    // interrupted at the code step resumes right here: the pending
    // session is persisted in the repository, not just in memory, so
    // the section starts expanded whenever a pending login exists.
    // ------------------------------------------------------------------
    val scope = rememberCoroutineScope()
    var legacyExpanded by rememberSaveable {
        mutableStateOf(authRepository.pendingLogin() != null)
    }
    var legacyEmail by remember { mutableStateOf("") }
    var legacyPassword by remember { mutableStateOf("") }
    var legacyCode by remember { mutableStateOf("") }
    var awaitingTwoFactor by remember { mutableStateOf(authRepository.pendingLogin() != null) }
    var pendingEmail by remember {
        mutableStateOf(authRepository.pendingLogin()?.email?.takeIf { it.isNotBlank() })
    }
    var legacyLoading by remember { mutableStateOf(false) }
    var legacyError by remember { mutableStateOf<String?>(null) }
    var legacyNotice by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    fun doSubmitLogin() {
        val cleanEmail = FxaAuthRepository.normalizeEmail(legacyEmail)
        if (cleanEmail.isBlank() || legacyPassword.isBlank() || legacyLoading) return
        legacyError = null
        legacyNotice = null
        legacyLoading = true
        scope.launch {
            val result = authRepository.startLogin(cleanEmail, legacyPassword)
            legacyLoading = false
            result.onSuccess { needsVerification ->
                if (needsVerification) {
                    pendingEmail = cleanEmail
                    awaitingTwoFactor = true
                } else {
                    onSignedIn()
                }
            }.onFailure { legacyError = it.message ?: "Sign-in failed" }
        }
    }

    fun doSubmitTwoFactor() {
        val cleanCode = FxaAuthRepository.sanitizeCode(legacyCode)
        if (cleanCode.isBlank() || legacyLoading) return
        val kind = authRepository.pendingLogin()?.kind
        legacyError = null
        legacyLoading = true
        scope.launch {
            val result = authRepository.submitTwoFactorCode(cleanCode)
            legacyLoading = false
            result.onSuccess {
                if (kind == FxaAuthRepository.PendingKind.ACCOUNT) {
                    // An account add/import finished, not a sign-in:
                    // stay here and say so.
                    awaitingTwoFactor = false
                    pendingEmail = null
                    legacyCode = ""
                    legacyNotice = "Account added. Sign in to use it."
                } else {
                    onSignedIn()
                }
            }.onFailure { legacyError = it.message ?: "Verification failed" }
        }
    }

    // Batch import ends here: with v2's single-session core, the first
    // imported account is installed as the session so the flow still
    // lands the user inside the app, as it did in the v1 fork.
    val activateFirstImported: () -> Unit = {
        val repo = accountRepository
        val store = accountStore
        if (repo != null && store != null) {
            store.getAccounts().firstOrNull { it.isEnabled }?.let { first ->
                if (repo.switchTo(first.id)) onSignedIn()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.login_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            storageError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = model::startBrowserLogin,
                enabled = !completingBrowserLogin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_btn_sign_in))
            }
            Text(
                stringResource(R.string.login_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }

            // ---- Fork addition: secondary sign-in + batch import ----
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

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
                Spacer(Modifier.height(16.dp))

                if (!awaitingTwoFactor) {
                    OutlinedTextField(
                        value = legacyEmail,
                        onValueChange = {
                            legacyEmail = it.replace(Regex("[\\p{Z}\\u200B-\\u200D\\uFEFF\\u00A0\\s]+"), "").lowercase()
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
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = legacyPassword,
                        onValueChange = { legacyPassword = it },
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
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            doSubmitLogin()
                        },
                        enabled = !legacyLoading && legacyEmail.isNotBlank() && legacyPassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (legacyLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        } else {
                            Text(s("Continue"))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AccountHelpRow()
                } else {
                    Text(s("Enter the confirmation code sent to your email"), style = MaterialTheme.typography.bodyMedium)
                    pendingEmail?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = legacyCode,
                        onValueChange = {
                            legacyCode = it.replace(Regex("[\\p{Z}\\u200B-\\u200D\\uFEFF\\u00A0\\s]+"), "")
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
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            doSubmitTwoFactor()
                        },
                        enabled = !legacyLoading && legacyCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (legacyLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        } else {
                            Text(s("Verify"))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OutlinedButton(
                            onClick = {
                                legacyError = null
                                scope.launch {
                                    authRepository.resendPendingCode()
                                        .onSuccess { legacyNotice = "A new code was sent." }
                                        .onFailure { legacyError = it.message ?: "Could not resend the code" }
                                }
                            },
                            enabled = !legacyLoading,
                        ) {
                            Text(s("Resend code"))
                        }
                        OutlinedButton(
                            onClick = {
                                authRepository.abandonPendingLogin()
                                awaitingTwoFactor = false
                                pendingEmail = null
                                legacyCode = ""
                                legacyError = null
                                legacyNotice = null
                            },
                            enabled = !legacyLoading,
                        ) {
                            Text(s("Start over"))
                        }
                    }
                }

                legacyNotice?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }

                legacyError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            BatchImportSection(
                authRepository = authRepository,
                onImported = activateFirstImported,
            )
        }

        // Fork addition: settings must be reachable before sign-in —
        // the anti-block options used when Mozilla cannot be reached
        // live there, and a blocked user has no other way to them.
        if (onOpenSettings != null) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                )
            }
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
    Spacer(Modifier.height(8.dp))
    GuidedBatchImport(
        authRepository = authRepository,
        onImported = onImported,
    )
}
