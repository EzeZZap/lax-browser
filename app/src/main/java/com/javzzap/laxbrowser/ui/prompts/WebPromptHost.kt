package com.javzzap.laxbrowser.ui.prompts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.engine.gecko.PermissionRequest
import com.javzzap.laxbrowser.engine.gecko.SitePermission
import com.javzzap.laxbrowser.engine.gecko.WebPrompt
import com.javzzap.laxbrowser.ui.common.ColorPickerDialog
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Shows web page dialogs and permission requests, one at a time. [accepts] limits which tabs'
 * requests this screen handles (for example only its own session in a Custom Tab).
 */
@Composable
fun WebPromptHost(
    prompts: StateFlow<List<WebPrompt>>,
    permissions: StateFlow<List<PermissionRequest>>,
    accepts: (String) -> Boolean = { true },
) {
    val promptList by prompts.collectAsStateWithLifecycle()
    val requestList by permissions.collectAsStateWithLifecycle()
    val request = requestList.firstOrNull { accepts(it.tabId) }
    if (request != null) {
        PermissionDialog(request)
        return
    }
    promptList.firstOrNull { accepts(it.tabId) }?.let { PromptDialog(it) }
}

@Composable
private fun PromptDialog(prompt: WebPrompt) {
    when (prompt) {
        is WebPrompt.Alert -> SimpleDialog(prompt.title, prompt.message, stringResource(R.string.ok), onConfirm = prompt::ok, onDismiss = prompt::cancel, dismissLabel = null)
        is WebPrompt.Confirm -> SimpleDialog(
            prompt.title, prompt.message, stringResource(R.string.ok),
            onConfirm = { prompt.answer(true) }, onDismiss = { prompt.answer(false) },
        )
        is WebPrompt.Text -> TextPromptDialog(prompt)
        is WebPrompt.Auth -> AuthDialog(prompt)
        is WebPrompt.Choice -> ChoiceDialog(prompt)
        is WebPrompt.Color -> ColorPickerDialog(
            title = prompt.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.choose_color),
            initial = prompt.initial,
            presets = prompt.presets,
            onPick = prompt::pick,
            onDismiss = prompt::cancel,
        )
        is WebPrompt.DateTime -> DateTimeDialog(prompt)
        is WebPrompt.FilePick -> FilePicker(prompt)
        is WebPrompt.BeforeUnload -> SimpleDialog(
            stringResource(R.string.prompt_leave_title), stringResource(R.string.prompt_leave_message), stringResource(R.string.prompt_leave),
            onConfirm = { prompt.answer(true) }, onDismiss = { prompt.answer(false) }, dismissLabel = stringResource(R.string.prompt_stay),
        )
        is WebPrompt.Repost -> SimpleDialog(
            stringResource(R.string.prompt_repost_title), stringResource(R.string.prompt_repost_message), stringResource(R.string.prompt_resend),
            onConfirm = { prompt.answer(true) }, onDismiss = { prompt.answer(false) },
        )
        is WebPrompt.Blocked -> SimpleDialog(
            stringResource(if (prompt.isRedirect) R.string.prompt_redirect_title else R.string.prompt_popup_title),
            prompt.targetUri?.let { stringResource(R.string.prompt_blocked_target, it) },
            stringResource(R.string.allow),
            onConfirm = { prompt.answer(true) }, onDismiss = { prompt.answer(false) }, dismissLabel = stringResource(R.string.block),
        )
        is WebPrompt.Share -> ShareLauncher(prompt)
        is WebPrompt.LoginSave -> LoginSaveDialog(prompt)
        is WebPrompt.LoginSelect -> LoginSelectDialog(prompt)
    }
}

/** Page text is shown as plain text and limited in length, so a page cannot fill the screen with it. */
private fun pageText(text: String?) = text?.take(MAX_PAGE_TEXT)?.takeIf { it.isNotBlank() }

