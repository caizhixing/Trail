package io.github.caizhixing.trail.debug.trace

import io.github.caizhixing.trail.Trail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 4.1–4.4, 4.7: withTrace id lifecycle, structured-concurrency
 * propagation matrix, cancellation hygiene, and uniqueness under load.
 */
class TracePropagationTest {

    /** 行为 4.1 */
    @Test
    fun withTraceGivesStableIdInsideBlockAndRestoresNullAfter() = runBlocking {
        val result = Trail.withTrace("trace_scope_under_test") {
            val first = requireNotNull(Trail.currentTraceId())
            val second = requireNotNull(Trail.currentTraceId())

            assertEquals("id must be stable inside the block", first, second)
            "payload_value"
        }

        assertEquals("block value must be returned", "payload_value", result)
        assertNull("trace must be gone after the block", Trail.currentTraceId())
    }

    /** 行为 4.1 + 4.7 格式 */
    @Test
    fun withTraceIdMatchesContractFormat() = runBlocking {
        val id = Trail.withTrace("trace_scope_under_test") { Trail.currentTraceId() }

        assertNotNull(id)
        assertTrue("expected t_ + 16 hex chars, got: $id", TRAIL_ID_FORMAT.matches(requireNotNull(id)))
    }

    /** 行为 4.1 异常路径 + 4.4 出块无残留 */
    @Test
    fun withTracePropagatesBlockExceptionAndStillRestoresNull() = runBlocking {
        val boom = IllegalStateException("trace_boom")
        val thrown = runCatching {
            Trail.withTrace<String>("trace_scope_under_test") { throw boom }
        }.exceptionOrNull()

        // kotlinx.coroutines stack-trace recovery copies the exception instance
        // when it crosses the withContext boundary; type and message are kept.
        assertTrue(
            "block exception must pass through as IllegalStateException, was $thrown",
            thrown is IllegalStateException,
        )
        assertEquals("block exception message must be preserved", boom.message, thrown?.message)
        assertNull("trace must be gone after a failed block", Trail.currentTraceId())
    }

    /** 行为 4.2 */
    @Test
    fun nestedWithTraceUsesFreshInnerIdAndRestoresOuter() = runBlocking {
        val scopes = Trail.withTrace("trace_scope_under_test") {
            val outerId = requireNotNull(Trail.currentTraceId())
            val innerId = Trail.withTrace("trace_scope_under_test") {
                requireNotNull(Trail.currentTraceId())
            }
            val afterInnerId = requireNotNull(Trail.currentTraceId())
            Triple(outerId, innerId, afterInnerId)
        }

        assertTrue(
            "inner scope must get a fresh id, both were ${scopes.second}",
            scopes.first != scopes.second,
        )
        assertEquals("closing the inner scope must restore the outer id", scopes.first, scopes.third)
        assertNull(Trail.currentTraceId())
    }

