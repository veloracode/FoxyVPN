package com.vauth.foxyvpn.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.data.AccountRepository
import com.vauth.foxyvpn.data.AccountStore
import com.vauth.foxyvpn.data.ControlPlaneHttp
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.ui.LoginViewModel
import com.vauth.foxyvpn.ui.l10n.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AddPhase { PREPARING, BROWSING, FINISHING }

/**
 * Add-account via the SAME browser sign-in as the first account
 * (user spec 2026-11-02): the new session is captured into the account
 * list and the previously active account is restored as active, so an
 * add never disturbs the account that was in use.
 *
 * The trap this flow is built around: the in-app browser keeps an FxA
 * web session, so a naive second sign-in silently re-authenticates the
 * SAME account. Preparation therefore wipes the WebView's cookie store
 * (and the control-plane cookie jar) before the browser opens, forcing
 * a fresh credential prompt. That cannot touch the active account: its
 * session lives in the TokenStore, not in WebView cookies — the same
 * separation the global sign-out (removeAllCookies) and every account
 * switch (cookieJar.clear) already rely on. Dedupe is the second line
 * of defense: if the user signs in with the same account anyway,
 * capturing it refreshes its existing entry instead of duplicating it.
 *
 * Failure handling: if the new session cannot be captured after it
 * already replaced the TokenStore session, the previous account's
 * STORED session is reinstalled via
 * [AccountRepository.installStoredSession] — never via switchTo, whose
 * snapshot step would overwrite the previous entry with the new
 * session.
 */
@Composable
fun AddAccountScreen(
    authRepository: FxaAuthRepository,
    accountRepository: AccountRepository,
    accountStore: AccountStore,
    onDisconnect: () -> Unit,
    onFinished: (String) -> Unit,
    onCanceled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(AddPhase.PREPARING) }
    val previousActiveId = remember { accountStore.activeAccountIdFlow.value }

    val model: LoginViewModel =
        viewModel(
            factory = remember(authRepository) { LoginViewModel.factory(authRepository) }
        )
    val uiState by model.uiState.collectAsStateWithLifecycle()

    // Resolved here, in composition: s() reads a CompositionLocal and
    // cannot be called from the finish coroutine below.
    val msgRestored = s("Could not add the account — your previous account was restored.")
    val msgFailed = s("Could not add the account.")
    val msgAlready = s("That account is already here — its session was refreshed.")
    val msgAddedPrefix = s("Account added: ")
    val msgNowActiveSuffix = s(" (now active)")

    // Preparation: snapshot the live session into the active entry (so
    // it holds the exact current credentials), stop the tunnel (it is
    // about to change identity), then wipe the FxA web session.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { accountRepository.snapshotActiveSession() }
        onDisconnect()
        ControlPlaneHttp.cookieJar.clear()
        isolateFxAWebSession {
            phase = AddPhase.BROWSING
            model.startBrowserLogin()
        }
    }

    LaunchedEffect(uiState.signedIn) {
        if (!uiState.signedIn || phase != AddPhase.BROWSING) return@LaunchedEffect
        phase = AddPhase.FINISHING
        scope.launch {
            val email = runCatching { authRepository.profileEmailForCurrentSession() }.getOrNull()
            val captured = email?.let {
                withContext(Dispatchers.IO) { accountRepository.captureActiveSession(it) }
            }
            when {
                email == null || captured == null -> {
                    // The new session is in the TokenStore but could
                    // not be registered — put the previous account back
                    // exactly as it was rather than leave the app in a
                    // half-switched state.
                    val restored = previousActiveId?.let {
                        withContext(Dispatchers.IO) { accountRepository.installStoredSession(it) }
                    } ?: false
                    onFinished(
                        if (restored) msgRestored else msgFailed
                    )
                }
                previousActiveId == null || captured.id == previousActiveId -> {
                    // Signed in with the account that was already
                    // active: its entry was refreshed in place (dedupe),
                    // nothing to restore.
                    onFinished(msgAlready)
                }
                else -> {
                    // Capture set the NEW account active; switch back so
                    // the previously active account stays the one in
                    // use. switchTo's snapshot writes the new session
                    // into the new entry (where it belongs) on the way.
                    val back = withContext(Dispatchers.IO) {
                        accountRepository.switchTo(previousActiveId)
                    }
                    onFinished(
                        if (back) msgAddedPrefix + captured.email
                        else msgAddedPrefix + captured.email + msgNowActiveSuffix
                    )
                }
            }
        }
    }

    when (phase) {
        AddPhase.BROWSING -> {
            val startUrl = uiState.browserStartUrl
            if (startUrl != null) {
                BrowserLoginScreen(
                    startUrl = startUrl,
                    busy = uiState.isLoading,
                    authRepository = authRepository,
                    errorMessage = uiState.errorMessage,
                    onTokensCaptured = model::completeBrowserLoginWithTokens,
                    onSessionCaptured = model::completeBrowserLogin,
                    onCanceled = {
                        model.cancelVerification()
                        onCanceled()
                    },
                )
            } else {
                PreparingUi(s("Opening sign-in…"))
            }
        }
        AddPhase.PREPARING -> PreparingUi(s("Preparing…"))
        AddPhase.FINISHING -> PreparingUi(s("Adding account…"))
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

/**
 * Wipes the FxA web session so the add-account browser asks for fresh
 * credentials instead of silently re-using the signed-in account.
 * [onDone] fires once the cookie store reports the removal, so the
 * browser is only created after the wipe has landed.
 */
private fun isolateFxAWebSession(onDone: () -> Unit) {
    val manager = runCatching { CookieManager.getInstance() }.getOrNull()
    if (manager == null) {
        onDone()
        return
    }
    runCatching { WebStorage.getInstance().deleteOrigin("https://accounts.firefox.com") }
    manager.removeAllCookies {
        runCatching { manager.flush() }
        onDone()
    }
}