@Composable
private fun SimpleDialog(
    title: String?,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = stringResource(R.string.cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = pageText(title)?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        text = pageText(message)?.let { { Text(it, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = dismissLabel?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
    )
}

@Composable
private fun TextPromptDialog(prompt: WebPrompt.Text) {
    var value by remember(prompt) { mutableStateOf(prompt.defaultValue) }
    AlertDialog(
        onDismissRequest = prompt::cancel,
        title = pageText(prompt.title)?.let { { Text(it) } },
        text = {
            Column {
                pageText(prompt.message)?.let {
                    Text(it)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { prompt.submit(value) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = prompt::cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AuthDialog(prompt: WebPrompt.Auth) {
    var username by remember(prompt) { mutableStateOf(prompt.defaultUsername) }
    var password by remember(prompt) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = prompt::cancel,
        title = { Text(prompt.host?.let { stringResource(R.string.prompt_auth_title_host, it) } ?: stringResource(R.string.prompt_auth_title)) },
        text = {
            Column {
                pageText(prompt.message)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                if (prompt.insecure) {
                    Text(stringResource(R.string.prompt_auth_insecure), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                if (!prompt.passwordOnly) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.prompt_username)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.prompt_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { prompt.submit(username, password) }) { Text(stringResource(R.string.prompt_sign_in)) } },
        dismissButton = { TextButton(onClick = prompt::cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChoiceDialog(prompt: WebPrompt.Choice) {
    var selected by remember(prompt) { mutableStateOf(prompt.items.filter { it.selected && !it.isHeader }.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = prompt::cancel,
        title = pageText(prompt.title)?.let { { Text(it) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(prompt.items) { item ->
                    when {
                        item.isSeparator -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        item.isHeader -> Text(
                            item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        prompt.multiple -> Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = item.id in selected, enabled = item.enabled, role = Role.Checkbox) { checked ->
                                    selected = if (checked) selected + item.id else selected - item.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = item.id in selected, onCheckedChange = null, enabled = item.enabled)
                            Spacer(Modifier.width(12.dp))
                            Text(item.label)
                        }
                        else -> Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = item.id in selected, enabled = item.enabled, role = Role.RadioButton) { prompt.select(listOf(item.id)) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = item.id in selected, onClick = null, enabled = item.enabled)
                            Spacer(Modifier.width(12.dp))
                            Text(item.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (prompt.multiple) TextButton(onClick = { prompt.select(selected.toList()) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = prompt::cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialog(prompt: WebPrompt.DateTime) {
    // datetime-local asks for the date first, then the time.
    var pickedDate by remember(prompt) { mutableStateOf<LocalDate?>(null) }
    val needsDate = prompt.type != WebPrompt.DateTimeType.TIME && pickedDate == null
    if (needsDate) {
        val initial = DateTimeValues.initialDate(prompt.type, prompt.value)
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = prompt::cancel,
            confirmButton = {
                TextButton(onClick = {
                    val date = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: return@TextButton
                    if (prompt.type == WebPrompt.DateTimeType.DATETIME_LOCAL) pickedDate = date else prompt.pick(DateTimeValues.format(prompt.type, date, null))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = prompt::clear) { Text(stringResource(R.string.clear)) }
                    TextButton(onClick = prompt::cancel) { Text(stringResource(R.string.cancel)) }
                }
            },
        ) { DatePicker(state = dateState) }
        return
    }
    val initialTime = DateTimeValues.initialTime(prompt.value) ?: LocalTime.now()
    val timeState = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute)
    AlertDialog(
        onDismissRequest = prompt::cancel,
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(onClick = {
                prompt.pick(DateTimeValues.format(prompt.type, pickedDate, LocalTime.of(timeState.hour, timeState.minute)))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = prompt::clear) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = prompt::cancel) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** HTML date/time input values (yyyy-MM-dd, yyyy-MM, yyyy-Www, HH:mm, yyyy-MM-ddTHH:mm). */
internal object DateTimeValues {
    fun initialDate(type: WebPrompt.DateTimeType, value: String?): LocalDate? = runCatching {
        val v = value?.trim().orEmpty()
        when (type) {
            WebPrompt.DateTimeType.MONTH -> LocalDate.parse("$v-01")
            WebPrompt.DateTimeType.WEEK -> {
                val (year, week) = v.split("-W").let { it[0].toInt() to it[1].toInt() }
                LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            WebPrompt.DateTimeType.DATETIME_LOCAL -> LocalDate.parse(v.substringBefore('T'))
            else -> LocalDate.parse(v)
        }
    }.getOrNull()

    fun initialTime(value: String?): LocalTime? = runCatching {
        val v = value?.trim().orEmpty().let { if ('T' in it) it.substringAfter('T') else it }
        LocalTime.parse(v.take(5))
    }.getOrNull()

    fun format(type: WebPrompt.DateTimeType, date: LocalDate?, time: LocalTime?): String {
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        return when (type) {
            WebPrompt.DateTimeType.DATE -> date!!.toString()
            WebPrompt.DateTimeType.MONTH -> "%04d-%02d".format(date!!.year, date.monthValue)
            WebPrompt.DateTimeType.WEEK -> "%04d-W%02d".format(date!!.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            WebPrompt.DateTimeType.TIME -> time!!.format(hm)
            WebPrompt.DateTimeType.DATETIME_LOCAL -> "${date!!}T${time!!.format(hm)}"
        }
    }
}

@Composable
private fun FilePicker(prompt: WebPrompt.FilePick) {
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) prompt.cancel() else prompt.pick(listOf(uri))
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) prompt.cancel() else prompt.pick(uris)
    }
    LaunchedEffect(prompt) {
        val types = prompt.mimeTypes.map { it.lowercase() }.filter { '/' in it }.ifEmpty { listOf("*/*") }.toTypedArray()
        try {
            if (prompt.multiple) multiple.launch(types) else single.launch(types)
        } catch (_: ActivityNotFoundException) {
            prompt.cancel()
        }
    }
}

/** "Save login for example.com?" with Save, Never for this site, and Not now. */
@Composable
private fun LoginSaveDialog(prompt: WebPrompt.LoginSave) {
    AlertDialog(
        onDismissRequest = prompt::cancel,
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text(stringResource(if (prompt.isUpdate) R.string.login_update_title else R.string.login_save_title, prompt.host)) },
        text = {
            Column {
                if (prompt.username.isNotBlank()) {
                    Text(prompt.username, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    stringResource(R.string.login_save_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { prompt.answer(WebPrompt.LoginSaveAnswer.SAVE) }) {
                Text(stringResource(if (prompt.isUpdate) R.string.login_update else R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (!prompt.isUpdate) {
                    TextButton(onClick = { prompt.answer(WebPrompt.LoginSaveAnswer.NEVER) }) { Text(stringResource(R.string.login_never)) }
                }
                TextButton(onClick = { prompt.answer(WebPrompt.LoginSaveAnswer.NOT_NOW) }) { Text(stringResource(R.string.login_not_now)) }
            }
        },
    )
}

/** Picks which saved login fills the form, or fills Gecko's suggested strong password. */
@Composable
private fun LoginSelectDialog(prompt: WebPrompt.LoginSelect) {
    val generated = prompt.generatedPassword
    if (prompt.usernames.isEmpty() && generated != null) {
        AlertDialog(
            onDismissRequest = prompt::cancel,
            icon = { Icon(Icons.Default.Password, contentDescription = null) },
            title = { Text(stringResource(R.string.login_generated_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.login_generated_body, prompt.host))
                    Spacer(Modifier.height(12.dp))
                    Text(generated, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = { TextButton(onClick = prompt::useGeneratedPassword) { Text(stringResource(R.string.login_generated_use)) } },
            dismissButton = { TextButton(onClick = prompt::cancel) { Text(stringResource(R.string.login_not_now)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = prompt::cancel,
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.login_select_title, prompt.host)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                prompt.usernames.forEachIndexed { index, username ->
                    Text(
                        username.ifBlank { stringResource(R.string.login_no_username) },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { prompt.pick(index) }.padding(vertical = 12.dp),
                    )
                }
                if (generated != null) {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = prompt::useGeneratedPassword).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Password, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.login_generated_use), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = prompt::cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ShareLauncher(prompt: WebPrompt.Share) {
    val context = LocalContext.current
    LaunchedEffect(prompt) {
        val text = listOfNotNull(prompt.text?.takeIf { it.isNotBlank() }, prompt.url).joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        prompt.title?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
        val shared = runCatching { context.startActivity(Intent.createChooser(send, null)) }.isSuccess
        prompt.shared(shared)
    }
}

@Composable
private fun PermissionDialog(request: PermissionRequest) {
    when (request) {
        is PermissionRequest.Android -> AndroidPermissionLauncher(request)
        is PermissionRequest.Content -> ContentPermissionDialog(request)
        is PermissionRequest.Media -> MediaPermissionDialog(request)
    }
}

@Composable
private fun AndroidPermissionLauncher(request: PermissionRequest.Android) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        request.result(results.isNotEmpty() && results.values.any { it })
    }
    LaunchedEffect(request) { launcher.launch(request.permissions.toTypedArray()) }
}

@Composable
private fun ContentPermissionDialog(request: PermissionRequest.Content) {
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val message = if (request.permission == SitePermission.STORAGE_ACCESS && request.thirdPartyHost != null) {
        stringResource(R.string.permission_storage_access, request.thirdPartyHost, request.host)
    } else {
        stringResource(R.string.permission_request, request.host, stringResource(permissionLabel(request.permission)))
    }
    AlertDialog(
        onDismissRequest = request::deny,
        title = { Text(stringResource(R.string.permission_title)) },
        text = {
            Column {
                Text(message)
                if (request.isPrivate) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.permission_private_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                request.answer(true)
                // Website notifications also need Android's notification permission.
                if (request.permission == SitePermission.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) { Text(stringResource(R.string.allow)) }
        },
        dismissButton = { TextButton(onClick = request::deny) { Text(stringResource(R.string.block)) } },
    )
}

@Composable
private fun MediaPermissionDialog(request: PermissionRequest.Media) {
    var camera by remember(request) { mutableStateOf(request.cameras.firstOrNull { it.isFrontCamera } ?: request.cameras.firstOrNull()) }
    val what = when {
        request.cameras.isNotEmpty() && request.microphones.isNotEmpty() -> R.string.permission_camera_microphone
        request.cameras.isNotEmpty() -> R.string.permission_camera
        else -> R.string.permission_microphone
    }
    AlertDialog(
        onDismissRequest = request::deny,
        title = { Text(stringResource(R.string.permission_title)) },
        text = {
            Column {
                Text(stringResource(R.string.permission_request, request.host, stringResource(what)))
                if (request.cameras.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    request.cameras.forEach { device ->
                        Row(
                            Modifier.fillMaxWidth().clickable { camera = device }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = camera == device, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    device.isFrontCamera -> stringResource(R.string.camera_front)
                                    device.name.contains("back", ignoreCase = true) -> stringResource(R.string.camera_back)
                                    else -> device.name.ifBlank { device.id }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { request.allow(camera?.id, request.microphones.firstOrNull()?.id) }) { Text(stringResource(R.string.allow)) }
        },
        dismissButton = { TextButton(onClick = request::deny) { Text(stringResource(R.string.block)) } },
    )
}

fun permissionLabel(permission: SitePermission): Int = when (permission) {
    SitePermission.LOCATION -> R.string.permission_location
    SitePermission.NOTIFICATIONS -> R.string.permission_notifications
    SitePermission.CAMERA -> R.string.permission_camera
    SitePermission.MICROPHONE -> R.string.permission_microphone
    SitePermission.DRM -> R.string.permission_drm
    SitePermission.AUTOPLAY_AUDIBLE -> R.string.permission_autoplay_audible
    SitePermission.AUTOPLAY_INAUDIBLE -> R.string.permission_autoplay_inaudible
    SitePermission.PERSISTENT_STORAGE -> R.string.permission_persistent_storage
    SitePermission.STORAGE_ACCESS -> R.string.permission_storage_access_short
    SitePermission.LOCAL_NETWORK -> R.string.permission_local_network
    SitePermission.LOCAL_DEVICE -> R.string.permission_local_device
}

private const val MAX_PAGE_TEXT = 2000
