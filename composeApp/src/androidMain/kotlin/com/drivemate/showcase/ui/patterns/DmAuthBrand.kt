package com.drivemate.showcase.ui.patterns

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared brand identity for the two pre-auth screens (Splash + Login).
 *
 * Splash and Login are one visual system: same gradient, same "Neural Wheel"
 * mark, same two-tone wordmark — the login mark is only smaller. Keeping all of
 * it here is what stops the two screens drifting apart the way the old
 * car-photo splash and the old header-band login did.
 */

/** Nodes around the hub, 60 degrees apart. */
private const val NEURAL_WHEEL_NODE_COUNT = 6

/** One full node pulse cycle (opacity + radius), per spec. */
private const val NEURAL_WHEEL_PULSE_MILLIS = 2400

/** Stagger between adjacent nodes, so the pulse travels around the wheel. */
private const val NEURAL_WHEEL_PULSE_STAGGER_MILLIS = 300

/** One full rotation of the whole mark. */
private const val NEURAL_WHEEL_ROTATION_MILLIS = 14000

/** Shine sweep across the loading bar fill. */
private const val LOADING_SHINE_MILLIS = 1700

/** One full "breath" of the Prism Orb hub (0.92 -> 1.16 -> 0.92). */
private const val PRISM_ORB_BREATH_MILLIS = 2400
private const val PRISM_ORB_BREATH_MIN = 0.92f
private const val PRISM_ORB_BREATH_MAX = 1.16f

/**
 * One sweep of the indeterminate loading fill.
 *
 * Deliberately different from [LOADING_SHINE_MILLIS] so the two travelling
 * elements do not lock into a single visual beat.
 */
private const val LOADING_SWEEP_MILLIS = 1150

/** Width of the indeterminate fill band, as a fraction of the track. */
private const val LOADING_SWEEP_WIDTH_FRACTION = 0.45f

@Immutable
data class DmAuthBrandPalette(
    val gradientStart: Color,
    val gradientEnd: Color,
    val nodeColors: List<Color>,
    val spokeColor: Color,
    val wordmarkPrimary: Color,
    val wordmarkAccent: Color,
    val wordmarkGlowRadius: Dp,
    val wordmarkGlowAlpha: Float,
    val loadingTrack: Color,
    val loadingTrackInset: Color,
    val loadingFill: List<Color>,
    val loadingGlow: Color,
    val cardContainer: Color,
    val cardBorder: Color
)

/**
 * The 6 node hues are identical in both modes on purpose — the spec wants the
 * glow to read more vivid against the dark background on its own, not via
 * different colors.
 */
private val NeuralWheelNodeColors = listOf(
    Color(0xFF60A5FA),
    Color(0xFF22D3EE),
    Color(0xFF818CF8),
    Color(0xFF38BDF8),
    Color(0xFF2DD4BF),
    Color(0xFFA78BFA)
)

/**
 * "Prism Orb" hub stops, centre -> edge. Identical in light and dark on purpose:
 * the approved concept reads well against both the light `#2563EB` gradient and
 * the dark `#1E3A8A` one, so unlike the rest of the palette this needs no
 * per-scheme branching. The final stop fades to fully transparent, which is what
 * gives the orb its soft edge — no extra blur or shadow is stacked on top.
 */
private val PrismOrbStops = arrayOf(
    0.00f to Color(0xFFFFFFFF),
    0.35f to Color(0xFF93C5FD),
    0.70f to Color(0xFF22D3EE),
    1.00f to Color(0x00A78BFA)
)

private val LoadingFillColors = listOf(
    Color(0xFF38BDF8),
    Color(0xFF818CF8),
    Color(0xFF22D3EE)
)

/**
 * Whether the *applied* theme is dark — derived from the resolved color scheme,
 * not from `isSystemInDarkTheme()`.
 *
 * This matters: `MainActivity` has `AUTO_TIME_BASED_DARK_MODE`, which forces the
 * dark scheme between 19:00 and 05:59 regardless of the system setting. Asking
 * the system directly would report "light" at 20:00 with the system toggle off,
 * and these two screens would then paint a light card on a dark app.
 */
@Composable
fun rememberDmAuthIsDark(): Boolean {
    val background = MaterialTheme.colorScheme.background
    return remember(background) { background.luminance() < 0.45f }
}

