package io.github.caizhixing.trail.debug.export

import java.io.File

/**
 * Pure description of one completed export, handed to the UI layer to launch
 * the system share sheet. Carries no android types beyond [File] so the
 * ViewModel and its tests stay JVM-pure; the FileProvider authority is
 * derived by [AndroidExportSharer] from the process package name at share
 * time, not carried here.
 */
internal data class ExportSharePayload(
    val file: File,
    val eventCount: Int,
)
