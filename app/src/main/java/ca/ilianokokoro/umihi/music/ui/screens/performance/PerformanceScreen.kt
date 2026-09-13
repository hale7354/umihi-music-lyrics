package ca.ilianokokoro.umihi.music.ui.screens.performance

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.performance.MetricStatus
import ca.ilianokokoro.umihi.music.core.performance.PerformanceMetric
import ca.ilianokokoro.umihi.music.core.performance.PerformanceMetrics

@Composable
fun PerformanceScreen(
    onBack: () -> Unit,
    application: Application,
) {
    val context = LocalContext.current
    var metrics by remember { mutableStateOf(PerformanceMetrics.collect(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null
                    )
                }
                Text(
                    text = "Performance-Check",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            IconButton(onClick = { metrics = PerformanceMetrics.collect(context) }) {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Aktualisieren")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            metrics.forEach { metric ->
                PerformanceMetricRow(metric)
            }
        }
    }
}

@Composable
private fun PerformanceMetricRow(metric: PerformanceMetric) {
    val color = when (metric.status) {
        MetricStatus.GOOD -> MaterialTheme.colorScheme.primary
        MetricStatus.WARNING -> androidx.compose.ui.graphics.Color(0xFFE6A700)
        MetricStatus.CRITICAL -> MaterialTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 12.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = metric.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = metric.valueText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = color
                )
                Text(
                    text = metric.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
