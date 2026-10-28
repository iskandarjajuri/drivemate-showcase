package com.drivemate.showcase.ui.patterns

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.drivemate.showcase.ui.dsv2.Dsv2BottomBar
import com.drivemate.showcase.ui.dsv2.Dsv2BottomNavItem

@Composable
fun DmBottomNavHost(
    items: List<Dsv2BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (Dsv2BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Dsv2BottomBar(
        items = items,
        currentRoute = currentRoute,
        onItemSelected = onItemSelected,
        modifier = modifier
    )
}
