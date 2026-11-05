package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.LogEvent
import java.io.File
import java.io.Writer

/**
 * Rolling JSONL file store (behavior 3.8). Files are named `trail_<index>.jsonl`
 * with a monotonically increasing index; the next store instance resumes after
 * the highest existing index so restarts keep the order stable.
 *
 * Rolling rule: a line is appended to the current file while
 * `currentBytes + lineBytes <= maxFileBytes` — reaching exactly the limit does
 * NOT roll; exceeding it opens a new file and then prunes the oldest files
 * down to [maxFiles]. All IO failures are contained: they report through
 * [ioWarner] and drop the line instead of crashing the host app.
 */
internal class JsonlFileStore(
    private val dir: File,
    private val maxFileBytes: Long,
    private val maxFiles: Int,
    private val sinkFactory: (File) -> Writer = { file -> file.bufferedWriter(Charsets.UTF_8) },
    private val ioWarner: (String) -> Unit = { },
) {

    private var currentWriter: Writer? = null
    private var currentFile: File? = null
    private var currentBytes: Long = 0L
    private var nextIndex: Int = 0

    init {
        nextIndex = (listTrailFiles().maxOfOrNull { indexOf(it) ?: -1 }?.plus(1)) ?: 0
    }

    fun append(line: String) {
        if (maxFiles <= 0 || maxFileBytes <= 0L) {
            return
        }
        val lineBytes = line.utf8Size() + NEWLINE_BYTES
        val writer = currentWriter
        if (writer == null || currentBytes + lineBytes > maxFileBytes) {
            if (!roll()) {
                return
            }
        }
        val active = currentWriter ?: return
        val result = runCatching {
            active.write(line)
            active.write("\n")
            active.flush()
        }
        if (result.isFailure) {
            abandonActive()
            ioWarner("trail file write failed: ${result.exceptionOrNull()?.javaClass?.simpleName}")
        } else {
            currentBytes += lineBytes
        }
    }

    fun close() {
        abandonActive()
    }

    /** Oldest-first list of managed files (empty when dir is unusable). */
    fun filesOldestFirst(): List<File> = listTrailFiles().sortedBy { indexOf(it) ?: Int.MAX_VALUE }

    fun fileCount(): Int = listTrailFiles().size

    /**
     * Decodes up to [limit] events starting at line [offset], walking files
     * oldest-first; corrupt lines occupy their slot but decode to nothing.
     * A file that vanishes mid-read (concurrent prune) is skipped.
     */
    fun readEvents(offset: Int, limit: Int): List<LogEvent> {
        if (offset < 0 || limit <= 0) {
            return emptyList()
        }
        val out = ArrayList<LogEvent>(minOf(limit, 256))
        var lineIndex = 0
        for (file in filesOldestFirst()) {
            if (lineIndex >= offset + limit) {
                break
            }
            val lines = runCatching { file.useLines { it.toList() } }.getOrNull() ?: continue
            for (line in lines) {
                if (lineIndex >= offset + limit) {
                    break
                }
                if (lineIndex >= offset) {
                    JsonlDecoder.decodeEvent(line)?.let { out.add(it) }
                }
                lineIndex++
            }
        }
        return out
    }

    fun eventCount(): Int {
        var count = 0
        for (file in filesOldestFirst()) {
            count += runCatching { file.useLines { it.count() } }.getOrNull() ?: 0
        }
        return count
    }

    private fun roll(): Boolean {
        abandonActive()
        if (!ensureDir()) {
            return false
        }
        val file = File(dir, FILE_NAME_PREFIX + nextIndex.toString().padStart(INDEX_WIDTH, '0') + FILE_NAME_SUFFIX)
        val writer = runCatching { sinkFactory(file) }
        nextIndex++
        if (writer.isFailure) {
            ioWarner("trail file open failed: ${writer.exceptionOrNull()?.javaClass?.simpleName}")
            return false
        }
        currentFile = file
        currentWriter = writer.getOrThrow()
        currentBytes = 0L
        pruneOldest()
        return true
    }

    private fun pruneOldest() {
        val files = filesOldestFirst().toMutableList()
        var excess = files.size - maxFiles
        var cursor = 0
        while (excess > 0 && cursor < files.size) {
            if (files[cursor].delete()) {
                excess--
            }
            cursor++
        }
    }

    private fun ensureDir(): Boolean {
        if (dir.isDirectory) {
            return true
        }
        if (!dir.exists()) {
            val created = runCatching { dir.mkdirs() }.getOrDefault(false)
            if (!created) {
                ioWarner("trail dir unavailable: ${dir.path}")
                return false
            }
            return true
        }
        ioWarner("trail dir unavailable: ${dir.path}")
        return false
    }

    private fun abandonActive() {
        currentWriter?.let { writer -> runCatching { writer.close() } }
        currentWriter = null
        currentFile = null
        currentBytes = 0L
    }

    private fun listTrailFiles(): List<File> {
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        return files.filter { it.isFile && FILE_NAME_PATTERN.matches(it.name) }
    }

    private fun indexOf(file: File): Int? =
        FILE_NAME_PATTERN.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    private companion object {
        const val NEWLINE_BYTES = 1
        const val INDEX_WIDTH = 4
        const val FILE_NAME_PREFIX = "trail_"
        const val FILE_NAME_SUFFIX = ".jsonl"
        val FILE_NAME_PATTERN = Regex("trail_(\\d+)\\.jsonl")
    }
}
