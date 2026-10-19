package com.javzzap.laxbrowser.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.javzzap.laxbrowser.IntentReceiverActivity
import com.javzzap.laxbrowser.domain.BrowserOperations
import com.javzzap.laxbrowser.domain.UrlInput
import java.io.File
import java.io.FileOutputStream

/** Sends a rendered PDF to Android printing, which also offers "Save as PDF". */
object PdfPrinter {
    fun print(activity: Activity, pdf: File, jobName: String) {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(jobName.take(MAX_JOB_NAME), FileAdapter(pdf, jobName), PrintAttributes.Builder().build())
    }

    private class FileAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(safeFileName(name) + ".pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    private fun safeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(MAX_JOB_NAME).ifBlank { "page" }

    private const val MAX_JOB_NAME = 80
}

/** Plain home screen shortcuts that open a page in a new tab (not web apps). */
object HomeScreenShortcuts {
    private const val ICON_SIZE = 192

    fun add(context: Context, url: String, title: String, favicon: Bitmap?): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, IntentReceiverActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(url.toUri())
            .putExtra(IntentReceiverActivity.EXTRA_FROM_SHORTCUT, true)
        val shortcut = ShortcutInfoCompat.Builder(context, "page_" + url.hashCode())
            .setShortLabel(title.take(SHORT_LABEL).ifBlank { UrlInput.displayHost(url) })
            .setLongLabel(title.take(LONG_LABEL).ifBlank { UrlInput.displayHost(url) })
            .setIcon(IconCompat.createWithBitmap(iconFor(favicon, title, url)))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun iconFor(favicon: Bitmap?, title: String, url: String): Bitmap {
        val bitmap = createBitmap(ICON_SIZE, ICON_SIZE)
        val canvas = Canvas(bitmap)
        val host = UrlInput.displayHost(url)
        val background = BrowserOperations.DEFAULT_COLORS[(host.hashCode() and 0x7fffffff) % BrowserOperations.DEFAULT_COLORS.size]
        canvas.drawCircle(ICON_SIZE / 2f, ICON_SIZE / 2f, ICON_SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (favicon != null) 0xFFFFFFFF.toInt() else background })
        if (favicon != null) {
            val inset = ICON_SIZE / 5
            canvas.drawBitmap(favicon, null, android.graphics.Rect(inset, inset, ICON_SIZE - inset, ICON_SIZE - inset), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val letter = (title.ifBlank { host }).firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = ICON_SIZE * 0.45f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(letter, ICON_SIZE / 2f, ICON_SIZE / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        }
        return bitmap
    }

    private const val SHORT_LABEL = 12
    private const val LONG_LABEL = 25
}

/** Picture-in-picture aspect ratios, clamped to the range Android accepts (1:2.39 to 2.39:1). */
object PipRatio {
    private const val MAX = 2.39f

    fun of(width: Long, height: Long): android.util.Rational {
        val ratio = android.util.Rational(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        return when {
            ratio.toFloat() > MAX -> android.util.Rational(239, 100)
            ratio.toFloat() < 1 / MAX -> android.util.Rational(100, 239)
            else -> ratio
        }
    }
}

/** Picks what to open from text shared by another app. */
object SharedText {
    private val URL_PATTERN = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)
    private const val MAX_LENGTH = 2000

    /** The first web address in [text], or the trimmed text itself to search for. */
    fun extract(text: String): String? {
        val trimmed = text.trim().take(MAX_LENGTH)
        if (trimmed.isEmpty()) return null
        return URL_PATTERN.find(trimmed)?.value?.trimEnd('.', ',', ')', ';', '!', '?') ?: trimmed
    }
}
