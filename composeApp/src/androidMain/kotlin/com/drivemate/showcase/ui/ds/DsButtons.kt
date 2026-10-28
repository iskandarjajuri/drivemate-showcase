package com.drivemate.showcase.ui.ds

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.drivemate.showcase.ui.AppMotion
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion

enum class DmButtonVariant { Primary, Secondary, Ghost, Destructive }
enum class DmButtonLoadingStyle { InlineWithText, SpinnerOnly }

@Composable
fun DmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingStyle: DmButtonLoadingStyle = DmButtonLoadingStyle.InlineWithText,
    animateLoading: Boolean = false,
    strongDisabled: Boolean = false,
    variant: DmButtonVariant = DmButtonVariant.Primary,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    val reduceMotion = LocalReduceMotion.current
    val shouldAnimate = animateLoading && !reduceMotion
    val motionFast = AppMotion.duration(reduceMotion, AppMotion.fast)
    val motionMedium = AppMotion.duration(reduceMotion, AppMotion.medium)
    val targetScale = if (loading && shouldAnimate) 0.98f else 1f
    val targetAlpha = if (loading && shouldAnimate) 0.95f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (shouldAnimate) {
            tween(
                durationMillis = if (loading) motionFast else motionMedium,
                easing = FastOutSlowInEasing
            )
        } else {
            snap()
        },
        label = "buttonScale"
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (shouldAnimate) {
            tween(
                durationMillis = if (loading) motionFast else motionMedium,
                easing = FastOutSlowInEasing
            )
        } else {
            snap()
        },
        label = "buttonAlpha"
    )

    val baseModifier = modifier
        .heightIn(min = AppSizing.primaryButtonHeight)
        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)

    val spinnerOnlyStyle = loadingStyle == DmButtonLoadingStyle.SpinnerOnly
    val content: @Composable RowScope.() -> Unit = {
        if (spinnerOnlyStyle) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (shouldAnimate) {
                    AnimatedContent(
                        targetState = loading,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(motionMedium)).togetherWith(
                                fadeOut(animationSpec = tween(motionFast))
                            )
                        },
                        label = "buttonSpinnerSwap"
                    ) { isLoading ->
                        if (isLoading) {
                            DriveMateLoadingIndicator(
                                size = AppSizing.iconMd,
                                color = when (variant) {
                                    DmButtonVariant.Primary, DmButtonVariant.Destructive -> AppPalette.onPrimary
                                    DmButtonVariant.Secondary, DmButtonVariant.Ghost -> AppPalette.primary
                                },
                                strokeWidth = AppSpacing.xxs / 1.5f
                            )
                        } else {
                            Text(
                                text = text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (loading) {
                    DriveMateLoadingIndicator(
                        size = AppSizing.iconMd,
                        color = when (variant) {
                            DmButtonVariant.Primary, DmButtonVariant.Destructive -> AppPalette.onPrimary
                            DmButtonVariant.Secondary, DmButtonVariant.Ghost -> AppPalette.primary
                        },
                        strokeWidth = AppSpacing.xxs / 1.5f
                    )
                } else {
                    Text(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            if (loading) {
                DriveMateLoadingIndicator(
                    modifier = Modifier.size(AppSizing.iconSm),
                    color = when (variant) {
                        DmButtonVariant.Primary, DmButtonVariant.Destructive -> AppPalette.onPrimary
                        DmButtonVariant.Secondary, DmButtonVariant.Ghost -> AppPalette.primary
                    },
                    strokeWidth = AppSpacing.xxs / 2
                )
                Spacer(Modifier.width(AppSpacing.sm))
            }
            if (leading != null) {
                leading()
                Spacer(Modifier.width(AppSpacing.xs))
            }
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    val primaryDisabledContainer = if (strongDisabled) {
        AppPalette.primary.copy(alpha = 0.68f)
    } else {
        AppPalette.disabledContainer
    }
    val primaryDisabledContent = if (strongDisabled) {
        AppPalette.onPrimary.copy(alpha = 0.9f)
    } else {
        AppPalette.onDisabled
    }

    when (variant) {
        DmButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = enabled && !loading,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(AppShapes.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppPalette.primary,
                contentColor = AppPalette.onPrimary,
                disabledContainerColor = primaryDisabledContainer,
                disabledContentColor = primaryDisabledContent
            ),
            contentPadding = PaddingValues(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
            modifier = baseModifier
        ) { content() }

        DmButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(AppShapes.button),
            border = BorderStroke(width = AppSpacing.xxs / 4, color = AppPalette.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AppPalette.onSurface,
                disabledContentColor = AppPalette.onDisabled
            ),
            contentPadding = PaddingValues(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
            modifier = baseModifier
        ) { content() }

        DmButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            enabled = enabled && !loading,
            colors = ButtonDefaults.textButtonColors(
                contentColor = AppPalette.primaryHover,
                disabledContentColor = AppPalette.onDisabled
            ),
            contentPadding = PaddingValues(horizontal = AppSpacing.xs, vertical = AppSpacing.xs),
            modifier = modifier.heightIn(min = AppSizing.touchTarget)
        ) { content() }

        DmButtonVariant.Destructive -> Button(
            onClick = onClick,
            enabled = enabled && !loading,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(AppShapes.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppPalette.error,
                contentColor = AppPalette.onError,
                disabledContainerColor = AppPalette.disabledContainer,
                disabledContentColor = AppPalette.onDisabled
            ),
            contentPadding = PaddingValues(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
            modifier = baseModifier
        ) { content() }
    }
}
