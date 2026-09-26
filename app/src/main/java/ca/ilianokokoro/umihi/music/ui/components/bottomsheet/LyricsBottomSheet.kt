package ca.ilianokokoro.umihi.music.ui.components.bottomsheet

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.lyrics.LyricsLine
import ca.ilianokokoro.umihi.music.ui.components.SheetHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    lyricsPlain: String?,
    syncedLines: List<LyricsLine>,
    positionMs: Long,
    notFound: Boolean,
    instrumental: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = { changeVisibility(false) },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(
                icon = Icons.Rounded.Subtitles,
                title = stringResource(R.string.lyrics),
            )

            when {
                isLoading -> LyricsCentered { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }

                instrumental -> LyricsCentered {
                    Text(
                        text = stringResource(R.string.lyrics_instrumental),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                notFound || (lyricsPlain.isNullOrBlank() && syncedLines.isEmpty()) -> LyricsCentered {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                syncedLines.isNotEmpty() -> SyncedLyrics(lines = syncedLines, positionMs = positionMs)

                else -> {
                    Text(
                        text = lyricsPlain.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsCentered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/**
 * Scrollable, line-synced lyrics view: the line matching the current playback position is
 * highlighted, and the list auto-scrolls to keep it a couple lines below the top.
 */
@Composable
private fun SyncedLyrics(
    lines: List<LyricsLine>,
    positionMs: Long,
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timestampMs <= positionMs }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.height(360.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index-${line.timestampMs}" }) { index, line ->
            LyricsLineRow(text = line.text, isActive = index == currentIndex)
        }
    }
}

@Composable
private fun LyricsLineRow(text: String, isActive: Boolean) {
    val color by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        label = "lyricsLineColor",
    )
    Text(
        text = text,
        style = if (isActive) {
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        color = color,
    )
}
