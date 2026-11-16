package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** Indicateur vertical discret pour les LazyColumn. */
@Composable
fun LazyScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val total = state.layoutInfo.totalItemsCount
    if (total <= 0) return
    val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val fraction = (visible.toFloat() / total).coerceIn(0.08f, 0.5f)
    val progress = (state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1))
        .coerceIn(0f, 1f)
    ScrollbarThumb(progress, fraction, modifier)
}

/** Indicateur vertical pour les Column avec `verticalScroll`. */
@Composable
fun ColumnScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    val max = state.maxValue
    if (max <= 0) return
    val fraction = (state.viewportSize.toFloat() / (max + state.viewportSize)).coerceIn(0.08f, 0.5f)
    val progress = (state.value.toFloat() / max).coerceIn(0f, 1f)
    ScrollbarThumb(progress, fraction, modifier)
}

@Composable
private fun ScrollbarThumb(progress: Float, fraction: Float, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Canvas(modifier.fillMaxHeight().width(5.dp)) {
        val height = (size.height * fraction).coerceAtLeast(28.dp.toPx())
        val top = (size.height - height) * progress
        drawRoundRect(
            color = color,
            topLeft = Offset(1.dp.toPx(), top),
            size = Size(size.width - 2.dp.toPx(), height),
            cornerRadius = CornerRadius(size.width / 2),
        )
    }
}