    /**
     * 行为 4.3 · launch。块签名 `suspend () -> T` 冻结为无 receiver（PRD §2.3），
     * trace 的结构化子协程须经 coroutineScope 创建——继承含 trace 元素的当前
     * 上下文（行为 4.4 "trace 随 Job 结构化并发走"）；裸 launch 会捕获块外
     * 作用域、逃逸 trace 子树。
     */
    @Test
    fun traceIdPropagatesToLaunchedChild() = runBlocking {
        val childSaw = CompletableDeferred<String?>()
        val outerId = Trail.withTrace("trace_scope_under_test") {
            val expected = requireNotNull(Trail.currentTraceId())
            coroutineScope { launch { childSaw.complete(Trail.currentTraceId()) } }
            expected
        }

        assertEquals("launched child must read the outer trace id", outerId, childSaw.await())
        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.3 · async.await */
    @Test
    fun traceIdPropagatesThroughAsyncAwait() = runBlocking {
        val observed = Trail.withTrace("trace_scope_under_test") {
            val expected = requireNotNull(Trail.currentTraceId())
            val childSaw = coroutineScope { async { Trail.currentTraceId() }.await() }
            Pair(expected, childSaw)
        }

        assertEquals("async child must read the outer trace id", observed.first, observed.second)
        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.3 · withContext(Dispatchers.Default) 真实线程切换 */
    @Test
    fun traceIdPropagatesAcrossThreadHopToDefaultDispatcher() = runBlocking {
        val callerThread = Thread.currentThread().name
        var outerId: String? = null
        val hop = Trail.withTrace("trace_scope_under_test") {
            outerId = requireNotNull(Trail.currentTraceId())
            withContext(Dispatchers.Default) {
                Pair(requireNotNull(Trail.currentTraceId()), Thread.currentThread().name != callerThread)
            }
        }

        assertNotNull(outerId)
        assertEquals("trace id must survive the thread hop", outerId, hop.first)
        assertTrue("withContext(Default) must actually switch threads", hop.second)
        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.3 · flow builder collect */
    @Test
    fun traceIdPropagatesIntoFlowBuilderCollect() = runBlocking {
        val collected = mutableListOf<String?>()
        Trail.withTrace("trace_scope_under_test") {
            val expected = requireNotNull(Trail.currentTraceId())
            flow { emit(Trail.currentTraceId()) }.collect { id ->
                collected.add(id)
                assertEquals("collector must read the outer trace id", expected, id)
            }
        }

        assertEquals(1, collected.size)
        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.3 · map/onEach 操作符（flowOn 把上游切到 worker 线程） */
    @Test
    fun traceIdPropagatesThroughFlowOperatorsOnWorkerThreads() = runBlocking {
        val seenByOnEach = mutableListOf<String?>()
        val mapped = Trail.withTrace("trace_scope_under_test") {
            flowOf(Unit)
                .map { Trail.currentTraceId() }
                .flowOn(Dispatchers.Default)
                .onEach { id -> seenByOnEach.add(Trail.currentTraceId()) }
                .single()
        }

        assertNotNull("map transform must read a trace id", mapped)
        assertEquals("onEach must see the same trace id as map", mapped, seenByOnEach.single())
        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.4 · 子协程取消不破坏外层 trace */
    @Test
    fun childCancellationInsideTraceLeavesOuterTraceIntact() = runBlocking {
        Trail.withTrace("trace_scope_under_test") {
            coroutineScope {
                val child = launch { awaitCancellation() }
                child.cancelAndJoin()
            }

            assertNotNull("outer trace must survive child cancellation", Trail.currentTraceId())
        }

        assertNull(Trail.currentTraceId())
    }

    /** 行为 4.4 · 块自身被取消后线程状态无残留 */
    @Test
    fun cancelledWithTraceBlockRestoresThreadState() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            Trail.withTrace<String>("trace_scope_under_test") {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        job.cancelAndJoin()

        assertNull("cancelled trace must leave no thread residue", Trail.currentTraceId())
    }

    /** 行为 4.7 · 1000 并发 withTrace id 唯一 */
    @Test
    fun thousandConcurrentWithTracesProduceUniqueIds() = runBlocking {
        val ids = coroutineScope {
            (1..1_000).map {
                async(Dispatchers.Default) {
                    Trail.withTrace("trace_scope_under_test") {
                        val first = requireNotNull(Trail.currentTraceId())
                        val second = Trail.currentTraceId()
                        assertEquals("id must be stable inside concurrent block", first, second)
                        first
                    }
                }
            }.awaitAll()
        }

        assertEquals(1_000, ids.size)
        assertEquals(
            "concurrent trace ids must be unique",
            1_000,
            ids.map { requireNotNull(it) }.distinct().size,
        )
        ids.forEach { id ->
            assertTrue("expected t_ + 16 hex chars, got: $id", TRAIL_ID_FORMAT.matches(requireNotNull(id)))
        }
    }

    private companion object {
        // DECISIONS.md P-7: traceId = t_ + 16 hex
        val TRAIL_ID_FORMAT = Regex("t_[0-9a-f]{16}")
    }
}
