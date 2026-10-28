package com.drivemate.showcase.ui.ds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSpacing
import kotlin.math.max

data class BarPoint(val label: String, val value: Float)
data class LinePoint(val xLabel: String, val value: Float)
data class GroupedBarSeries(val label: String, val color: Color)
data class GroupedBarGroup(val label: String, val values: List<Float>)

@Composable
fun BarChartCard(
    title: String,
    points: List<BarPoint>,
    modifier: Modifier = Modifier,
    height: Int = 160
) {
    DmCard(modifier = modifier, variant = DmCardVariant.Window) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (points.isEmpty()) {
            TableEmptyState(
                title = stringResource(R.string.ds_chart_empty_title),
                message = stringResource(R.string.ds_chart_empty_message)
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp)
                    .padding(top = AppSpacing.sm)
            ) {
                val maxVal = max(1f, points.maxOf { it.value })
                val barCount = points.size
                val gap = size.width * 0.06f / max(1, barCount - 1)
                val barWidth = (size.width - gap * (barCount - 1)) / barCount

                val axisY = size.height
                drawLine(
                    color = AppPalette.outline,
                    start = Offset(0f, axisY),
                    end = Offset(size.width, axisY),
                    strokeWidth = 2f
                )

                points.forEachIndexed { index, point ->
                    val x = index * (barWidth + gap)
                    val h = (point.value / maxVal) * (size.height * 0.85f)
                    drawRect(
                        color = AppPalette.primary,
                        topLeft = Offset(x, axisY - h),
                        size = Size(barWidth, h)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val first = points.firstOrNull()?.label.orEmpty()
                val last = points.lastOrNull()?.label.orEmpty()
                Text(first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(last, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LineChartCard(
    title: String,
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    height: Int = 160
) {
    DmCard(modifier = modifier, variant = DmCardVariant.Window) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (points.isEmpty()) {
            TableEmptyState(
                title = stringResource(R.string.ds_chart_empty_title),
                message = stringResource(R.string.ds_chart_empty_message)
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp)
                    .padding(top = AppSpacing.sm)
            ) {
                val maxVal = max(1f, points.maxOf { it.value })
                val minVal = points.minOf { it.value }
                val range = max(1f, maxVal - minVal)

                val axisY = size.height
                drawLine(
                    color = AppPalette.outline,
                    start = Offset(0f, axisY),
                    end = Offset(size.width, axisY),
                    strokeWidth = 2f
                )

                val stepX = if (points.size <= 1) size.width else size.width / (points.size - 1)
                var prev: Offset? = null
                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = axisY - ((point.value - minVal) / range) * (size.height * 0.85f)
                    val cur = Offset(x, y)
                    prev?.let {
                        drawLine(
                            color = AppPalette.primary,
                            start = it,
                            end = cur,
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(
                        color = AppPalette.primaryHover,
                        radius = 6f,
                        center = cur
                    )
                    prev = cur
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val first = points.firstOrNull()?.xLabel.orEmpty()
                val last = points.lastOrNull()?.xLabel.orEmpty()
                Text(first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(last, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GroupedBarChartCard(
    title: String,
    groups: List<GroupedBarGroup>,
    series: List<GroupedBarSeries>,
    modifier: Modifier = Modifier,
    height: Int = 180,
    subtitle: String? = null,
    heroContent: (@Composable () -> Unit)? = null,
    yAxisTickLabels: List<String> = emptyList(),
    /** Nilai garis teratas sumbu-y; bila diberikan, batang diskalakan penuh terhadapnya. */
    yAxisMax: Float? = null,
    legendContentDescription: String? = null,
    insight: String? = null,
    /** null = tebak dari nilai (semua nol = kosong); non-null = keputusan pemanggil: nol terukur BUKAN kosong. */
    hasData: Boolean? = null,
    /** Indeks grup tanpa data: digambar berpola arsir dan diberi [noDataLabel], bukan batang setinggi nol. */
    noDataGroups: Set<Int> = emptySet(),
    noDataLabel: String? = null
) {
    val isEmpty = groups.isEmpty() ||
        series.isEmpty() ||
        (hasData?.not() ?: groups.all { group -> group.values.all { it <= 0f } })

    DmCard(modifier = modifier, variant = DmCardVariant.Window) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        heroContent?.invoke()
        if (isEmpty) {
            TableEmptyState(
                title = stringResource(R.string.ds_chart_empty_title),
                message = stringResource(R.string.ds_chart_empty_message)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.Bottom
            ) {
                if (yAxisTickLabels.isNotEmpty()) {
                    YAxisTickColumn(
                        labels = yAxisTickLabels,
                        height = height,
                        // Sejajar dengan kanvas (atas), bukan dengan dasar Row yang juga memuat label
                        // kategori di bawah kanvas; kalau tidak, angka sumbu bergeser ke bawah garis.
                        modifier = Modifier.align(Alignment.Top)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height.dp)
                            .padding(top = AppSpacing.sm)
                    ) {
                        val maxVal = yAxisMax?.takeIf { it > 0f } ?: max(1f, groups.maxOf { group -> group.values.maxOrNull() ?: 0f })
                        // Dengan sumbu-y yang tampil, 100% dari skala = garis teratas (bukan 85%).
                        val plotFraction = if (yAxisMax != null) 1f else 0.85f
                        val groupCount = groups.size
                        val seriesCount = max(1, series.size)
                        val interGroupGap = AppSpacing.md.toPx()
                        val intraGroupGap = AppSpacing.xs.toPx() / 2f
                        val totalGroupGaps = interGroupGap * max(0, groupCount - 1)
                        val groupWidth = if (groupCount == 0) {
                            0f
                        } else {
                            (size.width - totalGroupGaps) / groupCount.toFloat()
                        }
                        val barWidth = if (seriesCount == 1) {
                            groupWidth
                        } else {
                            (groupWidth - (intraGroupGap * (seriesCount - 1))) / seriesCount.toFloat()
                        }.coerceAtLeast(0f)

                        val axisY = size.height
                        if (yAxisTickLabels.size >= 2) {
                            val tickSteps = yAxisTickLabels.size - 1
                            repeat(yAxisTickLabels.size) { index ->
                                val ratio = index.toFloat() / tickSteps.toFloat()
                                val y = size.height * ratio
                                drawLine(
                                    color = AppPalette.outline.copy(alpha = 0.35f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        drawLine(
                            color = AppPalette.outline,
                            start = Offset(0f, axisY),
                            end = Offset(size.width, axisY),
                            strokeWidth = 2f
                        )

                        groups.forEachIndexed { groupIndex, group ->
                            val groupX = groupIndex * (groupWidth + interGroupGap)
                            if (groupIndex in noDataGroups) {
                                drawNoDataHatch(
                                    left = groupX,
                                    width = groupWidth,
                                    bottom = axisY,
                                    height = size.height * 0.5f,
                                    color = AppPalette.outline
                                )
                                return@forEachIndexed
                            }
                            group.values.forEachIndexed { seriesIndex, value ->
                                val seriesColor = series.getOrNull(seriesIndex)?.color ?: AppPalette.primary
                                val barHeight = (value / maxVal) * (size.height * plotFraction)
                                val x = groupX + (seriesIndex * (barWidth + intraGroupGap))
                                drawRect(
                                    color = seriesColor,
                                    topLeft = Offset(x, axisY - barHeight),
                                    size = Size(barWidth, barHeight)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        groups.forEachIndexed { groupIndex, group ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (groupIndex in noDataGroups && noDataLabel != null) {
                                    Text(
                                        text = noDataLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .then(
                        if (legendContentDescription != null) {
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = legendContentDescription
                            }
                        } else {
                            Modifier
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                series.forEach { item ->
                    Surface(
                        color = item.color.copy(alpha = 0.14f),
                        contentColor = item.color,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawRect(color = item.color, size = size)
                            }
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = item.color
                            )
                        }
                    }
                }
            }
            if (!insight.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun YAxisTickColumn(
    labels: List<String>,
    height: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(40.dp)
            .height(height.dp)
            .padding(top = AppSpacing.sm),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}

/** Arsir diagonal berbingkai: "tidak ada data", jelas berbeda dari batang bernilai nol. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoDataHatch(
    left: Float,
    width: Float,
    bottom: Float,
    height: Float,
    color: androidx.compose.ui.graphics.Color
) {
    if (width <= 0f || height <= 0f) return
    val top = bottom - height
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )
    val step = 6.dp.toPx()
    clipRect(left, top, left + width, bottom) {
        var x = left - height
        while (x < left + width) {
            drawLine(
                color = color.copy(alpha = 0.7f),
                start = Offset(x, bottom),
                end = Offset(x + height, top),
                strokeWidth = 1.dp.toPx()
            )
            x += step
        }
    }
}
