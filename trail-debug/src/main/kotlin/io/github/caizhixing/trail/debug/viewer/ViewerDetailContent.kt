package io.github.caizhixing.trail.debug.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.caizhixing.trail.model.LogEvent

/**
 * Stateless detail pane (behavior 5.5): shows the stored values verbatim —
 * the full `attrs` map with no display truncation (sanitization and the
 * 512-char cap already happened at capture time and are preserved as-is).
 */
@Composable
internal fun ViewerDetailContent(
    event: LogEvent,
    onBack: () -> Unit,
    onTraceIdSelected: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text(text = "back to list") }
            event.traceId?.let { traceId ->
                TextButton(onClick = { onTraceIdSelected(traceId) }) {
                    Text(text = "filter this trace")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                DetailLine(label = "time", value = event.ts.toString())
                DetailLine(label = "level", value = event.level.name)
                DetailLine(label = "tag", value = event.tag)
                DetailLine(label = "name", value = event.name)
                DetailLine(label = "audience", value = event.audience.name)
                DetailLine(label = "traceId", value = event.traceId ?: "null")
                DetailLine(label = "thread", value = event.tid ?: "null")
            }
        }
        Text(text = "attrs (${event.attrs.size})", modifier = Modifier.padding(top = 12.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (event.attrs.isEmpty()) {
                    Text(text = "empty")
                } else {
                    event.attrs.forEach { (key, value) ->
                        DetailLine(label = key, value = value)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
        )
    }
}
