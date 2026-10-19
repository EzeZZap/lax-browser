package com.javzzap.laxbrowser.ui.common

import android.graphics.Paint
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.os.Build
import java.util.Locale

/** An emoji and the words it can be found by (lowercase). */
data class EmojiEntry(val emoji: String, val keywords: String)

/**
 * Emoji search for the picker, which has none. Names come from the platform's Unicode data
 * (English), flags use country names in the app language, and [aliases] add everyday words in
 * English and Spanish. Matching is pure, so it is unit-tested.
 */
object EmojiSearch {
    private const val MAX_RESULTS = 120
    private const val VARIATION_SELECTOR = "\uFE0F"

    /** Common words that Unicode names do not contain. */
    private val aliases: Map<String, String> = mapOf(
        "❤️" to "love amor corazon corazón", "😍" to "love amor", "🥰" to "love amor",
        "😂" to "laugh lol risa", "🤣" to "laugh lol risa", "😆" to "laugh risa",
        "💼" to "work trabajo office oficina", "💻" to "work trabajo code codigo código computer computadora",
        "🎓" to "school escuela universidad university study estudio", "📚" to "school escuela study estudio libros",
        "🎮" to "games juegos gaming", "🕹️" to "games juegos gaming", "🎵" to "music musica música",
        "🎧" to "music musica música", "💰" to "money dinero", "💵" to "money dinero",
        "🛍️" to "shopping compras tienda", "🛒" to "shopping compras supermercado",
        "✈️" to "travel viaje vacaciones", "🧳" to "travel viaje", "🍕" to "food comida", "🍔" to "food comida",
        "🏠" to "home casa", "🏡" to "home casa", "🐱" to "gato", "🐶" to "perro", "⭐" to "estrella",
        "🔥" to "fuego", "☀️" to "sol", "🌙" to "luna", "🌱" to "plant planta naturaleza",
        "🧪" to "science ciencia lab", "🏋️" to "gym fitness ejercicio", "⚽" to "sport deporte futbol fútbol",
        "🎬" to "movies peliculas películas cine", "📰" to "news noticias", "💬" to "chat mensajes",
        "🛠️" to "tools herramientas projects proyectos", "🎨" to "art arte design diseño", "📷" to "photo foto",
        "🔒" to "private privado secure seguro", "💡" to "ideas", "📝" to "notes notas",
    )

    /** Emoji matching every word of [query], best matches (a word starting with it) first. */
    fun search(entries: List<EmojiEntry>, query: String): List<String> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { entry -> words.all { entry.keywords.contains(it) } }
            .sortedBy { entry -> if (entry.keywords.split(' ').any { it.startsWith(words.first()) }) 0 else 1 }
            .map { it.emoji }
            .distinct()
            .take(MAX_RESULTS)
            .toList()
    }

    /** True when [text] is something the user typed as an emoji itself (no letters or digits). */
    fun isTypedEmoji(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && trimmed.length <= 16 && trimmed.none { it.isLetterOrDigit() || it.isWhitespace() } &&
            trimmed.codePoints().anyMatch { it >= 0x2190 }
    }

    @Volatile
    private var cached: List<EmojiEntry>? = null

    /** Every emoji this device can draw, with its keywords. Slow the first time: call off the main thread. */
    fun catalog(locale: Locale): List<EmojiEntry> {
        cached?.let { return it }
        val paint = Paint()
        val entries = ArrayList<EmojiEntry>(4000)
        aliases.forEach { (emoji, words) -> entries += EmojiEntry(emoji, words) }
        for (range in RANGES) {
            for (cp in range) {
                if (!isEmoji(cp)) continue
                val name = UCharacter.getName(cp) ?: continue
                val presentation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_PRESENTATION)
                val emoji = String(Character.toChars(cp)) + if (presentation || cp >= 0x1F000) "" else VARIATION_SELECTOR
                if (!paint.hasGlyph(emoji)) continue
                entries += EmojiEntry(emoji, name.lowercase(Locale.ROOT))
            }
        }
        for (country in Locale.getISOCountries()) {
            val flag = country.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
            if (!paint.hasGlyph(flag)) continue
            val names = setOf(Locale("", country).getDisplayCountry(locale), Locale("", country).getDisplayCountry(Locale.ENGLISH))
            entries += EmojiEntry(flag, (names + "flag bandera").joinToString(" ").lowercase(locale))
        }
        return entries.also { cached = it }
    }

    private fun isEmoji(cp: Int): Boolean {
        // Skin tones, regional indicators (flags are added separately), and tags are only parts of emoji.
        if (cp in 0x1F3FB..0x1F3FF || cp in 0x1F1E6..0x1F1FF || cp in 0xE0000..0xE007F) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return UCharacter.hasBinaryProperty(cp, UProperty.EMOJI)
        return cp >= 0x1F300 || cp in 0x2600..0x27BF
    }

    private val RANGES = listOf(0x2190..0x2BFF, 0x3030..0x3030, 0x303D..0x303D, 0x3297..0x3299, 0x1F000..0x1FAFF)
}
