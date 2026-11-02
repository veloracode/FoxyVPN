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

    fun refreshAccount() {
        viewModelScope.launch {
            try {
                val token = authRepository.currentAccessToken() ?: return@launch
                // Generation tagging (review decision 2026-11-02): the
                // account + session lineage at request start; a
                // response that lands after a switch is dropped, never
                // attributed to the newly active account.
                val accountIdAtStart = accountStore?.activeAccountIdFlow?.value
                val lineageAtStart = authRepository.sessionLineage()
                val info = guardianClient.fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, token)
                if (accountStore?.activeAccountIdFlow?.value != accountIdAtStart) return@launch
                if (authRepository.sessionLineage() != lineageAtStart) return@launch
                val hasKnownQuota = info.quotaRemaining != null || info.maxBytes != null
                val isUnlimited = !info.limitedBandwidth && !hasKnownQuota
                val nowSeconds = System.currentTimeMillis() / 1000
                withContext(Dispatchers.IO) {
                    settingsStore.saveAccountQuota(
                        isUnlimited,
                        info.quotaRemaining,
                        info.maxBytes,
                        accountIdAtStart,
                        nowSeconds,
                    )
                    // Per-account last-known values + identity backfill
                    // ride the same verified-fresh call.
                    val store = accountStore
                    if (store != null && accountIdAtStart != null) {
                        store.getAccount(accountIdAtStart)?.let { entry ->
                            store.updateAccount(
                                entry.copy(
                                    quotaRemaining = info.quotaRemaining,
                                    quotaFetchedAtEpochSeconds = nowSeconds,
                                )
                            )
                        }
                        val repo = accountRepository
                        if (repo != null && store.getAccount(accountIdAtStart)?.uid == null) {
                            val identity = authRepository.profileIdentityForCurrentSession()
                            repo.backfillActiveIdentity(identity.email, identity.uid)
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
