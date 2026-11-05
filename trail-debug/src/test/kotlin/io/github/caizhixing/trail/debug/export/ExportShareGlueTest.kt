package io.github.caizhixing.trail.debug.export

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 6.5 glue, statically checked from the JVM (framework share-sheet
 * code is exercised on device only): the manifest declares the FileProvider
 * for `cacheDir/trail_exports` with `res/xml/file_paths.xml`, and the intent
 * target class exists with the authority derivation the manifest literal
 * encodes.
 */
class ExportShareGlueTest {

    @Test
    fun manifestDeclaresTheFileProviderForExports() {
        val manifest = manifestFile().readText()
        val provider = manifest.substringAfter("<provider").substringBefore("</provider>")

        assertTrue(
            "provider backed by androidx FileProvider",
            provider.contains("android:name=\"androidx.core.content.FileProvider\""),
        )
        assertTrue(
            "authority is applicationId-scoped so multiple apps can host the library",
            provider.contains("android:authorities=\"\${applicationId}.trail.fileprovider\""),
        )
        assertTrue(
            "the provider itself is not exported",
            provider.contains("android:exported=\"false\""),
        )
        assertTrue(
            "receivers get read access via grantUriPermissions",
            provider.contains("android:grantUriPermissions=\"true\""),
        )
        assertTrue(
            "paths come from the frozen resource",
            provider.contains("android:resource=\"@xml/file_paths\""),
        )
    }

    @Test
    fun filePathsExposeExactlyTheExportCacheDirectory() {
        val paths = filePathsFile().readText()

        assertTrue("only cache-dir sharing", paths.contains("<cache-path"))
        assertTrue("the exporter's default write dir", paths.contains("path=\"trail_exports/\""))
    }

    @Test
    fun authorityDerivationMatchesTheManifestLiteral() {
        assertEquals(".trail.fileprovider", AndroidExportSharer.AUTHORITY_SUFFIX)
        assertEquals(
            "com.example.app.trail.fileprovider",
            AndroidExportSharer.authority("com.example.app"),
        )
        assertTrue(
            "manifest literal and code derivation stay in sync",
            manifestFile().readText().contains("\${applicationId}" + AndroidExportSharer.AUTHORITY_SUFFIX),
        )
    }

    @Test
    fun sharerTargetClassExists() {
        assertNotNull(
            "the share intent builder is part of the built target",
            Class.forName("io.github.caizhixing.trail.debug.export.AndroidExportSharer"),
        )
    }

    @Test
    fun payloadIsPureJvmData() {
        val payload = ExportSharePayload(file = File("exports", "trail_export_20261105_123456.jsonl"), eventCount = 7)

        assertEquals(7, payload.eventCount)
        assertEquals("trail_export_20261105_123456.jsonl", payload.file.name)
    }

    private fun manifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("trail-debug/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("trail-debug AndroidManifest.xml not found from ${File(".").absolutePath}")
    }

    private fun filePathsFile(): File {
        val candidates = listOf(
            File("src/main/res/xml/file_paths.xml"),
            File("trail-debug/src/main/res/xml/file_paths.xml"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("file_paths.xml not found from ${File(".").absolutePath}")
    }
}
