package com.javzzap.laxbrowser.ui.extensions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javzzap.laxbrowser.R

/**
 * A well-known extension from addons.mozilla.org. [id] is the add-on's ID, used to tell whether it
 * is already installed; Gecko verifies the signature of the downloaded package.
 */
data class RecommendedExtension(
    val id: String,
    val name: String,
    val slug: String,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
) {
    val installUrl: String get() = "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"
    val listingUrl: String get() = "https://addons.mozilla.org/android/addon/$slug/"
}

val recommendedExtensions = listOf(
    RecommendedExtension("uBlock0@raymondhill.net", "uBlock Origin", "ublock-origin", R.string.rec_ext_ublock, R.drawable.ext_ublock_origin),
    RecommendedExtension(
        "{446900e4-71c2-419f-a6a7-df9c091e268b}", "Bitwarden", "bitwarden-password-manager", R.string.rec_ext_bitwarden, R.drawable.ext_bitwarden,
    ),
    RecommendedExtension("addon@darkreader.org", "Dark Reader", "darkreader", R.string.rec_ext_dark_reader, R.drawable.ext_dark_reader),
    RecommendedExtension("gdpr@cavi.au.dk", "Consent-O-Matic", "consent-o-matic", R.string.rec_ext_consent, R.drawable.ext_consent_o_matic),
    RecommendedExtension("sponsorBlocker@ajay.app", "SponsorBlock", "sponsorblock", R.string.rec_ext_sponsorblock, R.drawable.ext_sponsorblock),
    RecommendedExtension("jid1-MnnxcxisBPnSXQ@jetpack", "Privacy Badger", "privacy-badger17", R.string.rec_ext_privacy_badger, R.drawable.ext_privacy_badger),
)

/**
 * Rows of recommended extensions, each with an add button that installs it directly. With
 * [onDetails], tapping a row opens its store page.
 */
@Composable
fun RecommendedExtensionList(
    installedIds: Set<String>,
    installingIds: Set<String>,
    onAdd: (RecommendedExtension) -> Unit,
    modifier: Modifier = Modifier,
    onDetails: ((RecommendedExtension) -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        recommendedExtensions.forEach { extension ->
            RecommendedRow(extension, installed = extension.id in installedIds, installing = extension.id in installingIds, onAdd, onDetails)
        }
    }
}

@Composable
private fun RecommendedRow(
    extension: RecommendedExtension,
    installed: Boolean,
    installing: Boolean,
    onAdd: (RecommendedExtension) -> Unit,
    onDetails: ((RecommendedExtension) -> Unit)?,
) {
    val detailsLabel = stringResource(R.string.rec_ext_details, extension.name)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onDetails != null) Modifier.clickable(onClickLabel = detailsLabel) { onDetails(extension) } else Modifier)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bundled icons, so the list shows them without contacting the store.
        Image(painterResource(extension.icon), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(extension.name, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(extension.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        when {
            installed -> {
                val label = stringResource(R.string.rec_ext_added)
                Icon(
                    Icons.Default.Check,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = label },
                )
            }
            installing -> CircularProgressIndicator(Modifier.padding(horizontal = 20.dp).size(24.dp), strokeWidth = 2.dp)
            else -> FilledTonalButton(onClick = { onAdd(extension) }) { Text(stringResource(R.string.add)) }
        }
    }
}
