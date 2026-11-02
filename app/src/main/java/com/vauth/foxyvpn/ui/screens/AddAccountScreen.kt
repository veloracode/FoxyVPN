package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.data.AccountRepository
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.ui.l10n.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add-account via the SAME browser sign-in as the first account
 * (user spec 2026-11-02), implemented DETACHED (review decision
 * 2026-11-02, replacing the earlier swap-and-restore design):
 *
 * The token exchange and the profile fetch are stateless calls that
 * take the new credentials as parameters; only a login COMPLETION
 * writes the global TokenStore. This flow never calls a completion —
 * it exchanges the captured credentials into a session value and
 * stores it directly in the account list
 * ([AccountRepository.addDetachedAccount]). The active account's
 * TokenStore session is therefore never replaced: byte-exact
 * preservation of the previous account is trivial, its tunnel is not
 * even disconnected, and there is no kill-window that can leave the
 * accounts mixed up. If anything fails before the store write, the
 * add simply did not happen.
 *
 * The trap the preparation defends against: the in-app browser keeps
 * an FxA web session, so a naive second sign-in silently
 * re-authenticates the SAME account. The FxA web state (cookies +
 * site storage) is wiped before the browser opens — the browser then
 * asks for fresh credentials — and wiped again when the flow ends,
 * so the WebView never holds a live FxA session at rest. That cannot
 * touch the active account: its session lives in the TokenStore, not
 * in WebView state (the same separation the global sign-out's cookie
 * wipe already relies on). Dedupe is the second line of defense:
 * identity is the FxA uid from the profile endpoint (email as
 * fallback), and capturing an account that is already listed
 * refreshes its entry instead of duplicating it.
 */
@Composable
fun AddAccountScreen(
    authRepository: FxaAuthRepository,
    accountRepository: AccountRepository,
    onFinished: (String) -> Unit,
    onCanceled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var startUrl by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Resolved here, in composition: s() reads a CompositionLocal and
    // cannot be called from the finish coroutine below.
    val msgAddedPrefix = s("Account added: ")
    val msgRefreshed = s("That account is already here — its session was refreshed.")
    val msgUnidentified =
        s("Sign-in finished, but the account could not be identified. Nothing was added.")
    val msgFailed = s("Could not add the account. Check the connection and try again.")

    // Preparation: wipe the FxA web session so the browser prompts for
    // credentials; only open it once the wipe has landed.
    LaunchedEffect(Unit) {
        clearFxAWebState { startUrl = authRepository.getBrowserLoginUrl() }
    }

    fun finish(buildAuth: suspend () -> RuntimeAuth) {
        if (busy) return
        busy = true
        errorMessage = null
        scope.launch {
            val outcome = runCatching {
                val auth = withContext(Dispatchers.IO) { buildAuth() }
                val identity = authRepository.profileIdentityForAuth(auth)
                val email = identity.email
                if (email.isNullOrBlank()) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        accountRepository.addDetachedAccount(auth, email, identity.uid)
                    }
                }
            }
            busy = false
            outcome
                .onSuccess { result ->
                    // Whatever happened, no live FxA web session is
                    // left behind.
                    clearFxAWebState()
                    if (result == null) {
                        onFinished(msgUnidentified)
                    } else {
                        onFinished(
                            if (result.wasExisting) msgRefreshed
                            else msgAddedPrefix + result.account.email
                        )
                    }
                }
                .onFailure { errorMessage = it.message ?: msgFailed }
        }
    }

    val url = startUrl
    if (url != null) {
        BrowserLoginScreen(
            startUrl = url,
            busy = busy,
            authRepository = authRepository,
            errorMessage = errorMessage,
            onTokensCaptured = { accessToken, refreshToken, expiresIn ->
                finish {
                    authRepository.detachedAuthWithTokens(accessToken, refreshToken, expiresIn)
                }
            },
            onSessionCaptured = { sessionToken ->
                finish { authRepository.exchangeBrowserSessionToken(sessionToken) }
            },
            onCanceled = {
                clearFxAWebState()
                onCanceled()
            },
        )
    } else {
        PreparingUi(s("Preparing…"))
    }
}

@Composable
private fun PreparingUi(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
