package com.vauth.foxyvpn.ui.navigation

import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vauth.foxyvpn.AppContainer
import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.ui.HomeViewModel
import com.vauth.foxyvpn.ui.ServerListViewModel
import com.vauth.foxyvpn.ui.screens.AccountScreen
import com.vauth.foxyvpn.ui.screens.AddAccountScreen
import com.vauth.foxyvpn.ui.screens.HomeScreen
import com.vauth.foxyvpn.ui.screens.LoginScreen
import com.vauth.foxyvpn.ui.screens.LogsScreen
import com.vauth.foxyvpn.ui.screens.ServerListScreen
import com.vauth.foxyvpn.ui.screens.SettingsScreen
import com.vauth.foxyvpn.ui.screens.SplashScreen
import com.vauth.foxyvpn.ui.theme.AppearanceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FoxyRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
    // Fork additions.
    const val ACCOUNTS = "accounts"
    const val ADD_ACCOUNT = "add_account"
}

@Composable
fun FoxyNavGraph(
    navController: NavHostController = rememberNavController(),
    container: AppContainer,
    appearance: AppearanceController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val sessionScope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }

    // Fork addition: after any successful sign-in (browser flow or a
    // restored session) the LIVE session is registered in the account list.
    //  Step 1 is OFFLINE and unconditional (create-or-match, placeholder
    //  identity if nothing is known) — it does not depend on any network
    //  call, so a 406-prone network can no longer leave the signed-in
    //  account unregistered. Navigation waits for it (a few ms of disk I/O)
    //  so Home's very first composition already has an active account.
    //  Step 2 resolves the real identity (refreshing, challenge-aware,
    //  retrying) and backfills email/uid; failure leaves the placeholder.
    // The FxA web state is cleared afterwards: the WebView must never hold a
    // live FxA session at rest (the app session lives in the TokenStore).
    fun registerSignedInAccount(thenNavigate: () -> Unit) {
        sessionScope.launch {
            try {
                try {
                    withContext(Dispatchers.IO) { container.accountRepository.registerLiveSession() }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppLogger.w("FoxyNavGraph", "live-session registration failed", error)
                }
                thenNavigate()
                try {
                    val epoch = container.authRepository.currentSessionEpoch()
                    val identity = container.authRepository.resolveCurrentIdentity()
                    if (identity != null) {
                        withContext(Dispatchers.IO) {
                            container.accountRepository.registerLiveSession(
                                identity.email,
                                identity.uid,
                                epoch,
                            )
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppLogger.w("FoxyNavGraph", "identity backfill failed", error)
                }
            } finally {
                com.vauth.foxyvpn.ui.screens.clearFxAWebState()
            }
        }
    }
    NavHost(navController = navController, startDestination = FoxyRoutes.SPLASH) {
        composable(FoxyRoutes.SPLASH) {
            SplashScreen(
                authRepository = container.authRepository,
                onSignedIn = {
                    registerSignedInAccount {
                        navController.navigate(FoxyRoutes.HOME) {
                            popUpTo(FoxyRoutes.SPLASH) { inclusive = true }
                        }
                    }
                },
                onNeedsLogin = {
                    navController.navigate(FoxyRoutes.LOGIN) {
                        popUpTo(FoxyRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(FoxyRoutes.LOGIN) {
            LoginScreen(
                authRepository = container.authRepository,
                onSignedIn = {
                    registerSignedInAccount {
                        navController.navigate(FoxyRoutes.HOME) {
                            popUpTo(FoxyRoutes.LOGIN) { inclusive = true }
                        }
                    }
                },
                onOpenSettings = { navController.navigate(FoxyRoutes.SETTINGS) },
            )
        }
        composable(FoxyRoutes.HOME) {
            val homeModel: HomeViewModel =
                viewModel(
                    factory =
                        HomeViewModel.factory(
                            settingsStore = container.settingsStore,
                            guardianClient = container.guardianClient,
                            authRepository = container.authRepository,
                            connectivity = context.getSystemService(ConnectivityManager::class.java),
                            accountStore = container.accountStore,
                            accountRepository = container.accountRepository,
                        )
                )
            HomeScreen(
                container = container,
                appearance = appearance,
                homeModel = homeModel,
                onRequestConnect = onRequestConnect,
                onDisconnect = onDisconnect,
                onOpenServers = { navController.navigate(FoxyRoutes.SERVERS) },
                onOpenSettings = { navController.navigate(FoxyRoutes.SETTINGS) },
                onOpenAccounts = { navController.navigate(FoxyRoutes.ACCOUNTS) },
            )
        }
        composable(FoxyRoutes.SERVERS) {
            val serverListModel: ServerListViewModel =
                viewModel(
                    factory =
                        ServerListViewModel.factory(
                            serverListClient = container.serverListClient,
                            proxyStateStore = container.proxyStateStore,
                        )
                )
            ServerListScreen(
                viewModel = serverListModel,
                proxyStateStore = container.proxyStateStore,
                onServerSelected = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(FoxyRoutes.SETTINGS) {
            val app = context.applicationContext as? com.vauth.foxyvpn.FoxyVpnApp
            val updater = app?.appUpdater
            var autoUpdates by remember { mutableStateOf(updater?.automaticChecks ?: false) }
            SettingsScreen(
                settingsStore = container.settingsStore,
                appearance = appearance,
                hasSession = container.tokenStore.loadAuth() != null,
                onOpenAccounts = { navController.navigate(FoxyRoutes.ACCOUNTS) },
                onOpenLogs = { navController.navigate(FoxyRoutes.LOGS) },
                signOutInProgress = signingOut,
                onCheckForUpdates = {
                    updater?.showPrompt()
                    updater?.check()
                },
                automaticUpdates = autoUpdates,
                onAutomaticUpdatesChange = { enabled ->
                    updater?.automaticChecks = enabled
                    autoUpdates = enabled
                },
                onSignOut = signOut@{
                    if (signingOut) return@signOut
                    signingOut = true
                    val epoch = container.tokenStore.sessionEpoch
                    onDisconnect()
                    sessionScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(container.tokenStore.clearIfCurrent(epoch)) {
                                    "Session changed. Try signing out again."
                                }
                                // Fork addition: the explicit global
                                // sign-out clears the account list too
                                // (v1 fork semantics); per-account removal
                                // lives on the accounts screen.
                                container.accountRepository.onGlobalSignOut()
                                com.vauth.foxyvpn.data.ControlPlaneHttp.cookieJar.clear()
                                container.settingsStore.clearAccountSession()
                                container.proxyStateStore.clear()
                            }
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            navController.navigate(FoxyRoutes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                error.message ?: "Could not clear the session",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            signingOut = false
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(FoxyRoutes.ACCOUNTS) {
            AccountScreen(
                authRepository = container.authRepository,
                accountStore = container.accountStore,
                onSwitchAccount = { account ->
                    onDisconnect()
                    sessionScope.launch {
                        val switched =
                            withContext(Dispatchers.IO) {
                                val before = container.accountStore.activeAccountIdFlow.value
                                container.accountRepository.switchTo(account.id).also { ok ->
                                    // The snapshot still holds the OUTGOING account's
                                    // numbers; drop them so they can never render as
                                    // the new account's before its refresh lands.
                                    if (ok && before != account.id) {
                                        container.settingsStore.clearAccountQuota()
                                    }
                                }
                            }
                        if (switched) {
                            navController.navigate(FoxyRoutes.HOME) {
                                popUpTo(FoxyRoutes.HOME) { inclusive = true }
                            }
                        } else {
                            android.widget.Toast.makeText(
                                    context,
                                    "Could not switch accounts. Try again.",
                                    android.widget.Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                    }
                },
                onRemoveAccount = { account ->
                    val wasActive =
                        container.accountStore.activeAccountIdFlow.value == account.id
                    if (!wasActive) {
                        container.accountStore.removeAccount(account.id)
                    } else {
                        // Removing the ACTIVE account also ends its
                        // session (it lives in the TokenStore).
                        onDisconnect()
                        sessionScope.launch {
                            try {
                                val epoch = container.tokenStore.sessionEpoch
                                withContext(Dispatchers.IO) {
                                    check(container.tokenStore.clearIfCurrent(epoch)) {
                                        "Session changed. Try again."
                                    }
                                    container.accountStore.removeAccount(account.id)
                                    com.vauth.foxyvpn.data.ControlPlaneHttp.cookieJar
                                        .clear()
                                    container.settingsStore.clearAccountSession()
                                    container.proxyStateStore.clear()
                                }
                                android.webkit.CookieManager.getInstance()
                                    .removeAllCookies(null)
                                navController.navigate(FoxyRoutes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                android.widget.Toast.makeText(
                                        context,
                                        error.message ?: "Could not remove the account",
                                        android.widget.Toast.LENGTH_LONG,
                                    )
                                    .show()
                            }
                        }
                    }
                },
                onAddAccount = { navController.navigate(FoxyRoutes.ADD_ACCOUNT) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(FoxyRoutes.ADD_ACCOUNT) {
            AddAccountScreen(
                authRepository = container.authRepository,
                accountRepository = container.accountRepository,
                onFinished = { message ->
                    navController.popBackStack()
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG)
                        .show()
                },
                onCanceled = { navController.popBackStack() },
            )
        }
        composable(FoxyRoutes.LOGS) { LogsScreen(onBack = { navController.popBackStack() }) }
    }
}
