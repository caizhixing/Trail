package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.debug.export.ExportScope
import io.github.caizhixing.trail.debug.export.ExportSharePayload
import io.github.caizhixing.trail.debug.export.ResultState
import io.github.caizhixing.trail.debug.export.TrailEventExporter
import io.github.caizhixing.trail.model.LogEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ViewerViewModel export flow (issue #6): the export request runs on the
 * injected IO dispatcher with the injected [TrailEventExporter], publishes
 * Loading → Success/Error, guards concurrent requests, and the share payload
 * is consumed exactly once.
 */
class ViewerExportFlowTest {

    private class FakeViewerRepository : ViewerRepository {
        override fun memoryEvents(): List<LogEvent> = emptyList()
        override fun fileEvents(offset: Int, limit: Int): List<LogEvent> = emptyList()
        override fun fileEventCount(): Int = 0
    }

    private class FakePreferences : ViewerPreferences {
        var stored: Boolean? = null
        override fun conciseView(): Boolean? = stored
        override fun setConciseView(value: Boolean) {
            stored = value
        }
    }

    /** Hand-written fake (no mock libraries): scripted result + call log. */
    private class FakeExporter(
        var result: ResultState<ExportSharePayload>,
    ) : TrailEventExporter {
        val requests = mutableListOf<ExportScope>()
        var duringExport: () -> Unit = {}

        override fun export(scope: ExportScope): ResultState<ExportSharePayload> {
            requests.add(scope)
            duringExport()
            return result
        }
    }

    private fun payload() = ExportSharePayload(
        file = File("exports", "trail_export_20261105_123456.jsonl"),
        eventCount = 3,
    )

    private fun newViewModel(exporter: TrailEventExporter): ViewerViewModel = ViewerViewModel(
        FakeViewerRepository(),
        FakePreferences(),
        Dispatchers.Unconfined,
        Dispatchers.Unconfined,
        exporter,
    )

    private fun content(viewModel: ViewerViewModel): ViewerUiState.Content =
        viewModel.uiState.value as ViewerUiState.Content

    @Test
    fun exportRequestSurfacesTheSuccessPayload() = runBlocking {
        val expected = payload()
        val exporter = FakeExporter(ResultState.Success(expected))
        val viewModel = newViewModel(exporter)

        viewModel.onExportRequested(ExportScope.Trace("t_a"))

        val export = content(viewModel).export
        assertTrue(export is ResultState.Success)
        assertEquals(expected, (export as ResultState.Success).data)
        assertEquals(listOf(ExportScope.Trace("t_a")), exporter.requests)
    }

    @Test
    fun exportFailureSurfacesTheErrorAndRecoversOnTheNextRequest() = runBlocking {
        val exporter = FakeExporter(ResultState.Error(message = "export failed: disk full"))
        val viewModel = newViewModel(exporter)

        viewModel.onExportRequested(ExportScope.RecentHours(1))

        val failed = content(viewModel).export
        assertTrue(failed is ResultState.Error)
        assertEquals("export failed: disk full", (failed as ResultState.Error).message)

        exporter.result = ResultState.Success(payload())
        viewModel.onExportRequested(ExportScope.RecentHours(1))

        assertTrue("a later request can succeed again", content(viewModel).export is ResultState.Success)
    }

    @Test
    fun loadingStateIsObservableDuringTheExportAndReleasedAfter() = runBlocking {
        val exporter = FakeExporter(ResultState.Success(payload()))
        val viewModel = newViewModel(exporter)
        val duringCall = mutableListOf<ResultState<ExportSharePayload>?>()
        exporter.duringExport = {
            duringCall.add(content(viewModel).export)
        }

        viewModel.onExportRequested(ExportScope.CurrentFilter(EventFilter()))

        assertEquals("Loading is published before the IO work starts", 1, duringCall.size)
        assertTrue(duringCall.single() is ResultState.Loading)
        assertTrue("the guard is released after completion", content(viewModel).export is ResultState.Success)
    }

    @Test
    fun reentrantRequestsWhileAnExportIsInFlightAreIgnored() = runBlocking {
        val exporter = FakeExporter(ResultState.Success(payload()))
        val viewModel = newViewModel(exporter)
        var reentrantTriggers = 0
        exporter.duringExport = {
            reentrantTriggers++
            viewModel.onExportRequested(ExportScope.RecentHours(1))
        }

        viewModel.onExportRequested(ExportScope.Trace("t_a"))

        assertEquals(1, reentrantTriggers)
        assertEquals("only the original request reached the exporter", 1, exporter.requests.size)
    }

    @Test
    fun shareConsumedClearsASuccessButKeepsAFailureVisible() = runBlocking {
        val exporter = FakeExporter(ResultState.Success(payload()))
        val viewModel = newViewModel(exporter)

        viewModel.onExportRequested(ExportScope.Trace("t_a"))
        viewModel.onShareConsumed()

        assertNull("the ready payload is a one-shot event", content(viewModel).export)

        exporter.result = ResultState.Error(message = "export failed: x")
        viewModel.onExportRequested(ExportScope.Trace("t_a"))
        viewModel.onShareConsumed()

        assertTrue("a failure stays on screen until the next attempt", content(viewModel).export is ResultState.Error)
    }
}
