package com.vauth.foxyvpn.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.data.ControlPlaneHttp
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.ui.l10n.s

/**
 * The fork's settings additions, in upstream v2's ListItem idiom:
 *
 * - [ForkAccountsRow]: entry to the multi-account screen.
 * - [AntiBlockSettingsSection]: "When Mozilla can't be reached" — the
 *   two control-plane fallbacks that survived the v2.0.0 port (the
 *   in-socket SOCKS5 account proxy and the API relay). The v1 fork's
 *   third fallback, control-plane TLS splitting, was dropped: upstream
 *   v2 removed its own control-plane splitting and its tunnel-side
 *   anti-censorship suite supersedes the idea.
 *
 * The section is shown on the settings screen AND pre-login (through
 * the login screen's gear), because these toggles exist to unblock
 * sign-in itself — hiding them behind the sign-in they fix was the
 * v1.1.5 lesson.
 */

@Composable
fun ForkAccountsRow(onOpenAccounts: () -> Unit) {
    ListItem(
        headlineContent = { Text(s("Accounts")) },
        supportingContent = { Text(s("Multi-account list, quotas and batch import")) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onOpenAccounts),
    )
}

@Composable
fun AntiBlockSettingsSection(settingsStore: SettingsStore) {
    var apiProxyEnabled by remember { mutableStateOf(settingsStore.apiProxyEnabled) }
    var apiProxyHost by remember { mutableStateOf(settingsStore.apiProxyHost) }
    var apiProxyPort by remember { mutableStateOf(settingsStore.apiProxyPort) }
    var apiProxyUsername by remember { mutableStateOf(settingsStore.apiProxyUsername) }
    var apiProxyPassword by remember { mutableStateOf(settingsStore.apiProxyPassword) }
    var apiRelayEnabled by remember { mutableStateOf(settingsStore.apiRelayEnabled) }
    var apiRelayUrl by remember { mutableStateOf(settingsStore.apiRelayUrl) }
    var showApiProxyAddressDialog by remember { mutableStateOf(false) }
    var showApiProxyCredentialsDialog by remember { mutableStateOf(false) }
    var showApiRelayUrlDialog by remember { mutableStateOf(false) }

    if (showApiProxyAddressDialog) {
        ApiProxyAddressDialog(
            currentHost = apiProxyHost,
            currentPort = apiProxyPort,
            onDismiss = { showApiProxyAddressDialog = false },
            onConfirm = { host, port ->
                apiProxyHost = host
                apiProxyPort = port
                settingsStore.apiProxyHost = host
                settingsStore.apiProxyPort = port
                showApiProxyAddressDialog = false
            },
        )
    }
    if (showApiProxyCredentialsDialog) {
        ApiProxyCredentialsDialog(
            currentUsername = apiProxyUsername,
            currentPassword = apiProxyPassword,
            onDismiss = { showApiProxyCredentialsDialog = false },
            onConfirm = { username, password ->
                apiProxyUsername = username
                apiProxyPassword = password
                settingsStore.apiProxyUsername = username
                settingsStore.apiProxyPassword = password
                // Credentials live in the secure store, whose writes emit
                // no plain-prefs change event — invalidate the control
                // plane's cached proxy/relay state explicitly.
                ControlPlaneHttp.onSettingsChanged()
                showApiProxyCredentialsDialog = false
            },
        )
    }
    if (showApiRelayUrlDialog) {
        ApiRelayUrlDialog(
            current = apiRelayUrl,
            onDismiss = { showApiRelayUrlDialog = false },
            onConfirm = {
                apiRelayUrl = it
                settingsStore.apiRelayUrl = it
                showApiRelayUrlDialog = false
            },
        )
    }

    ForkSectionLabel(s("When Mozilla can't be reached"))
    ForkToggleRow(
        title = s("Account proxy"),
        summary = s("Send sign-in and account requests through a SOCKS5 proxy"),
        checked = apiProxyEnabled,
        onCheckedChange = {
            apiProxyEnabled = it
            settingsStore.apiProxyEnabled = it
        },
    )
    if (apiProxyEnabled) {
        ListItem(
            headlineContent = { Text(s("Proxy address")) },
            supportingContent = {
                Text(if (apiProxyHost.isBlank()) "Not set" else "$apiProxyHost:$apiProxyPort")
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
            modifier = Modifier.clickable { showApiProxyAddressDialog = true },
        )
        ListItem(
            headlineContent = { Text(s("Proxy credentials")) },
            supportingContent = { Text(if (apiProxyUsername.isBlank()) "None" else apiProxyUsername) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
            modifier = Modifier.clickable { showApiProxyCredentialsDialog = true },
        )
    }
    ForkToggleRow(
        title = s("API relay"),
        summary = s("Retry account requests through your relay when they cannot go directly"),
        checked = apiRelayEnabled,
        onCheckedChange = {
            apiRelayEnabled = it
            settingsStore.apiRelayEnabled = it
        },
    )
    if (apiRelayEnabled) {
        ListItem(
            headlineContent = { Text(s("Relay address")) },
            supportingContent = { Text(apiRelayUrl.ifBlank { "Not set" }) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
            modifier = Modifier.clickable { showApiRelayUrlDialog = true },
        )
    }
}

// Local copies of the settings screen's private label/toggle shapes, so
// this section renders identically without touching upstream's file
// beyond the single call site.
@Composable
private fun ForkSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ForkToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier =
            Modifier.toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .heightIn(min = 48.dp),
    )
}

@Composable
private fun ApiProxyAddressDialog(
    currentHost: String,
    currentPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var host by remember { mutableStateOf(currentHost) }
    var portText by remember { mutableStateOf(currentPort.toString()) }
    val cleanedHost = SettingsStore.cleanProxyHost(host)
    val parsedPort = portText.toIntOrNull()
    val isValid = SettingsStore.isValidProxyHost(cleanedHost) &&
        parsedPort != null && SettingsStore.isValidProxyPort(parsedPort)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s("Proxy address")) },
        text = {
            Column {
                Text(
                    s("A SOCKS5 proxy for sign-in and account requests only — for example a proxy app running on this phone. The VPN tunnel itself does not use it."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(s("Host or IP")) },
                    singleLine = true,
                    isError = host.isNotBlank() && !SettingsStore.isValidProxyHost(cleanedHost),
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port (1–65535)") },
                    singleLine = true,
                    isError = portText.isNotBlank() && (parsedPort == null || !SettingsStore.isValidProxyPort(parsedPort)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedPort?.let { onConfirm(cleanedHost, it) } },
                enabled = isValid,
            ) { Text(s("Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s("Cancel")) }
        },
    )
}

@Composable
private fun ApiProxyCredentialsDialog(
    currentUsername: String,
    currentPassword: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf(currentUsername) }
    var password by remember { mutableStateOf(currentPassword) }
    val credentialsError = SettingsStore.proxyCredentialsError(username.trim(), password.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s("Proxy credentials")) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(s("Username (optional)")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(s("Password (optional)")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (credentialsError != null) {
                    Text(
                        credentialsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username.trim(), password.trim()) },
                enabled = credentialsError == null,
            ) { Text(s("Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s("Cancel")) }
        },
    )
}

@Composable
private fun ApiRelayUrlDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf(current) }
    val trimmed = url.trim()
    val isValid = trimmed.isEmpty() || SettingsStore.isValidRelayUrl(trimmed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s("Relay address")) },
        text = {
            Column {
                Text(
                    s("Your own relay — a small worker or script that forwards a request and answers with the reply. Account requests go directly first and through the relay only when that fails. The address must start with https://, because sign-in data crosses it."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Relay URL (https://...)") },
                    placeholder = { Text("https://relay.example.com/") },
                    singleLine = true,
                    isError = url.isNotBlank() && !isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = isValid) { Text(s("Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s("Cancel")) }
        },
    )
}
