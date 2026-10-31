package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vauth.foxyvpn.ui.l10n.s

const val FIREFOX_SIGNUP_URL = "https://accounts.firefox.com/signup"

/**
 * Help row for the sign-in / add-account surfaces: a "Create Firefox
 * account" link (opens the FxA signup page in the browser) and the
 * Gmail dot-method explainer.
 */
@Composable
fun AccountHelpRow(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var showDotMethod by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(onClick = { uriHandler.openUri(FIREFOX_SIGNUP_URL) }) {
            Text(s("Create Firefox account"))
        }
        TextButton(onClick = { showDotMethod = true }) {
            Text(s("Dot method"))
        }
    }

    if (showDotMethod) {
        DotMethodDialog(onDismiss = { showDotMethod = false })
    }
}

@Composable
private fun DotMethodDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s("One Gmail, many accounts")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    s("Gmail ignores the dots in your address: a.b@gmail.com and ab@gmail.com are the same inbox."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    s("For a Firefox signup, each dotted variation counts as a brand-new email address — but every verification email arrives in that same one inbox."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                DotMethodStep(
                    number = 1,
                    text = s("Create a Firefox account with a new dotted variation of your Gmail address."),
                )
                DotMethodStep(
                    number = 2,
                    text = s("Open the verification email in your one inbox and confirm it, or note the code."),
                )
                DotMethodStep(
                    number = 3,
                    text = s("Come back here and add the account. Repeat for more accounts."),
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s("Example"), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.padding(top = 4.dp))
                        Text(
                            "name@gmail.com",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "n.ame@gmail.com · na.me@gmail.com · nam.e@gmail.com",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { uriHandler.openUri(FIREFOX_SIGNUP_URL) }) {
                Text(s("Create Firefox account"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s("Close"))
            }
        },
    )
}

@Composable
private fun DotMethodStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
