package com.drivemate.showcase.ui.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing

data class TableColumn(
    val title: String,
    val weight: Float,
    val alignEnd: Boolean = false
)

data class TableRowModel(
    val key: String,
    val values: List<String>,
    val onClick: (() -> Unit)? = null
)

@Composable
fun TableContainer(
    title: String,
    columns: List<TableColumn>,
    modifier: Modifier = Modifier,
    emptyTitle: String? = null,
    emptyMessage: String? = null,
    rows: List<TableRowModel>,
    useLazyColumn: Boolean = true
) {
    val resolvedEmptyTitle = emptyTitle ?: stringResource(R.string.ds_table_empty_title)
    val resolvedEmptyMessage = emptyMessage ?: stringResource(R.string.ds_table_empty_message)
    DmCard(modifier = modifier, variant = DmCardVariant.Window) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            TableEmptyState(title = resolvedEmptyTitle, message = resolvedEmptyMessage)
        } else {
            TableHeaderRow(columns = columns)
            HorizontalDivider(color = AppPalette.outline)
            if (useLazyColumn) {
                LazyColumn {
                    items(rows, key = { it.key }) { row ->
                        TableRow(
                            columns = columns,
                            values = row.values,
                            onClick = row.onClick
                        )
                        HorizontalDivider(color = AppPalette.outlineVariant)
                    }
                }
            } else {
                Column {
                    rows.forEach { row ->
                        TableRow(
                            columns = columns,
                            values = row.values,
                            onClick = row.onClick
                        )
                        HorizontalDivider(color = AppPalette.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun TableContainerSimple(
    title: String,
    columns: List<TableColumn>,
    modifier: Modifier = Modifier,
    emptyTitle: String? = null,
    emptyMessage: String? = null,
    rows: List<List<String>>,
    useLazyColumn: Boolean = true
) {
    TableContainer(
        title = title,
        columns = columns,
        modifier = modifier,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        rows = rows.mapIndexed { index, values ->
            TableRowModel(key = "row_$index", values = values)
        },
        useLazyColumn = useLazyColumn
    )
}

@Composable
fun TableHeaderRow(
    columns: List<TableColumn>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppPalette.surfaceVariant)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEach { col ->
            val align = if (col.alignEnd) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start
            Text(
                text = col.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Header boleh turun baris; potongan "Menun…" tidak terbaca sebagai apa pun.
                maxLines = 2,
                textAlign = align,
                modifier = Modifier.weight(col.weight)
            )
        }
    }
}

@Composable
fun TableRow(
    columns: List<TableColumn>,
    values: List<String>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppSizing.primaryButtonHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEachIndexed { index, col ->
            val text = values.getOrNull(index).orEmpty()
            val align = if (col.alignEnd) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
                modifier = Modifier.weight(col.weight)
            )
        }
    }
}

@Composable
fun TableEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
