package com.javzzap.laxbrowser.ui.onboarding

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.core.app.ActivityOptionsCompat
import com.javzzap.laxbrowser.domain.EssentialPick
import com.javzzap.laxbrowser.domain.OnboardingChoices
import com.javzzap.laxbrowser.domain.SpaceSetup
import com.javzzap.laxbrowser.ui.extensions.recommendedExtensions
import com.javzzap.laxbrowser.ui.theme.LaxTheme

/** Every onboarding page: 0–3 introduce Lax, 4–9 are the setup steps. */
private class OnboardingPageProvider : PreviewParameterProvider<Int> {
    override val values = (0..9).asSequence()
}

/** Previews have no activity, so file pickers and the default-browser dialog launch nothing. */
private val previewRegistryOwner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
    }
}

private val sampleChoices = OnboardingChoices(
    space = SpaceSetup("Work", "💼", 0xFF1E88E5.toInt()),
    essentials = listOf(EssentialPick("https://mail.google.com/", "Gmail"), EssentialPick("https://www.youtube.com/", "YouTube")),
)

@Composable
private fun OnboardingPreview(page: Int, dark: Boolean = false) {
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides previewRegistryOwner) {
        LaxTheme(darkTheme = dark) {
            OnboardingContent(
                homeSpaceName = "Personal",
                initialChoices = sampleChoices,
                installedExtensionIds = setOf(recommendedExtensions.first().id),
                installingExtensionIds = setOf(recommendedExtensions[1].id),
                onInstallExtension = {},
                onImportBookmarks = { _, _ -> },
                onDone = {},
                initialPage = page,
            )
        }
    }
}

@Preview(name = "Onboarding", showBackground = true, widthDp = 400, heightDp = 860)
@Composable
private fun OnboardingPagesPreview(@PreviewParameter(OnboardingPageProvider::class) page: Int) {
    OnboardingPreview(page)
}

@Preview(name = "Onboarding · dark", showBackground = true, widthDp = 400, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OnboardingDarkPreview(@PreviewParameter(OnboardingPageProvider::class) page: Int) {
    OnboardingPreview(page, dark = true)
}