@Composable
fun rememberDmAuthBrandPalette(isDark: Boolean): DmAuthBrandPalette = remember(isDark) {
    if (isDark) {
        DmAuthBrandPalette(
            gradientStart = Color(0xFF1E3A8A),
            gradientEnd = Color(0xFF0B1220),
            nodeColors = NeuralWheelNodeColors,
            spokeColor = Color.White.copy(alpha = 0.20f),
            wordmarkPrimary = Color(0xFFF1F5F9),
            wordmarkAccent = Color(0xFF22D3EE),
            wordmarkGlowRadius = 14.dp,
            wordmarkGlowAlpha = 0.70f,
            loadingTrack = Color.White.copy(alpha = 0.06f),
            loadingTrackInset = Color.Black.copy(alpha = 0.28f),
            loadingFill = LoadingFillColors,
            loadingGlow = Color(0xFF38BDF8),
            cardContainer = Color.White.copy(alpha = 0.07f),
            cardBorder = Color.White.copy(alpha = 0.14f)
        )
    } else {
        DmAuthBrandPalette(
            gradientStart = Color(0xFF2563EB),
            gradientEnd = Color(0xFF173E9E),
            nodeColors = NeuralWheelNodeColors,
            spokeColor = Color.White.copy(alpha = 0.35f),
            wordmarkPrimary = Color(0xFFFFFFFF),
            wordmarkAccent = Color(0xFF7DD3FC),
            wordmarkGlowRadius = 12.dp,
            wordmarkGlowAlpha = 0.65f,
            loadingTrack = Color.White.copy(alpha = 0.16f),
            loadingTrackInset = Color.Black.copy(alpha = 0.22f),
            loadingFill = LoadingFillColors,
            loadingGlow = Color(0xFF38BDF8),
            cardContainer = Color.White.copy(alpha = 0.90f),
            cardBorder = Color.White.copy(alpha = 0.55f)
        )
    }
}

/**
 * CSS `linear-gradient(160deg, start -> end)`.
 *
 * CSS measures the angle clockwise from "to top", so the direction vector in
 * screen space (y down) is `(sin a, -cos a)`, and the gradient line length is
 * the CSS formula `|w*dx| + |h*dy|`. Doing it properly here rather than
 * approximating with a vertical gradient keeps the diagonal the design asked
 * for at every aspect ratio (phone portrait vs landscape vs tablet).
 */
fun Modifier.dmAuthGradientBackground(palette: DmAuthBrandPalette): Modifier = drawBehind {
    val radians = Math.toRadians(160.0)
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val lineLength = abs(size.width * dx) + abs(size.height * dy)
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val start = Offset(centerX - dx * lineLength / 2f, centerY - dy * lineLength / 2f)
    val end = Offset(centerX + dx * lineLength / 2f, centerY + dy * lineLength / 2f)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(palette.gradientStart, palette.gradientEnd),
            start = start,
            end = end
        )
    )
}

/**
 * Hub + 6 glowing nodes on spokes, pulsing in a staggered sequence while the
 * whole mark rotates.
 *
 * The node glow is a radial-gradient halo drawn under each solid dot rather
 * than `Modifier.blur()`: blur is a RenderEffect that only exists on API 31+
 * and silently no-ops below it, and this app ships `minSdk = 26`. A radial
 * gradient gives the same soft halo on every supported device instead of
 * quietly degrading to hard-edged dots on older ones.
 */
