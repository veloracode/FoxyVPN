package com.vauth.foxyvpn.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.isPlaceholderEmail
import com.vauth.foxyvpn.data.GUARDIAN_ENDPOINT_DEFAULT
import com.vauth.foxyvpn.data.GuardianClient
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.vpn.ExitCheck
import com.vauth.foxyvpn.vpn.FoxyVpnService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val settingsStore: SettingsStore,
    private val guardianClient: GuardianClient,
    private val authRepository: FxaAuthRepository,
    private val connectivity: ConnectivityManager?,
    private val accountStore: com.vauth.foxyvpn.data.AccountStore? = null,
    private val accountRepository: com.vauth.foxyvpn.data.AccountRepository? = null,
) : ViewModel() {
    val account = settingsStore.account
    private var addressJob: Job? = null
    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshAddress()
                refreshAccount()
            }

            override fun onLost(network: Network) {
                refreshAddress()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    refreshAddress()
                    refreshAccount()
                }
            }
        }

    init {
        refreshAddress()
        refreshAccount()
        runCatching {
                connectivity?.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build(),
                    networkCallback,
                )
            }
            .onFailure {
                AppLogger.w("HomeViewModel", "Could not observe underlying-network changes")
            }
    }

    private var lastIdentityAttemptAtMs = 0L

    fun refreshAccount() {
        viewModelScope.launch {
            try {
                val store = accountStore
                val repo = accountRepository
                // Self-heal (offline, idempotent): if the live session somehow
                // has no active entry, register it BEFORE attributing quota.
                if (repo != null && store != null && store.activeAccountIdFlow.value == null) {
                    withContext(Dispatchers.IO) { repo.registerLiveSession() }
                }
                val token = authRepository.currentAccessToken() ?: return@launch
                // Generation tagging: a TokenStore epoch (bumped by sign-in,
                // sign-out and account switch) + the session lineage at request
                // start; a response that lands after any of those is dropped.
                val epochAtStart = authRepository.currentSessionEpoch()
                val lineageAtStart = authRepository.sessionLineage()
                val info = guardianClient.fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, token)
                if (authRepository.currentSessionEpoch() != epochAtStart) return@launch
                if (authRepository.sessionLineage() != lineageAtStart) return@launch

                // Resolve the real identity BEFORE attributing, so the stamp names
                // the entry that survives a placeholder fold. Throttled: a
                // persistent 406 must not turn every refresh into a retry storm.
                if (repo != null && store != null) {
                    val entry = store.activeAccount()
                    val nowMs = System.currentTimeMillis()
                    if (entry != null &&
                        (entry.uid == null || isPlaceholderEmail(entry.email)) &&
                        nowMs - lastIdentityAttemptAtMs > IDENTITY_RETRY_INTERVAL_MS
                    ) {
                        lastIdentityAttemptAtMs = nowMs
                        val identity = authRepository.resolveCurrentIdentity(maxAttempts = 2)
                        if (identity != null) {
                            withContext(Dispatchers.IO) {
                                repo.registerLiveSession(identity.email, identity.uid, epochAtStart)
                            }
                        }
                    }
                }
                if (authRepository.currentSessionEpoch() != epochAtStart) return@launch

                val accountIdNow = store?.activeAccountIdFlow?.value
                val hasKnownQuota = info.quotaRemaining != null || info.maxBytes != null
                val isUnlimited = !info.limitedBandwidth && !hasKnownQuota
                val nowSeconds = System.currentTimeMillis() / 1000
                withContext(Dispatchers.IO) {
                    settingsStore.saveAccountQuota(
                        isUnlimited,
                        info.quotaRemaining,
                        info.maxBytes,
                        accountIdNow,
                        nowSeconds,
                    )
                    if (store != null && accountIdNow != null) {
                        store.getAccount(accountIdNow)?.let { entry ->
                            store.updateAccount(
                                entry.copy(
                                    quotaRemaining = info.quotaRemaining,
                                    quotaFetchedAtEpochSeconds = nowSeconds,
                                )
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.w("HomeViewModel", "Account refresh failed; keeping last known quota", e)
            }
        }
    }

    fun triggerPing() {
        if (_isPinging.value) return
        viewModelScope.launch {
            _isPinging.value = true
            try {
                FoxyVpnService.pingNow()
            } finally {
                _isPinging.value = false
            }
        }
    }

    private fun refreshAddress() {
        addressJob?.cancel()
        addressJob =
            viewModelScope.launch {
                delay(750L)
                ExitCheck().fetchDirectIpInfo().onSuccess { info ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            settingsStore.saveDirectAddress(
                                info.ip,
                                info.countryCode,
                            )
                        }
                    }
                    .onFailure { if (it is CancellationException) throw it }
                }
            }
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    companion object {
        private const val IDENTITY_RETRY_INTERVAL_MS = 5L * 60L * 1000L

        fun factory(
            settingsStore: SettingsStore,
            guardianClient: GuardianClient,
            authRepository: FxaAuthRepository,
            connectivity: ConnectivityManager?,
            accountStore: com.vauth.foxyvpn.data.AccountStore? = null,
            accountRepository: com.vauth.foxyvpn.data.AccountRepository? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    settingsStore,
                    guardianClient,
                    authRepository,
                    connectivity,
                    accountStore,
                    accountRepository,
                )
            }
        }
    }
}
