package io.github.caizhixing.trail.debug.export

/**
 * Viewer-injected export seam: the ViewModel never touches the snapshot store
 * or the filesystem directly. Implementations run blocking on the caller's
 * IO dispatcher and return the finished payload or a [ResultState.Error] —
 * they must not throw for an export that fails, only for cancellation.
 */
internal interface TrailEventExporter {

    fun export(scope: ExportScope): ResultState<ExportSharePayload>
}
