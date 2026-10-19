package com.javzzap.laxbrowser.domain

/** One group of changes: an optional heading ("Privacy and security"), notes, and list items. */
data class ChangelogSection(val title: String?, val notes: List<String>, val items: List<String>)

data class ChangelogRelease(val version: String, val sections: List<ChangelogSection>)

/**
 * Reads CHANGELOG.md, bundled in the app: `## 0.2.1` starts a release, `### Title` a group, `- `
 * a list item (indented lines continue it), and other lines are notes. Sections without a version
 * number, such as "Unreleased", are left out.
 */
object Changelog {
    private val version = Regex("""\d+(\.\d+)*""")

    fun parse(text: String): List<ChangelogRelease> {
        val releases = ArrayList<ChangelogRelease>()
        var currentVersion: String? = null
        val sections = ArrayList<ChangelogSection>()
        var title: String? = null
        var notes = ArrayList<String>()
        var items = ArrayList<String>()

        fun closeSection() {
            if (title != null || notes.isNotEmpty() || items.isNotEmpty()) sections += ChangelogSection(title, notes, items)
            title = null
            notes = ArrayList()
            items = ArrayList()
        }
        fun closeRelease() {
            closeSection()
            currentVersion?.let { releases += ChangelogRelease(it, sections.toList()) }
            sections.clear()
            currentVersion = null
        }

        var skipping = true
        // A line right below a list item continues it, indented or not; a blank line ends the item.
        var inItem = false
        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            val wasInItem = inItem
            inItem = false
            when {
                line.startsWith("## ") -> {
                    closeRelease()
                    val name = line.removePrefix("## ").trim().removePrefix("v").substringBefore(' ')
                    skipping = !version.matches(name)
                    if (!skipping) currentVersion = name
                }
                skipping || line.startsWith("# ") -> Unit
                line.startsWith("### ") -> {
                    closeSection()
                    title = clean(line.removePrefix("### "))
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    items += clean(line.drop(2))
                    inItem = true
                }
                line.isBlank() -> Unit
                wasInItem || (raw.startsWith(" ") && items.isNotEmpty()) -> {
                    items[items.lastIndex] = items.last() + " " + clean(line)
                    inItem = true
                }
                else -> notes += clean(line)
            }
        }
        closeRelease()
        return releases
    }

    /** Drops Markdown emphasis and code marks; the app shows plain text. */
    private fun clean(text: String): String = text.trim().replace("**", "").replace("`", "")
}

/** When to show "What's new": once after an update, not on a fresh install (onboarding shows instead). */
object WhatsNew {
    fun shouldShow(lastSeenVersion: String?, currentVersion: String, onboardingDone: Boolean, isNewer: (String, String) -> Boolean): Boolean = when {
        !onboardingDone -> false
        // Installed before Lax remembered the version: this launch is the first after an update.
        lastSeenVersion == null -> true
        else -> isNewer(currentVersion, lastSeenVersion)
    }
}
