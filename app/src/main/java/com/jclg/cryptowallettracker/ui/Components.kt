package com.jclg.cryptowallettracker.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jclg.cryptowallettracker.R
import com.jclg.cryptowallettracker.data.Coin
import com.jclg.cryptowallettracker.data.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

/** Logos récupérés depuis CoinGecko, conservés en mémoire jusqu'à un rafraîchissement manuel. */
object LogoLoader {
    private val client = OkHttpClient()
    private val cache = HashMap<String, ImageBitmap>()
    private val failed = HashSet<String>()
    private val versionState = MutableStateFlow(0)
    val version: StateFlow<Int> = versionState.asStateFlow()

    /** Force le rechargement de tous les logos affichés. */
    fun clear() {
        cache.clear()
        failed.clear()
        versionState.update { it + 1 }
    }

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache[url] ?: run {
            if (url in failed) return@run null
            val bitmap = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) null
                    else BitmapFactory.decodeStream(response.body?.byteStream())?.asImageBitmap()
                }
            }.getOrNull()
            if (bitmap == null) failed.add(url) else cache[url] = bitmap
            bitmap
        }
    }
}

@Composable
fun CoinLogo(coin: Coin, imageUrl: String?, size: Dp = 38.dp, modifier: Modifier = Modifier) {
    val version by LogoLoader.version.collectAsState()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, coin.id, imageUrl, version) {
        if (imageUrl != null) value = LogoLoader.load(imageUrl)
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = coin.name,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(Color(coin.color)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                coin.symbol.take(2),
                color = Color.White,
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun AppLogo(size: Dp = 40.dp) {
    Image(
        painter = painterResource(R.drawable.ic_launcher),
        contentDescription = "Crypto Wallet Tracker",
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)),
    )
}

/** Courbe interactive : tap ou glissement pour lire un point. */
@Composable
fun PriceChart(
    points: List<Point>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Chargement…", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier
            .fillMaxWidth()
            .pointerInput(points) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun pick(x: Float) = ((x / size.width) * (points.size - 1))
                        .roundToInt().coerceIn(0, points.size - 1)

                    onSelectIndex(pick(down.position.x))
                    drag(down.id) { change ->
                        onSelectIndex(pick(change.position.x))
                        change.consume()
                    }
                }
            },
    ) {
        val stepX = size.width / (points.size - 1)
        fun y(value: Double): Float {
            val normalized = ((value - min) / range).toFloat()
            return size.height - (normalized * size.height * 0.86f + size.height * 0.07f)
        }

        val area = Path().apply {
            moveTo(0f, size.height)
            points.forEachIndexed { i, point -> lineTo(i * stepX, y(point.value)) }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)),
        )

        val line = Path()
        points.forEachIndexed { i, point ->
            if (i == 0) line.moveTo(i * stepX, y(point.value)) else line.lineTo(i * stepX, y(point.value))
        }
        drawPath(line, lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        selectedIndex?.let { index ->
            if (index in points.indices) {
                val x = index * stepX
                drawLine(
                    color = lineColor.copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2f,
                )
                drawCircle(lineColor, radius = 6f, center = Offset(x, y(points[index].value)))
            }
        }
    }
}

@Composable
fun DottedDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .size(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    )
}
