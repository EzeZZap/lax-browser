package com.javzzap.laxbrowser.ui.onboarding

import androidx.annotation.StringRes
import com.javzzap.laxbrowser.R

/** A ready-made second Space offered during onboarding. */
data class SpacePreset(val id: String, @StringRes val name: Int, @StringRes val description: Int, val emoji: String, val color: Int)

val spacePresets = listOf(
    SpacePreset("work", R.string.preset_space_work, R.string.preset_space_work_desc, "💼", 0xFF1E88E5.toInt()),
    SpacePreset("school", R.string.preset_space_school, R.string.preset_space_school_desc, "🎓", 0xFF43A047.toInt()),
    SpacePreset("projects", R.string.preset_space_projects, R.string.preset_space_projects_desc, "🛠️", 0xFFF4511E.toInt()),
    SpacePreset("gaming", R.string.preset_space_gaming, R.string.preset_space_gaming_desc, "🎮", 0xFFD81B60.toInt()),
    SpacePreset("shopping", R.string.preset_space_shopping, R.string.preset_space_shopping_desc, "🛍️", 0xFF00897B.toInt()),
)

/** Popular sites offered as Essentials during onboarding. */
data class EssentialPreset(val title: String, val url: String)

val essentialPresets = listOf(
    EssentialPreset("Gmail", "https://mail.google.com/"),
    EssentialPreset("Outlook", "https://outlook.live.com/"),
    EssentialPreset("Google Calendar", "https://calendar.google.com/"),
    EssentialPreset("Google Drive", "https://drive.google.com/"),
    EssentialPreset("WhatsApp", "https://web.whatsapp.com/"),
    EssentialPreset("Discord", "https://discord.com/app"),
    EssentialPreset("YouTube", "https://www.youtube.com/"),
    EssentialPreset("Spotify", "https://open.spotify.com/"),
    EssentialPreset("Reddit", "https://www.reddit.com/"),
    EssentialPreset("Wikipedia", "https://www.wikipedia.org/"),
    EssentialPreset("GitHub", "https://github.com/"),
    EssentialPreset("Notion", "https://www.notion.so/"),
)
