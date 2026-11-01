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
import com.vauth.foxyvpn.ui.HomeViewModel
import com.vauth.foxyvpn.ui.ServerListViewModel
import com.vauth.foxyvpn.ui.screens.AccountScreen
import com.vauth.foxyvpn.ui.screens.HomeScreen
import com.vauth.foxyvpn.ui.screens.LoginScreen
import com.vauth.foxyvpn.ui.screens.LogsScreen
import com.vauth.foxyvpn.ui.screens.ServerListScreen
import com.vauth.foxyvpn.ui.screens.SettingsScreen
import com.vauth.foxyvpn.ui.screens.SplashScreen
import com.vauth.foxyvpn.ui.theme.ThemeController
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
    // Fork addition.
    const val ACCOUNTS = "accounts"
}

@Composable
fun FoxyNavGraph(
    navController: NavHostController = rememberNavController(),
    container: AppContainer,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val sessionScope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }

    // Fork addition: after any successful sign-in (browser, legacy, or a
    // restored session), register the session in the account list with
    // its real email. Fire-and-forget: it never blocks navigation, and
    // startup reconciliation covers the case where the lookup fails.
    fun captureSignedInAccount() {
        sessionScope.launch {
            runCatching {
                val email = container.authRepository.profileEmailForCurrentSession()
                if (!email.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        container.accountRepository.captureActiveSession(email)
                    }
                }
            }
        }
    }
    NavHost(navController = navController, startDestination = FoxyRoutes.SPLASH) {
        composable(FoxyRoutes.SPLASH) {
            SplashScreen(
                authRepository = container.authRepository,
                onSignedIn = {
                    captureSignedInAccount()
                    navController.navigate(FoxyRoutes.HOME) {
                        popUpTo(FoxyRoutes.SPLASH) { inclusive = true }
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
                    captureSignedInAccount()
                    navController.navigate(FoxyRoutes.HOME) {
                        popUpTo(FoxyRoutes.LOGIN) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(FoxyRoutes.SETTINGS) },
                accountRepository = container.accountRepository,
                accountStore = container.accountStore,
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
                        )
                )
            HomeScreen(
                proxyStateStore = container.proxyStateStore,
                homeModel = homeModel,
                themeController = themeController,
                onRequestConnect = onRequestConnect,
                onDisconnect = onDisconnect,
                onOpenServers = { navController.navigate(FoxyRoutes.SERVERS) },
                onOpenSettings = { navController.navigate(FoxyRoutes.SETTINGS) },
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
                                container.accountRepository.switchTo(account.id)
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
                onBack = { navController.popBackStack() },
            )
        }
        composable(FoxyRoutes.LOGS) { LogsScreen(onBack = { navController.popBackStack() }) }
    }
}
