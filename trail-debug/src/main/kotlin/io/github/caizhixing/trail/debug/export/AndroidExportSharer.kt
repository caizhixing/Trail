package io.github.caizhixing.trail.debug.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

/**
 * System share-sheet glue (behavior 6.5). The exported file is exposed
 * through the manifest-declared FileProvider (authority
 * `<applicationId>.trail.fileprovider`, paths from `res/xml/file_paths.xml`)
 * as a `content://` Uri, granted to the receiver with
 * [Intent.FLAG_GRANT_READ_URI_PERMISSION], and wrapped in an explicit
 * [Intent.createChooser].
 */
internal object AndroidExportSharer {

    /** Manifest authority suffix: `android:authorities="${applicationId}.trail.fileprovider"`. */
    const val AUTHORITY_SUFFIX = ".trail.fileprovider"

    fun authority(packageName: String): String = packageName + AUTHORITY_SUFFIX

    fun shareIntent(context: Context, payload: ExportSharePayload): Intent {
        val contentUri = FileProvider.getUriForFile(context, authority(context.packageName), payload.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    fun launch(context: Context, payload: ExportSharePayload) {
        context.startActivity(shareIntent(context, payload))
    }

    private const val MIME_TYPE = "application/octet-stream"
}
