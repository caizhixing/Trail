package io.github.caizhixing.trail.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

class ModuleRuntimeDependenciesTest {

    @Test
    fun `contract types load from a single module artifact`() {
        val moduleLocation = requireNotNull(codeSourceLocationOf(LogEvent::class.java))

        listOf(
            LogEvent::class.java,
            Audience::class.java,
            Level::class.java,
            TrailSchema::class.java,
            TrailConfig::class.java,
        ).forEach { type ->
            assertEquals(moduleLocation, codeSourceLocationOf(type))
        }
    }

    @Test
    fun `constructor signatures reference only jdk or module types`() {
        val moduleLocation = requireNotNull(codeSourceLocationOf(LogEvent::class.java))

        listOf(LogEvent::class.java, TrailConfig::class.java)
            .flatMap { type ->
                type.declaredConstructors
                    .filter { constructor -> !constructor.isSynthetic }
                    .flatMap { constructor -> constructor.parameterTypes.toList() }
            }
            .forEach { parameterType ->
                val source = codeSourceLocationOf(parameterType)
                assertTrue(
                    "unexpected third-party type in api surface: $parameterType",
                    source == null || source == moduleLocation,
                )
            }
    }

    @Test
    fun `compiled classes stay inside model package`() {
        val moduleLocation = requireNotNull(codeSourceLocationOf(LogEvent::class.java))
        val moduleRoot = File(moduleLocation.toURI())
        assertTrue(moduleRoot.isDirectory)

        val classPaths = moduleRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "class" }
            .map { file -> file.relativeTo(moduleRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(classPaths.isNotEmpty())
        classPaths.forEach { path ->
            assertTrue(
                "unexpected class outside model package: $path",
                path.startsWith("io/github/caizhixing/trail/model/"),
            )
        }
    }

    private fun codeSourceLocationOf(type: Class<*>): URL? =
        type.protectionDomain.codeSource?.location
}
