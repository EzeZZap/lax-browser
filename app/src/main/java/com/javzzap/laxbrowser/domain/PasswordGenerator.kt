package com.javzzap.laxbrowser.domain

import java.security.SecureRandom
import java.util.Random

/**
 * Random passwords for new accounts. Letters that are easy to confuse (l, I, O, 0, 1) are left
 * out, and every enabled kind of character appears at least once, so sites that require digits or
 * symbols accept the result.
 */
object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val DEFAULT_LENGTH = 20

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "-_.!@#$%^&*+=?"

    fun generate(length: Int = DEFAULT_LENGTH, digits: Boolean = true, symbols: Boolean = true, random: Random = SecureRandom()): String {
        val groups = buildList {
            add(LOWER)
            add(UPPER)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        val all = groups.joinToString("")
        val size = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val chars = CharArray(size) { index ->
            val group = groups.getOrNull(index) ?: all
            group[random.nextInt(group.length)]
        }
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            chars[i] = chars[j].also { chars[j] = chars[i] }
        }
        return String(chars)
    }
}