@Composable
fun DmNeuralWheel(
    palette: DmAuthBrandPalette,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    reduceMotion: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "neuralWheel")

    val rotation = if (reduceMotion) {
        0f
    } else {
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(NEURAL_WHEEL_ROTATION_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "neuralWheelRotation"
        )
        animated
    }

    // 0f = trough (dim/small), 1f = crest (bright/large). Node count is a
    // compile-time constant, so the composable call count is stable.
    val pulses: List<Float> = List(NEURAL_WHEEL_NODE_COUNT) { index ->
        if (reduceMotion) {
            0.5f
        } else {
            val animated by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(NEURAL_WHEEL_PULSE_MILLIS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(NEURAL_WHEEL_PULSE_STAGGER_MILLIS * index)
                ),
                label = "neuralWheelPulse$index"
            )
            animated
        }
    }

    // Breathing scale for the hub ONLY — the node ring keeps its own rotation and
    // pulse. Applied arithmetically to the radius rather than as a
    // `Modifier.graphicsLayer` scale, because the hub is a `drawCircle` inside a
    // Canvas that already carries the wheel's `rotationZ`: a modifier-level scale
    // there would scale the whole mark, and splitting the hub into its own
    // composable just to host a transform would buy nothing a multiply doesn't.
    val breath = if (reduceMotion) {
        1f
    } else {
        val animated by transition.animateFloat(
            initialValue = PRISM_ORB_BREATH_MIN,
            targetValue = PRISM_ORB_BREATH_MAX,
            animationSpec = infiniteRepeatable(
                animation = tween(PRISM_ORB_BREATH_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "prismOrbBreath"
        )
        animated
    }

    val markDescription = stringResource(R.string.dm_brand_mark_description)

    Canvas(
        modifier = modifier
            .size(diameter)
            .graphicsLayer { rotationZ = rotation }
            .semantics { contentDescription = markDescription }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // Nodes sit on a ring inset from the edge so their halos stay inside
        // the composable's own bounds instead of being clipped.
        val ringRadius = size.minDimension * 0.34f
        val hubRadius = size.minDimension * 0.10f
        val nodeBaseRadius = size.minDimension * 0.038f
        val nodePeakRadius = size.minDimension * 0.051f
        val spokeWidth = size.minDimension * 0.011f

        repeat(NEURAL_WHEEL_NODE_COUNT) { index ->
            val angle = Math.toRadians((index * 360.0 / NEURAL_WHEEL_NODE_COUNT) - 90.0)
            val nodeCenter = Offset(
                x = center.x + (ringRadius * cos(angle)).toFloat(),
                y = center.y + (ringRadius * sin(angle)).toFloat()
            )
            val pulse = pulses[index]
            val nodeColor = palette.nodeColors[index % palette.nodeColors.size]
            val nodeRadius = nodeBaseRadius + (nodePeakRadius - nodeBaseRadius) * pulse
            val nodeAlpha = 0.6f + 0.4f * pulse

            // Spoke first, so the node and its halo sit on top of the line end.
            drawLine(
                color = palette.spokeColor,
                start = center,
                end = nodeCenter,
                strokeWidth = spokeWidth
            )

            val glowRadius = nodeRadius * 3.2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        nodeColor.copy(alpha = 0.55f * nodeAlpha),
                        nodeColor.copy(alpha = 0.22f * nodeAlpha),
                        Color.Transparent
                    ),
                    center = nodeCenter,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = nodeCenter
            )
            drawCircle(
                color = nodeColor.copy(alpha = nodeAlpha),
                radius = nodeRadius,
                center = nodeCenter
            )
        }

        // "Prism Orb" hub: a radial gradient blend, not a flat disc.
        //
        // The gradient's radius tracks the breathing radius so the
        // fade-to-transparent stop always lands exactly on the orb's own edge —
        // pinning it to the unbreathed radius would leave a hard rim at 1.16 and
        // an early fade at 0.92.
        //
        // The centre sits up-left of true centre for a "light source" feel. The
        // spec gives that as 42%/38% of the circle's BOUNDING BOX, and the box is
        // 2r wide, so the offset from centre is (0.42-0.5)*2r = -0.16r and
        // (0.38-0.5)*2r = -0.24r. Halving those by measuring against r instead of
        // 2r is the easy way to get a subtly wrong-looking orb.
        val orbRadius = hubRadius * breath
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = PrismOrbStops,
                center = Offset(
                    x = center.x - 0.16f * orbRadius,
                    y = center.y - 0.24f * orbRadius
                ),
                radius = orbRadius
            ),
            // The drawn circle is only a mask, and it is deliberately LARGER than
            // the gradient so the gradient's own fade is what forms the edge.
            //
            // Sizing the mask to orbRadius instead cuts the orb: with the centre
            // offset by hypot(0.16, 0.24) = 0.29 * orbRadius, the boundary sits
            // between 0.71 and 1.29 * orbRadius away from that centre, so on the
            // near side the gradient is still solid #22D3EE when the hard edge
            // arrives — a cyan crescent along the upper-left rim. Measured on a
            // screencap before this: the upward radial profile ramped to
            // rgb(42,210,239) and then jumped 128 straight to the background in
            // one 4px step, while every other direction faded smoothly.
            //
            // 1.35 clears the far side (1.29) with a small margin; everything
            // past the gradient's last stop is transparent, so the visible orb is
            // still exactly orbRadius across.
            radius = orbRadius * 1.35f,
            center = center
        )
    }
}

/**
 * "Drive" + "Mate" in one text node, two colors, with the accent half glowing.
 *
 * Uses a `Shadow` on the accent `SpanStyle` — that is the only way to get a
 * per-span glow in Compose text; a `Modifier`-level shadow would bleed onto
 * both halves and lose the two-tone effect the brand depends on.
 */
