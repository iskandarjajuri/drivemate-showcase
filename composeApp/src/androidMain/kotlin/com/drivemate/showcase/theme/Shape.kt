package com.drivemate.showcase.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import com.drivemate.showcase.ui.AppShapes

val DriveMateShapes = Shapes(
    extraSmall = RoundedCornerShape(AppShapes.field),
    small = RoundedCornerShape(AppShapes.field),
    medium = RoundedCornerShape(AppShapes.button),
    large = RoundedCornerShape(AppShapes.screenCard),
    extraLarge = RoundedCornerShape(AppShapes.screenCard)
)
