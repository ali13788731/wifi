package com.example.netspeed.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads are written to an app-private temp file (safe positional writes, no SAF quirks) and,
 * only after verification, moved to the user's chosen SAF folder or the app download folder.
 */
@Singleton
class StorageHelper @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun tempFile(id: Long): File =
        File(ctx.getExternalFilesDir("parts") ?: ctx.filesDir, "dl_$id.part")

    private fun defaultDir(): File =
        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir

    /** Returns the final location (content:// uri or file path) as a string. */
    fun finalize(temp: File, name: String, treeUri: String?, mime: String?, overwrite: Boolean): String {
        if (treeUri != null) {
            val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri))
            if (tree != null && tree.canWrite()) {
                val existing = tree.findFile(name)
                if (existing != null && overwrite) existing.delete()
                val finalName = if (overwrite) name else uniqueName(name) { tree.findFile(it) != null }
                val doc = tree.createFile(mime ?: "application/octet-stream", finalName)
                    ?: error("Cannot create file in the selected folder")
                ctx.contentResolver.openOutputStream(doc.uri, "w")!!.use { out ->
                    temp.inputStream().use { it.copyTo(out, 256 * 1024) }
                }
                temp.delete()
                return doc.uri.toString()
            }
            // folder permission lost -> fall through to the default folder
        }
        val dir = defaultDir().apply { mkdirs() }
        var target = File(dir, name)
        if (!overwrite) target = File(dir, uniqueName(name) { File(dir, it).exists() })
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, true); temp.delete()
        }
        return target.absolutePath
    }

    private fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (exists("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    private fun uriFor(path: String): Uri =
        if (path.startsWith("content://")) Uri.parse(path)
        else FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(path))

    private fun mimeOf(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun openIntent(path: String): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(path), mimeOf(path))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun shareIntent(path: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType(mimeOf(path))
            .putExtra(Intent.EXTRA_STREAM, uriFor(path))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun delete(path: String?) {
        path ?: return
        runCatching {
            if (path.startsWith("content://")) DocumentFile.fromSingleUri(ctx, Uri.parse(path))?.delete()
            else File(path).delete()
        }
    }

    fun exists(path: String?): Boolean = path != null && runCatching {
        if (path.startsWith("content://")) DocumentFile.fromSingleUri(ctx, Uri.parse(path))?.exists() == true
        else File(path).exists()
    }.getOrDefault(false)
}
