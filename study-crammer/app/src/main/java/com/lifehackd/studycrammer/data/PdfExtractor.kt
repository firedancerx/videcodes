package com.lifehackd.studycrammer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PDF (PDFBox-Android) and plain-text import. Output is normalised; chunking happens in the session store. */
class PdfExtractor(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var initialized = false

    data class Result(val text: String, val pageCount: Int, val displayName: String, val truncated: Boolean)

    class ImportException(message: String) : Exception(message)

    suspend fun extract(uri: Uri): Result = withContext(Dispatchers.IO) {
        val (name, size) = queryMeta(uri)
        if (size != null && size > MAX_BYTES) {
            throw ImportException("That file is ${size / 1_048_576} MB — keep it under ${MAX_BYTES / 1_048_576} MB (export just this lecture).")
        }
        val mime = appContext.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("text/") || name.endsWith(".txt", ignoreCase = true)) {
            return@withContext readText(uri, name)
        }
        ensureInit()
        try {
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: throw ImportException("Couldn't open that file.")
            stream.use { input ->
                PDDocument.load(input).use { doc ->
                    val pages = doc.numberOfPages
                    val stripper = PDFTextStripper()
                    stripper.setSortByPosition(true)
                    stripper.setStartPage(1)
                    stripper.setEndPage(minOf(pages, MAX_PAGES))
                    val text = TextChunker.normalize(stripper.getText(doc))
                    if (text.length < StudySession.MIN_NOTES_CHARS) {
                        throw ImportException("No selectable text found — this PDF is probably scanned images. Paste the text instead.")
                    }
                    Result(text, pages, name, pages > MAX_PAGES)
                }
            }
        } catch (e: InvalidPasswordException) {
            throw ImportException("This PDF is password-protected. Remove the password and try again.")
        } catch (e: ImportException) {
            throw e
        } catch (e: IOException) {
            throw ImportException("That doesn't look like a readable PDF.")
        } catch (e: OutOfMemoryError) {
            throw ImportException("That PDF is too heavy for this phone. Export fewer slides and retry.")
        }
    }

    private fun readText(uri: Uri, name: String): Result {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ImportException("Couldn't open that file.")
        val text = TextChunker.normalize(String(bytes, Charsets.UTF_8))
        if (text.length < StudySession.MIN_NOTES_CHARS) throw ImportException("That file is basically empty.")
        return Result(text, 1, name, false)
    }

    private fun ensureInit() {
        if (!initialized) synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(appContext)
                initialized = true
            }
        }
    }

    private fun queryMeta(uri: Uri): Pair<String, Long?> {
        var name = "document"
        var size: Long? = null
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        return name to size
    }

    companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        const val MAX_PAGES = 400
    }
}
