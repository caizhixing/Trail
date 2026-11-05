package io.github.caizhixing.trail.okhttp

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Behavior 7.8: the module stays JVM-pure — no android classes reachable and
 * the compileOnly spi stub never leaked onto a runtime classpath (if it did,
 * every interceptor test would silently run against the empty stub facade).
 */
class ModuleJvmPurityTest {

    @Test
    fun androidClassesAreAbsentFromTheModuleTestJvm() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("android.content.Context")
        }
    }

    @Test
    fun onlyTheTestFacadeIsOnTheRuntimeClasspath() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("io.github.caizhixing.trail.TrailGate")
        }
    }
}
