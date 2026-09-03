package dev.wizishan.stonks.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/**
 * The file-system side of backup: writing a share-able copy out, and reading a picked one
 * back in.
 *
 * The export lands in the cache directory and is handed out through a [FileProvider], so
 * the app never needs storage permissions — the share sheet grants the receiving app
 * read access to that one URI and nothing else.
 */
class BackupFiles(private val context: Context) {

    /** Overwrites the previous export rather than accumulating one file per tap. */
    fun writeExport(json: String, today: LocalDate = LocalDate.now()): Uri {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        val file = File(directory, fileName(today))
        directory.listFiles()?.forEach { if (it != file) it.delete() }
        file.writeText(json)
        return FileProvider.getUriForFile(context, "${context.packageName}.backups", file)
    }

    /**
     * Reads a document the user picked. Returns null if it could not be opened at all —
     * a revoked permission or a file that has since been deleted.
     */
    fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    fun fileName(today: LocalDate = LocalDate.now()): String = "stonks-backup-$today.json"

    private companion object {
        const val EXPORT_DIRECTORY = "backups"
    }
}
