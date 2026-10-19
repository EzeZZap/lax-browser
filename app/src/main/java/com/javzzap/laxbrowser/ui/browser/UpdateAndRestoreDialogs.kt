package com.javzzap.laxbrowser.ui.browser

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.platform.UpdateProgress

/** An in-app update: download progress, the install permission, installing, or what went wrong. */
@Composable
internal fun UpdateProgressDialog(
    progress: UpdateProgress,
    canInstall: () -> Boolean,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // Coming back from the "install unknown apps" screen continues the update once allowed.
    if (progress is UpdateProgress.NeedsPermission) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (canInstall()) onInstall() }
    }
    val busy = progress is UpdateProgress.Downloading || progress is UpdateProgress.Installing
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(dismissOnClickOutside = !busy),
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = {
            Text(
                when (progress) {
                    is UpdateProgress.Downloading -> stringResource(R.string.update_downloading_title, progress.version)
                    is UpdateProgress.Ready -> stringResource(R.string.update_ready_title, progress.version)
                    is UpdateProgress.NeedsPermission -> stringResource(R.string.update_permission_title)
                    is UpdateProgress.Installing -> stringResource(R.string.update_installing_title, progress.version)
                    is UpdateProgress.Failed -> stringResource(R.string.update_failed_title)
                },
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when (progress) {
                    is UpdateProgress.Downloading -> {
                        val fraction = progress.fraction
                        if (fraction != null) {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    is UpdateProgress.Ready -> Text(stringResource(R.string.update_ready_body))
                    is UpdateProgress.NeedsPermission -> Text(stringResource(R.string.update_permission_body))
                    is UpdateProgress.Installing -> {
                        Text(stringResource(R.string.update_installing_body))
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpdateProgress.Failed -> Text(
                        stringResource(
                            when (progress.reason) {
                                UpdateProgress.Reason.DOWNLOAD -> R.string.update_failed_download
                                UpdateProgress.Reason.VERIFY -> R.string.update_failed_verify
                                UpdateProgress.Reason.INSTALL -> R.string.update_failed_install
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            when (progress) {
                is UpdateProgress.Ready -> TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
                is UpdateProgress.NeedsPermission -> TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    runCatching { context.startActivity(intent) }
                }) { Text(stringResource(R.string.update_permission_open)) }
                is UpdateProgress.Failed -> TextButton(onClick = onOpenPage) { Text(stringResource(R.string.update_open_page)) }
                is UpdateProgress.Downloading, is UpdateProgress.Installing -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(if (progress is UpdateProgress.Downloading) R.string.cancel else R.string.close))
            }
        },
    )
}

/** After restoring a backup with saved logins: asks for the backup password, or skips the logins. */
@Composable
internal fun RestoreLoginsDialog(count: Int, onRestore: (password: CharArray, onWrongPassword: () -> Unit) -> Unit, onSkip: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    fun submit() {
        if (password.isEmpty() || working) return
        working = true
        wrong = false
        onRestore(password.toCharArray()) {
            working = false
            wrong = true
        }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.restore_logins_title)) },
        text = {
            Column {
                Text(
                    if (count >= 0) pluralStringResource(R.plurals.restore_logins_body, count, count) else stringResource(R.string.restore_logins_body_unknown),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        wrong = false
                    },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(R.string.restore_logins_wrong_password)) }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = password.isNotEmpty() && !working) { Text(stringResource(R.string.restore_logins_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onSkip, enabled = !working) { Text(stringResource(R.string.restore_logins_skip), color = MaterialTheme.colorScheme.error) }
        },
    )
}
