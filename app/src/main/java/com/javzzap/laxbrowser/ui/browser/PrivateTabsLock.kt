package com.javzzap.laxbrowser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.platform.DeviceAuth

/**
 * Covers private tabs after the user left Lax with the lock on, until the screen lock is
 * confirmed. Leaving private browsing needs no unlock; the private tabs stay hidden and open.
 */
@Composable
fun PrivateTabsLock(onUnlocked: () -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.private_lock_auth_title)
    fun unlock() {
        // Without a screen lock there is nothing to check (the setting cannot be turned on then).
        if (!DeviceAuth.isAvailable(context)) onUnlocked() else activity?.let { DeviceAuth.authenticate(it, title, onUnlocked) }
    }
    LaunchedEffect(Unit) { unlock() }
    BackHandler(onBack = onLeave)

    Surface(
        Modifier
            .fillMaxSize()
            // Touches must not reach the page underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.private_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.private_lock_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 400.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = ::unlock) { Text(stringResource(R.string.private_lock_unlock)) }
            TextButton(onClick = onLeave) { Text(stringResource(R.string.private_lock_leave)) }
        }
    }
}
