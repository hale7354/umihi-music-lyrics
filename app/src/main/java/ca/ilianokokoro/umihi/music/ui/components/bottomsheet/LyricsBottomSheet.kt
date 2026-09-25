package ca.ilianokokoro.umihi.music.ui.components.bottomsheet

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.palette.graphics.Palette
import ca.ilianokokoro.umihi.music.core.lyrics.LyricLine
import ca.ilianokokoro.umihi.music.ui.screens.player.PlaybackProgress
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.flow.StateFlow

private val defaultFlowColors = listOf(
    Color(0xFF6D5DF6),
    Color(0xFFB13BFF),
    Color(0xFF2B7FFF),
)

@Composable
fun LyricsBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    lyricsPlain: String?,
    lyricsLines: List<LyricLine>,
    notFound: Boolean,
    songTitle: String?,
    thumbnailUrl: String?,
    playbackProgress: StateFlow<PlaybackProgress>,
    onSeek: (Float) -> Unit,
) {
    val context = LocalContext.current
    val progress by playbackProgress.collectAsState()
    var flowColors by remember { mutableStateOf(defaultFlowColors) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl.isNullOrBlank()) {
            flowColors = defaultFlowColors
            return@LaunchedEffect
        }
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .allowHardware(false)
            .build()
        val result = runCatching { loader.execute(request) }.getOrNull()
        val bitmap: Bitmap? = ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap
        val extracted = bitmap?.let { bmp ->
            val palette = Palette.from(bmp).generate()
            listOfNotNull(
                palette.vibrantSwatch?.rgb,
                palette.darkVibrantSwatch?.rgb,
                palette.mutedSwatch?.rgb,
                palette.darkMutedSwatch?.rgb,
            ).map { Color(it) }
        }
        flowColors = if (!extracted.isNullOrEmpty()) extracted else defaultFlowColors
    }

    Dialog(
        onDismissRequest = { changeVisibility(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            FlowingBackground(colors = flowColors)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = { changeVisibility(false) }) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    if (!songTitle.isNullOrBlank()) {
                        Text(
                            text = songTitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 48.dp),
                        )
                    }
                }

                when {
                    isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                    notFound || (lyricsLines.isEmpty() && lyricsPlain.isNullOrBlank()) -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                text = "Kein Lyrics gefunden",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                            )
                        }
                    }
                    lyricsLines.isNotEmpty() -> SyncedLyrics(
                        lines = lyricsLines,
                        positionMs = progress.position.toLong(),
                        onLineClick = { onSeek(it.timeMs.toFloat()) },
                    )
                    else -> PlainLyrics(text = lyricsPlain.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun FlowingBackground(colors: List<Color>) {
    val transition = rememberInfiniteTransition(label = "lyrics_flow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flow_t",
    )

    val c1 = colors.getOrElse(0) { defaultFlowColors[0] }
    val c2 = colors.getOrElse(1) { defaultFlowColors[1] }
    val c3 = colors.getOrElse(2) { c1 }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(c1, Color.Transparent),
                    center = Offset(200f + t * 400f, 300f - t * 200f),
                    radius = 900f,
                )
            ).blur(90.dp)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(c2, Color.Transparent),
                    center = Offset(900f - t * 500f, 700f + t * 300f),
                    radius = 1000f,
                )
            ).blur(100.dp)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(c3, Color.Transparent),
                    center = Offset(500f - t * 300f, 1400f - t * 400f),
                    radius = 850f,
                )
            ).blur(90.dp)
        )
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    onLineClick: (LyricLine) -> Unit,
) {
    val currentIndex by remember(positionMs, lines) {
        derivedStateOf {
            var idx = -1
            for (i in lines.indices) {
                if (lines[i].timeMs <= positionMs) idx = i else break
            }
            idx
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(index = currentIndex, scrollOffset = -300)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 200.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                text = line.text.ifBlank { "···" },
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.4f),
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isCurrent) 24.sp else 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineClick(line) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
    ) {
        item {
            Text(text = text, color = Color.White, fontSize = 19.sp, lineHeight = 28.sp)
        }
    }
}
