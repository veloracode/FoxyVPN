package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.model.BatchImportItemResult
import com.vauth.foxyvpn.ui.l10n.s
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Guided one-by-one batch import. Entries are processed strictly in order:
 * entries that sign in cleanly are added immediately; when FxA asks for an
 * email verification code the import PAUSES on that entry (the session is
 * kept pending in the repository), the user types the code, the entry is
 * added, and the loop automatically continues with the next line. Each
 * paused entry can also be skipped. Cancelling stops the loop and keeps
 * whatever was imported so far.
 */
@Composable
fun GuidedBatchImport(
    authRepository: FxaAuthRepository,
    onImported: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<FxaAuthRepository.BatchParsedItem>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<BatchImportItemResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var awaitingEmail by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun launchAdvance() {
        job = scope.launch {
            processing = true
            try {
                while (index < items.size) {
                    val item = items[index]
                    when (val step = authRepository.importBatchItem(item)) {
                        is FxaAuthRepository.GuidedImportStep.Done -> {
                            results = results + step.result
                            index += 1
                        }
                        is FxaAuthRepository.GuidedImportStep.NeedsCode -> {
                            awaitingEmail = step.item.email
                            code = ""
                            codeError = null
                            notice = null
                            processing = false
                            return@launch
                        }
                    }
                }
                running = false
                processing = false
                if (results.any { it.success }) onImported()
            } catch (cancelled: CancellationException) {
                processing = false
                throw cancelled
            }
        }
    }

    fun stopImport() {
        job?.cancel()
        if (awaitingEmail != null) authRepository.abandonPendingLogin()
        awaitingEmail = null
        processing = false
        running = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Paste one entry per line (email:password) or a JSON array of " +
                "{email, password} / {email, accessToken, refreshToken} objects. " +
                "Accounts that ask for an email code pause here so you can enter it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text("name@example.com:password") },
            enabled = !running,
        )
        formError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (running && awaitingEmail == null && index < items.size) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (processing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "${index + 1}/${items.size} · ${items[index].email}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        awaitingEmail?.let { pausedEmail ->
            Text(s("Enter the confirmation code sent to your email"), style = MaterialTheme.typography.bodyMedium)
            Text(
                pausedEmail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.replace(Regex("[\\s]+"), "") },
                label = { Text(s("Confirmation code")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            codeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        verifying = true
                        codeError = null
                        scope.launch {
                            authRepository.submitPendingAccountCode(code)
                                .onSuccess { account ->
                                    results = results + BatchImportItemResult(
                                        identifier = pausedEmail,
                                        success = true,
                                        account = account,
                                    )
                                    awaitingEmail = null
                                    index += 1
                                    verifying = false
                                    launchAdvance()
                                }
                                .onFailure {
                                    codeError = it.message ?: "Verification failed"
                                    verifying = false
                                }
                        }
                    },
                    enabled = !verifying && code.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (verifying) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(s("Verify"))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            authRepository.resendPendingCode()
                                .onSuccess { notice = "A new code was sent." }
                                .onFailure { codeError = it.message ?: "Could not resend the code" }
                        }
                    },
                    enabled = !verifying,
                ) {
                    Text(s("Resend code"))
                }
                TextButton(
                    onClick = {
                        authRepository.abandonPendingLogin()
                        results = results + BatchImportItemResult(
                            identifier = pausedEmail,
                            success = false,
                            error = "Skipped",
                        )
                        awaitingEmail = null
                        index += 1
                        launchAdvance()
                    },
                    enabled = !verifying,
                ) {
                    Text(s("Skip"))
                }
            }
        }

        if (results.isNotEmpty()) {
            val okCount = results.count { it.success }
            Text(
                "Imported $okCount of ${results.size}",
                style = MaterialTheme.typography.titleSmall,
            )
            results.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.identifier,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (r.success) "✓" else "✗ ${r.error.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (r.success) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (running) {
            OutlinedButton(
                onClick = { stopImport() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s("Cancel"))
            }
        } else {
            Button(
                onClick = {
                    val parsed = authRepository.parseBatchInput(input)
                    if (parsed.isEmpty()) {
                        formError = "Nothing to import — check the pasted lines."
                    } else {
                        formError = null
                        codeError = null
                        items = parsed
                        index = 0
                        results = emptyList()
                        awaitingEmail = null
                        running = true
                        launchAdvance()
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s("Import"))
            }
        }

        AccountHelpRow()
    }
}
