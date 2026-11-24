package com.javzzap.laxbrowser.engine.gecko

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packs a [SavedPageArchive] into a ZIP with `index.html` and a `resources/` folder. */
object PageArchive {
    fun zip(archive: SavedPageArchive): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("index.html"))
            zip.write(archive.html)
            zip.closeEntry()
            val seen = mutableSetOf("index.html")
            for (file in archive.resources) {
                val path = file.path.takeIf { it !in seen } ?: continue
                seen += path
                zip.putNextEntry(ZipEntry(path))
                zip.write(file.bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
