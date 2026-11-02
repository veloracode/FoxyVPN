package com.vauth.foxyvpn.ui.screens

import com.vauth.foxyvpn.ui.l10n.s

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.GUARDIAN_ENDPOINT_DEFAULT
import com.vauth.foxyvpn.data.GuardianClient
import com.vauth.foxyvpn.data.AccountStore
import com.vauth.foxyvpn.data.formatBytes
import com.vauth.foxyvpn.data.model.VpnAccount
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(
    authRepository: FxaAuthRepository,
    accountStore: AccountStore,
    onSwitchAccount: (VpnAccount) -> Unit,
    onRemoveAccount: (VpnAccount) -> Unit,
    onAddAccount: () -> Unit,
    onBack: () -> Unit,
) {
    // Fork screen, re-layered on the v2 port's storage split: the account
    // list lives in AccountStore; exactly one account is ACTIVE — its
    // credentials are the session in upstream's TokenStore, and the VPN
    // connects with it. Adding an account runs the same browser sign-in
    // as the first one (see the add-account flow); the screen itself
    // only lists, switches, refreshes quotas and removes.
    val scope = rememberCoroutineScope()
    val accounts by accountStore.accountsFlow.collectAsState()
    val activeAccountId by accountStore.activeAccountIdFlow.collectAsState()
    val usage by accountStore.usageFlow.collectAsState()

    var refreshingQuotas by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<VpnAccount?>(null) }

    fun doDelete(account: VpnAccount) {
        onRemoveAccount(account)
    }

    fun refreshQuotas() {
        refreshingQuotas = true
        banner = null
        scope.launch {
            var ok = 0
            for (account in accountStore.getAccounts()) {
                val renewed = runCatching {
                    authRepository.ensureFreshAccessTokenForAccount(account, force = false)
                }.getOrNull() ?: account.auth
                val entitlement = runCatching {
                    GuardianClient().fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, renewed.accessToken)
                }.getOrNull()
                if (entitlement != null) {
                    accountStore.updateAccount(
                        account.copy(
                            auth = renewed,
                            quotaRemaining = entitlement.quotaRemaining,
                            lastError = null,
                        ),
                    )
                    // Server quota is authoritative — drop locally counted usage.
                    accountStore.resetUsedBytes(account.id)
                    ok++
                } else {
                    accountStore.updateAccount(account.copy(lastError = "Quota refresh failed"))
                }
            }
            banner = "Updated $ok of ${accountStore.getAccounts().size} account(s)."
            refreshingQuotas = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s("Accounts"), style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshQuotas() }, enabled = !refreshingQuotas) {
                        if (refreshingQuotas) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh quotas")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp),
        ) {
            banner?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onAddAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s("Add account"))
            }

            Spacer(Modifier.height(12.dp))

            if (accounts.isEmpty()) {
                EmptyAccountsHint()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            isActive = account.id == activeAccountId,
                            usedBytes = usage[account.id] ?: 0L,
                            corner = 20.dp,
                            onToggle = { accountStore.toggleAccount(account.id, !account.isEnabled) },
                            onUse = { onSwitchAccount(account) },
                            onDelete = { pendingDelete = account },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { account ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(s("Remove account?")) },
            text = { Text("Remove ${account.email.ifBlank { "this account" }} from the app?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        doDelete(account)
                        pendingDelete = null
                    },
                ) { Text(s("Remove"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(s("Cancel")) }
            },
        )
    }

}

@Composable
private fun EmptyAccountsHint() {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = cs.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No accounts yet",
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Sign in with another Firefox account to add it here. The account marked Active is the one the VPN connects with — switch any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountCard(
    account: VpnAccount,
    isActive: Boolean,
    usedBytes: Long,
    corner: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dotTarget = when {
        !account.isEnabled -> cs.outlineVariant
        isActive -> cs.primary
        else -> cs.outline
    }
    val dot by animateColorAsState(dotTarget, tween(300), label = "card-dot")

    Surface(
        shape = RoundedCornerShape(corner),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    account.email.ifBlank { "Account" },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(checked = account.isEnabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove account",
                        tint = cs.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Quota bar — always visible (Bug 2). Server-reported remaining
            // when known, otherwise estimated from this device's usage.
            Spacer(Modifier.height(8.dp))
            val base = account.quotaRemaining ?: account.quotaMax
            val remaining = (base - usedBytes).coerceAtLeast(0L)
            val fraction = (remaining.toFloat() / account.quotaMax.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    fraction <= 0.1f -> cs.error
                    fraction <= 0.25f -> cs.tertiary
                    else -> cs.primary
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("${formatBytes(remaining)} left of ${formatBytes(account.quotaMax)}")
                    if (account.quotaRemaining == null) append(" · on this device")
                },
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
            if (isActive) {
                Text(
                    s("Active account"),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                )
            } else {
                TextButton(
                    onClick = onUse,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text(s("Use this account")) }
            }

            account.lastError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = cs.error, maxLines = 2)
            }
        }
    }
}
