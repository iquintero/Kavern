package com.iquintero.kavern.core.logging

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Logging wrapper that writes to logcat and to rotating log files for debugging.
 *
 * Notes:
 * - Avoid logging secrets; message is scrubbed for common sensitive tokens.
 * - File logging can be enabled/disabled independently from logcat logging.
 */
object LoggingManager {
    private const val MAX_FILE_SIZE_BYTES: Long = 1_000_000 // ~1MB
    private const val MAX_FILES = 5
    private const val MAX_FILE_AGE_MS: Long = 12L * 24 * 60 * 60 * 1000 // 12 days
    private const val FILE_PREFIX = "kavern-log"
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var debugEnabled: Boolean = false
    private var errorsEnabled: Boolean = true
    private var fileLoggingEnabled: Boolean = true
    private var logDir: File? = null
    private var hostMasks: List<Regex> = emptyList()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logDir = dir
        pruneOldFiles()
    }

    fun setEnabled(value: Boolean) {
        debugEnabled = value
    }

    fun setErrorsEnabled(value: Boolean) {
        errorsEnabled = value
    }

    fun updateHostMask(host: String?) {
        val h = host?.trim().orEmpty()
        val masks =
            if (h.isNotBlank()) {
                listOf(Regex(Regex.escape(h), RegexOption.IGNORE_CASE))
            } else {
                emptyList()
            }
        hostMasks = masks
    }

    fun setFileLoggingEnabled(value: Boolean) {
        fileLoggingEnabled = value
    }

    fun d(
        tag: String,
        msg: String,
    ) {
        if (!debugEnabled && !fileLoggingEnabled) return
        val text = scrub(msg)
        if (debugEnabled) Log.d(tag, text)
        writeToFile("D", tag, text)
    }

    fun i(
        tag: String,
        msg: String,
    ) {
        if (!debugEnabled && !fileLoggingEnabled) return
        val text = scrub(msg)
        if (debugEnabled) Log.i(tag, text)
        writeToFile("I", tag, text)
    }

    fun w(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        val text = scrubbedMessage(msg, tr)
        if (debugEnabled || errorsEnabled) Log.w(tag, text)
        writeToFile("W", tag, text)
    }

    fun e(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        val text = scrubbedMessage(msg, tr)
        if (errorsEnabled) Log.e(tag, text)
        writeToFile("E", tag, text)
    }

    /** Message plus stack trace, scrubbed once so logcat and the log files get the same text. */
    internal fun scrubbedMessage(
        msg: String,
        tr: Throwable?,
    ): String = if (tr == null) scrub(msg) else scrub(msg + "\n" + tr.stackTraceToString())

    fun isDebugEnabled(): Boolean = debugEnabled

    /**
     * Bundle existing log files into a zip ready for sharing.
     */
    fun exportLogsForShare(
        context: Context,
        extras: Map<String, String> = emptyMap(),
    ): File? {
        val dir = logDir ?: return null
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return null
        if (files.isEmpty() && extras.isEmpty()) return null

        val zipName = "kavern-logs-${System.currentTimeMillis()}.zip"
        val outFile = File(context.cacheDir, zipName)
        return buildZip(files, extras, outFile)
    }

    /**
     * Clear all local log files.
     */
    fun clearLogs() {
        logDir?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun writeToFile(
        level: String,
        tag: String,
        msg: String,
    ) {
        if (!fileLoggingEnabled) return
        val dir = logDir ?: return
        ioScope.launch {
            try {
                val file = currentFile(dir)
                val ts = timestampFormat.format(Date())
                val payload =
                    buildString {
                        append(ts)
                            .append(" ")
                            .append(level)
                            .append("/")
                            .append(tag)
                            .append(": ")
                        append(msg)
                        append("\n")
                    }
                FileOutputStream(file, true).bufferedWriter().use { it.write(payload) }
                rotateIfNeeded(file, dir)
                pruneOldFiles()
            } catch (e: Exception) {
                // Fallback to logcat only to avoid crash loops.
                if (errorsEnabled) Log.e("LoggingManager", "Failed to write log file", e)
            }
        }
    }

    private fun currentFile(dir: File): File {
        val existing =
            dir
                .listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.extension == "txt" }
                ?.maxByOrNull { it.lastModified() }
        return existing ?: File(dir, "$FILE_PREFIX-1.txt")
    }

    private fun rotateIfNeeded(
        file: File,
        dir: File,
    ) {
        if (file.length() <= MAX_FILE_SIZE_BYTES) return
        // Shift older files
        val files =
            dir
                .listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.extension == "txt" }
                ?.sortedByDescending { it.nameWithoutExtension }
                ?: emptyList()
        files.forEach { f ->
            val idx = f.nameWithoutExtension.substringAfter("$FILE_PREFIX-").toIntOrNull() ?: return@forEach
            val nextIdx = idx + 1
            if (nextIdx > MAX_FILES) {
                runCatching { f.delete() }
            } else {
                val target = File(dir, "$FILE_PREFIX-$nextIdx.txt")
                runCatching { f.renameTo(target) }
            }
        }
        // Start fresh file 1
        File(dir, "$FILE_PREFIX-1.txt").writeText("")
    }

    private fun pruneOldFiles() {
        val dir = logDir ?: return
        val now = System.currentTimeMillis()
        val files =
            dir
                .listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.extension == "txt" }
                ?.sortedBy { it.lastModified() }
                ?: return
        files.filter { now - it.lastModified() > MAX_FILE_AGE_MS }.forEach { runCatching { it.delete() } }
        val remaining =
            dir
                .listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.extension == "txt" }
                ?.sortedBy { it.lastModified() }
                ?: return
        if (remaining.size <= MAX_FILES) return
        val toDelete = remaining.take(remaining.size - MAX_FILES)
        toDelete.forEach { runCatching { it.delete() } }
    }

    internal fun scrub(input: String): String {
        var out = input
        val patterns =
            listOf(
                "api[_-]?key=([^\\s]+)" to "api_key=***",
                "Authorization: Bearer ([^\\s]+)" to "Authorization: Bearer ***",
                "token=([^\\s]+)" to "token=***",
                "serverUrl=([^\\s]+)" to "serverUrl=***",
                "x-api-key: ([^\\s]+)" to "x-api-key: ***",
            )
        patterns.forEach { (pattern, replacement) ->
            out = out.replace(Regex(pattern, RegexOption.IGNORE_CASE), replacement)
        }
        // Mask URLs/hosts/IPs to avoid leaking server info but keep stack trace readability.
        val ipPattern = Regex("\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?")
        out = out.replace(ipPattern, "[ip]")
        // Mask scheme URLs host part.
        out = out.replace(Regex("(https?://)([^/\\s]+)"), "$1[host]")
        hostMasks.forEach { mask -> out = out.replace(mask, "[host]") }
        out = out.replace(Regex("\\blocalhost(?::\\d+)?\\b", RegexOption.IGNORE_CASE), "[host]")
        return out
    }

    /**
     * Save logs to the app's external Documents/Kavern/logs directory for manual access.
     */
    fun saveLogsToDocuments(
        context: Context,
        extras: Map<String, String> = emptyMap(),
    ): File? {
        val dir = logDir ?: return null
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return null
        if (files.isEmpty() && extras.isEmpty()) return null

        val docsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) ?: return null
        val targetDir = File(docsRoot, "Kavern/logs").apply { if (!exists()) mkdirs() }
        val outFile = File(targetDir, "kavern-logs-${System.currentTimeMillis()}.zip")
        return buildZip(files, extras, outFile)
    }

    private fun buildZip(
        files: List<File>,
        extras: Map<String, String>,
        target: File,
    ): File? =
        runCatching {
            ZipOutputStream(FileOutputStream(target)).use { zos ->
                files.forEach { f ->
                    zos.putNextEntry(ZipEntry(f.name))
                    val sanitized = scrub(f.readText())
                    zos.write(sanitized.toByteArray())
                    zos.closeEntry()
                }
                extras.forEach { (name, content) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(scrub(content).toByteArray())
                    zos.closeEntry()
                }
            }
            target
        }.getOrNull()
}
