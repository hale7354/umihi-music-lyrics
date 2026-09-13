package ca.ilianokokoro.umihi.music.ui.components.bottomsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.SheetHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    lyrics: String?,
    notFound: Boolean,
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
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(
                icon = Icons.Rounded.Subtitles,
                title = stringResource(R.string.lyrics),
            )

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
                notFound || lyrics.isNullOrBlank() -> {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        text = lyrics,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