@Composable
fun DmBrandWordmark(
    palette: DmAuthBrandPalette,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall
) {
    val appName = stringResource(R.string.app_name)
    val wordmarkPrimary = stringResource(R.string.app_wordmark_primary)
    val wordmarkSecondary = stringResource(R.string.app_wordmark_secondary)
    val glowRadiusPx = with(LocalDensity.current) { palette.wordmarkGlowRadius.toPx() }

    val text = if (wordmarkPrimary.isNotBlank() && wordmarkSecondary.isNotBlank()) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = palette.wordmarkPrimary)) {
                append(wordmarkPrimary)
            }
            withStyle(
                SpanStyle(
                    color = palette.wordmarkAccent,
                    shadow = Shadow(
                        color = palette.wordmarkAccent.copy(alpha = palette.wordmarkGlowAlpha),
                        offset = Offset.Zero,
                        blurRadius = glowRadiusPx
                    )
                )
            ) {
                append(wordmarkSecondary)
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(color = palette.wordmarkPrimary)) { append(appName) }
        }
    }

    Text(
        text = text,
        style = style.copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Pill loading bar: recessed track, gradient fill with a matching glow, and a
 * highlight band sweeping across the fill.
 *
 * Compose has no inner-shadow primitive, so the "recessed" look is approximated
 * with a top-down dark overlay on the track — see the deviation note in the PR.
 */
@Composable
fun DmBrandLoadingBar(
    palette: DmAuthBrandPalette,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    height: Dp = 7.dp
) {
    val loadingDescription = stringResource(R.string.state_loading_message)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)

    val transition = rememberInfiniteTransition(label = "loadingBar")

    val shineProgress = if (reduceMotion) {
        0.5f
    } else {
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(LOADING_SHINE_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "loadingShineProgress"
        )
        animated
    }

    // Indeterminate sweep for the fill itself.
    //
    // There is deliberately no real progress bound here: the only readiness
    // signal the splash has is SessionViewModel.isReady, which is
    // `session.map { true }.onStart { emit(false) }` — one binary flip on the
    // first emission of the session stream, with no distinct stages in between.
    // `UserSession.role` resolving later is not usable as a second stage either,
    // because Splash navigates away on that first emission and, for a signed-out
    // user, the role never resolves at all. So an indeterminate sweep is the
    // honest representation; a width driven by an invented percentage would not
    // be.
    val sweepProgress = if (reduceMotion) {
        0f
    } else {
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(LOADING_SWEEP_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loadingSweepProgress"
        )
        animated
    }

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            // Clip the whole bar, not just each layer: the shine is positioned by
            // translationX and starts off the left edge, and Compose does not clip
            // children to their parent by default — without this the highlight
            // band paints outside the pill and floats on the splash gradient as a
            // stray grey smear. Caught on a full-resolution screencap of the
            // shipped build, not by the build gates.
            .clip(shape)
            .semantics { contentDescription = loadingDescription }
    ) {
        val barWidth = maxWidth

        // Track: base color + a top-down dark wash that fakes the inset.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .drawBehind {
                    drawRect(palette.loadingTrack)
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.loadingTrackInset,
                                Color.Transparent
                            )
                        )
                    )
                }
        )

        // Fill: an indeterminate band that sweeps the track, not a static
        // full-width bar. The old `fillMaxSize()` fill meant the bar sat at a
        // permanent, motionless 100% from its very first frame — which reads as
        // "finished", the one thing it definitely is not while the splash is up.
        //
        // Under reduceMotion it falls back to the full-width bar rather than
        // freezing mid-sweep, which would look like a random half-filled bar
        // stuck in place.
        val fillWidth = if (reduceMotion) barWidth else barWidth * LOADING_SWEEP_WIDTH_FRACTION
        // The band travels between the track's own edges and reverses, so it is
        // always fully visible. Letting it run off both ends instead (offset from
        // -fillWidth to barWidth) leaves the track completely empty at each end
        // of the cycle, which reads as a bar that keeps blinking out — caught on
        // a screencap, where several frames in a row showed bare track.
        val fillOffset = if (reduceMotion) {
            0.dp
        } else {
            (barWidth - fillWidth) * sweepProgress
        }
        Box(
            modifier = Modifier
                .width(fillWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = fillOffset.toPx() }
                .clip(shape)
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to palette.loadingFill[0],
                                0.6f to palette.loadingFill[1],
                                1f to palette.loadingFill[2]
                            )
                        )
                    )
                }
        ) {
            // The shine rides INSIDE the fill band rather than sweeping the whole
            // track independently.
            //
            // It used to be a sibling spanning the full track, which worked while
            // the fill was permanently full-width. Against an indeterminate band
            // it does not: the two travel at different periods, so most of the
            // time the white highlight is sitting on bare track as a grey ghost
            // while the coloured band is somewhere else entirely — verified on a
            // screencap, it reads as a washed-out smear rather than a sheen.
            // Nesting it keeps one coherent moving element and preserves the
            // shine as what it was meant to be: a highlight ON the fill.
            val shineWidth = fillWidth * 0.34f
            val shineOffset = (fillWidth + shineWidth) * shineProgress - shineWidth
            Box(
                modifier = Modifier
                    .width(shineWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = shineOffset.toPx() }
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
            )
        }
    }
}
