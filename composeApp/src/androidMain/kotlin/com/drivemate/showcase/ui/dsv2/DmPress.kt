package com.drivemate.showcase.ui.dsv2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.LocalReduceMotion
import kotlinx.coroutines.launch

/**
 * Satu sumber reaksi sentuh untuk SEMUA ikon/elemen yang bisa diketuk
 * (spesifikasi `docs/drivemate-ux-v3.html` bagian 4):
 *
 * - Ditekan: skala per [DmPressKind] (turun ±100 ms) + state layer bundar
 *   opasitas 10%. Warna state layer = [LocalContentColor] opasitas 10% -- SATU
 *   pendekatan konsisten untuk terang & gelap (bukan cabang onSurface/onPrimary):
 *   otomatis mengikuti warna konten di atas permukaan netral maupun aplikatif,
 *   dan identik dengan takaran "pressed 10%" Material 3.
 * - Dilepas: kembali ke 1 dengan pegas (spring bouncy).
 * - Reduce motion ([LocalReduceMotion]): tanpa skala/pegas (snap), state layer
 *   statis tetap tampil selama ditekan.
 *
 * [DmPressKind.Tab] sengaja TIDAK menggambar state layer di node ini: tab
 * navigasi menggambar state layer pada pil indikatornya sendiri
 * (`FloatingNavBarShell`), skala 0,9 tetap diberikan oleh node.
 */
object DmPressIndication : IndicationNodeFactory by DmPressIndicationFactory(DmPressKind.Icon)

/** Indikasi dengan jenis tekan selain [DmPressKind.Icon]. */
internal fun dmPressIndication(kind: DmPressKind): Indication =
    if (kind == DmPressKind.Icon) DmPressIndication else DmPressIndicationFactory(kind)

private class DmPressIndicationFactory(private val kind: DmPressKind) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node = DmPressNode(interactionSource, kind)

    override fun equals(other: Any?) = other is DmPressIndicationFactory && other.kind == kind

    override fun hashCode() = kind.hashCode()
}

private class DmPressNode(
    private val interactionSource: InteractionSource,
    private val kind: DmPressKind,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val scaleAnim = Animatable(1f)
    private var pressed by mutableStateOf(false)
    private val reduceMotion: Boolean get() = currentValueOf(LocalReduceMotion)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressed = true
                        val target = pressScaleTarget(pressed = true, reduceMotion = reduceMotion, kind = kind)
                        if (reduceMotion) {
                            scaleAnim.snapTo(target)
                        } else {
                            // Turun cepat (±100 ms) supaya terasa instan.
                            scaleAnim.animateTo(target, animationSpec = tween(durationMillis = 100))
                        }
                    }
                    is PressInteraction.Release -> release()
                    is PressInteraction.Cancel -> release()
                }
            }
        }
    }

    private suspend fun release() {
        pressed = false
        if (reduceMotion) {
            scaleAnim.snapTo(1f)
        } else {
            scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    override fun ContentDrawScope.draw() {
        // Kind Tab: state layer digambar pada pil oleh FloatingNavBarShell.
        if (pressed && kind != DmPressKind.Tab) {
            drawCircle(
                color = currentValueOf(LocalContentColor).copy(alpha = DmPressLayerAlpha),
                radius = minOf(size.width, size.height) / 2f
            )
        }
        val scaleValue = scaleAnim.value
        scale(scaleValue, pivot = center) {
            this@draw.drawContent()
        }
    }
}

/** Opasitas state layer "pressed" Material 3. */
internal const val DmPressLayerAlpha = 0.10f

/** Jenis elemen yang bisa ditekan; menentukan target skala saat ditekan. */
enum class DmPressKind(val pressedScale: Float) {
    Icon(0.92f),
    Tab(0.90f),
    Meaningful(0.96f)
}

/** Target skala murni -- fungsi ini yang diuji unit (lihat `DmPressTest`). */
fun pressScaleTarget(pressed: Boolean, reduceMotion: Boolean, kind: DmPressKind): Float =
    if (!pressed || reduceMotion) 1f else kind.pressedScale

/**
 * Tombol ikon standar DriveMate: target sentuh minimum 48dp, `Role.Button`,
 * indikasi [DmPressIndication]. Haptik hanya berbunyi bila [hapticOnClick].
 * Parameter [tint] ada untuk mempertahankan warna ikon pemanggil lama
 * (deviasi kecil dari signature spesifikasi, dibutuhkan agar visual tidak berubah).
 */
@Composable
fun DmIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: DmPressKind = DmPressKind.Icon,
    hapticOnClick: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .sizeIn(minWidth = DmPressTouchTarget, minHeight = DmPressTouchTarget)
            .dmClickable(
                onClick = {
                    if (hapticOnClick) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                },
                role = Role.Button,
                kind = kind,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint)
    }
}

private val DmPressTouchTarget = 48.dp

/**
 * Pengganti pola clickable tanpa indikasi bawaan -- semua elemen yang bisa
 * diketuk wajib lewat sini supaya reaksinya seragam. [feedback] = false dipakai
 * untuk penangkap sentuh tanpa umpan balik visual (mis. scrim dialog) --
 * perilaku identik dengan pola lama, tanpa menulis pola terlarang itu di
 * berkas lain.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dmClickable(
    onClick: () -> Unit,
    role: Role? = null,
    kind: DmPressKind = DmPressKind.Icon,
    hapticType: HapticFeedbackType? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    feedback: Boolean = true,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val indication = if (feedback) dmPressIndication(kind) else null
    val click = {
        if (hapticType != null) haptic.performHapticFeedback(hapticType)
        onClick()
    }
    if (onLongClick == null) {
        Modifier.clickable(
            interactionSource = source,
            indication = indication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = click
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = source,
            indication = indication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = click,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }
        )
    }
}

/**
 * Skala tekan manual untuk elemen yang perlu `graphicsLayer` sendiri
 * (dipakai tab/ubin). Hormat reduce motion (snap, target selalu 1).
 */
fun Modifier.dmPressScale(
    interactionSource: InteractionSource,
    kind: DmPressKind = DmPressKind.Icon
): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleValue by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, reduceMotion, kind),
        animationSpec = when {
            reduceMotion -> snap()
            pressed -> tween(durationMillis = 100)
            else -> spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "dmPressScale"
    )
    Modifier.graphicsLayer {
        scaleX = scaleValue
        scaleY = scaleValue
    }
}

/**
 * Skala "pop" ikon tab terpilih 0,8 → 1,2 → 1 (total ±320 ms) saat tab BARU
 * saja dipilih; tidak mempop pada komposisi pertama. [key] wajib disediakan
 * bila dipanggil di dalam loop (ingat-state per item). Reduce motion: tetap 1.
 */
@Composable
fun rememberDmTabPopScale(selected: Boolean, key: Any? = null): Float {
    val reduceMotion = LocalReduceMotion.current
    val anim = remember(key) { Animatable(1f) }
    var wasSelected by remember(key) { mutableStateOf(selected) }
    LaunchedEffect(key, selected) {
        if (selected && !wasSelected && !reduceMotion) {
            anim.snapTo(0.8f)
            anim.animateTo(1.2f, animationSpec = tween(durationMillis = 140))
            anim.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }
        wasSelected = selected
    }
    return anim.value
}
