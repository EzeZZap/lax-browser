package com.javzzap.laxbrowser.ui.logins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.PasswordGenerator
import kotlin.math.roundToInt

/** Makes a random password to copy into a sign-up form; nothing is saved until the site's form is submitted. */
@Composable
fun PasswordGeneratorDialog(onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    var length by remember { mutableIntStateOf(PasswordGenerator.DEFAULT_LENGTH) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf(PasswordGenerator.generate(length, digits, symbols)) }
    fun regenerate() {
        password = PasswordGenerator.generate(length, digits, symbols)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Password, contentDescription = null) },
        title = { Text(stringResource(R.string.password_generator_title)) },
        text = {
            Column {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(password, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                        IconButton(onClick = ::regenerate) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.password_generator_new))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.password_generator_length, length), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = length.toFloat(),
                    onValueChange = { value ->
                        val rounded = value.roundToInt()
                        if (rounded != length) {
                            length = rounded
                            regenerate()
                        }
                    },
                    valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
                )
                OptionSwitch(stringResource(R.string.password_generator_digits), digits) {
                    digits = it
                    regenerate()
                }
                OptionSwitch(stringResource(R.string.password_generator_symbols), symbols) {
                    symbols = it
                    regenerate()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCopy(password)
                onDismiss()
            }) { Text(stringResource(R.string.password_generator_copy)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
