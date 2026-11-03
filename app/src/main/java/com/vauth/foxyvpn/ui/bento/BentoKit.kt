package com.vauth.foxyvpn.ui.bento

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection as ComposeLayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.vauth.foxyvpn.ui.l10n.s
import com.vauth.foxyvpn.ui.theme.Bento
import com.vauth.foxyvpn.ui.theme.BentoCorners
import com.vauth.foxyvpn.ui.theme.BentoDensity
import com.vauth.foxyvpn.ui.theme.BentoTitle
import com.vauth.foxyvpn.ui.theme.BentoTone

/**
 * The Bento component kit — Phase B1 of the Zed overhaul.
 *
 * Original implementations of the reference design's primitives
 * (DESIGN-SYSTEM §5–§6): shapes, metrics, type styles, cards, buttons,
 * top bar, chips, pills, flags, badges and the motif watermark. All
 * values are the design spec; icons come from the fork's Material icon
 * set, strings from the fork's l10n.
 */

object BentoShapes {
    @Composable
    fun tile(prominent: Boolean): Shape = when (Bento.prefs.corners) {
        BentoCorners.Leaf -> if (prominent) {
            RoundedCornerShape(36.dp, 36.dp, 36.dp, 12.dp)
        } else {
            RoundedCornerShape(30.dp)
        }
        BentoCorners.Round -> RoundedCornerShape(if (prominent) 36.dp else 30.dp)
        BentoCorners.Soft -> RoundedCornerShape(if (prominent) 24.dp else 20.dp)
    }

    @Composable
    fun hero(): Shape = when (Bento.prefs.corners) {
        BentoCorners.Leaf -> RoundedCornerShape(34.dp, 34.dp, 34.dp, 12.dp)
        BentoCorners.Round -> RoundedCornerShape(34.dp)
        BentoCorners.Soft -> RoundedCornerShape(22.dp)
    }

    @Composable
    fun card(): Shape =
        if (Bento.prefs.corners == BentoCorners.Soft) RoundedCornerShape(20.dp) else RoundedCornerShape(30.dp)

    @Composable
    fun block(): Shape =
        if (Bento.prefs.corners == BentoCorners.Soft) RoundedCornerShape(18.dp) else RoundedCornerShape(26.dp)

    @Composable
    fun small(): Shape =
        if (Bento.prefs.corners == BentoCorners.Soft) RoundedCornerShape(16.dp) else RoundedCornerShape(22.dp)

    @Composable
    fun chip(): Shape =
        if (Bento.prefs.corners == BentoCorners.Soft) RoundedCornerShape(12.dp) else RoundedCornerShape(14.dp)

    @Composable
    fun row(index: Int, count: Int): Shape {
        val soft = Bento.prefs.corners == BentoCorners.Soft
        val outer = if (soft) 16.dp else 22.dp
        val inner = if (soft) 6.dp else 8.dp
        return when {
            count <= 1 -> RoundedCornerShape(outer)
            index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
            index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
            else -> RoundedCornerShape(inner)
        }
    }
}

object BentoMetrics {
    val gutter: Dp = 16.dp

    @Composable
    fun gap(): Dp = if (Bento.prefs.density == BentoDensity.Compact) 6.dp else 8.dp

    @Composable
    fun tileRow(): Dp = if (Bento.prefs.density == BentoDensity.Compact) 116.dp else 134.dp

    @Composable
    fun rowMin(): Dp = if (Bento.prefs.density == BentoDensity.Compact) 54.dp else 62.dp

    @Composable
    fun pad(): Dp = if (Bento.prefs.density == BentoDensity.Compact) 13.dp else 16.dp
}

object BentoType {
    @Composable
    fun display(): TextStyle {
        val (size, line) = when (Bento.prefs.title) {
            BentoTitle.Huge -> 50 to 56
            BentoTitle.Large -> 42 to 48
            BentoTitle.Compact -> 34 to 40
        }
        return MaterialTheme.typography.displayMedium.copy(
            fontSize = size.sp,
            lineHeight = line.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (Bento.latin) (-size * 0.033f).sp else 0.sp,
        )
    }

    @Composable
    fun title(size: TextUnit, tight: Boolean = true): TextStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = size,
            lineHeight = size * 1.18f,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (Bento.latin && tight) (-size.value * 0.022f).sp else 0.sp,
        )

    @Composable
    fun caption(): TextStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = if (Bento.latin) 0.9.sp else 0.sp,
    )

    @Composable
    fun number(size: TextUnit, weight: FontWeight = FontWeight.SemiBold): TextStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = size,
            lineHeight = size * 1.08f,
            fontWeight = weight,
            fontFeatureSettings = "tnum",
            letterSpacing = if (Bento.latin) (-size.value * 0.025f).sp else 0.sp,
        )

    @Composable
    fun body(): TextStyle =
        MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)

    @Composable
    fun rowTitle(): TextStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 15.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A [Shape] that clips to a Material expressive polygon (unit space). */
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: ComposeLayoutDirection,
        density: Density,
    ): Outline {
        val path = Morph(polygon, polygon).toPath(0f, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        val bounds = path.getBounds()
        path.translate(Offset(size.width / 2f - bounds.center.x, size.height / 2f - bounds.center.y))
        return Outline.Generic(path)
    }
}

/**
 * The motif watermark: a big expressive polygon drawn once (cached) at
 * the card's bottom-end corner, bleeding off the edge, at low alpha.
 */
fun Modifier.bentoMotif(
    shape: RoundedPolygon,
    color: Color,
    size: Dp,
    rotation: Float,
    enabled: Boolean = true,
    alpha: Float = 0.12f,
    bleedX: Dp = 30.dp,
    bleedY: Dp = 34.dp,
): Modifier = if (!enabled) this else drawWithCache {
    val px = size.toPx()
    val path = Morph(shape, shape).toPath(0f, Path()).apply {
        transform(Matrix().apply { scale(px, px) })
    }
    val bounds = path.getBounds()
    val bx = bleedX.toPx()
    val by = bleedY.toPx()
    val tint = color.copy(alpha = color.alpha * alpha)
    onDrawBehind {
        val rtl = layoutDirection == ComposeLayoutDirection.Rtl
        val center = Offset(
            if (rtl) px / 2f - bx else this.size.width - px / 2f + bx,
            this.size.height - px / 2f + by,
        )
        translate(center.x - bounds.center.x, center.y - bounds.center.y) {
            rotate(if (rtl) -rotation else rotation, pivot = bounds.center) {
                drawPath(path, tint)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BentoCard(
    tone: BentoTone,
    modifier: Modifier = Modifier,
    shape: Shape = BentoShapes.card(),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    motif: RoundedPolygon? = null,
    motifSize: Dp = 160.dp,
    motifRotation: Float = 16f,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val playful = Bento.prefs.playfulTouch
    val scale by animateFloatAsState(
        if (pressed && onClick != null) (if (playful) 0.965f else 0.985f) else 1f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "bento-card-press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(tone.container)
            .then(
                if (motif != null) {
                    Modifier.bentoMotif(motif, tone.content, motifSize, motifRotation, enabled = Bento.prefs.motifs)
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = source,
                        indication = ripple(color = tone.content),
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides tone.content) {
            Column(
                Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MotifButton(
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val playful = Bento.prefs.playfulTouch
    val spin by animateFloatAsState(
        if (pressed && playful) 34f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "motif-spin",
    )
    val squash by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "motif-squash",
    )
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = spin
                    scaleX = squash
                    scaleY = squash
                    alpha = if (enabled) 1f else 0.45f
                }
                .clip(PolygonShape(shape))
                .background(container),
        )
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun BentoIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Bento.colors.surface,
    content: Color = Bento.colors.ink,
    size: Dp = 46.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.55f),
        label = "icon-squash",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(CircleShape)
            .background(container)
            .semantics { contentDescription = label }
            .clickable(source, ripple(color = content), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun BentoBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    BentoIconButton(Icons.AutoMirrored.Filled.ArrowBack, s("Back"), onClick, modifier)
}

@Composable
fun BentoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = BentoType.display(),
            color = Bento.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onTitleClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTitleClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        actions()
    }
}

@Composable
fun BentoCaption(text: String, modifier: Modifier = Modifier, color: Color = Bento.colors.muted) {
    Text(
        text.uppercase(),
        style = BentoType.caption(),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun BentoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = Bento.colors
    val style = MaterialTheme.typography.bodyLarge.copy(color = c.ink)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(c.surface)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = style, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(c.ink),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            BentoIconButton(
                Icons.Filled.Close,
                s("Clear"),
                { onValueChange("") },
                container = Color.Transparent,
                content = c.muted,
                size = 40.dp,
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BentoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: String? = null,
) {
    val c = Bento.colors
    val shape = BentoShapes.chip()
    val bg = if (selected) c.lime else Color.Transparent
    val fg = if (selected) c.onLime else c.ink
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.line, shape))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = fg),
                role = Role.Tab,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(
                trailing,
                style = BentoType.number(13.sp),
                color = fg.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/** Latin/digit runs are isolated LTR inside RTL text (numbers, IPs, times). */
fun ltrIsolate(text: String): String = "⁦$text⁩"

@Composable
fun BentoPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    numeric: Boolean = false,
) {
    Row(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(container)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            if (numeric) ltrIsolate(text) else text,
            style = if (numeric) {
                BentoType.number(13.sp)
            } else {
                MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            },
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Country flag tile. The reference loads bundled flag SVGs; B1 ships
 * the reference's own fallback rendering (the country code as bold
 * text) — flag assets are a Phase B2 question (DESIGN-MAP §12).
 */
@Composable
fun BentoFlag(
    countryCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    corner: Dp = 13.dp,
    fallback: String? = null,
    container: Color = Bento.colors.surface2,
    content: Color = Bento.colors.ink,
) {
    val code = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(container)
            .border(1.dp, content.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (fallback ?: code ?: "··"),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.Bold,
            ),
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
fun BentoIndex(index: Int, color: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    Text(
        "%02d".format(index),
        style = BentoType.number(12.sp, FontWeight.Medium),
        color = color.copy(alpha = color.alpha * 0.7f),
        modifier = modifier,
    )
}

@Composable
fun BentoBadge(
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    icon: ImageVector,
    size: Dp,
    modifier: Modifier = Modifier,
    rotation: Float = 10f,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(PolygonShape(shape))
                .background(container),
        )
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** A shape that morphs between two expressive polygons as [progress] goes 0→1. */
@Composable
fun MorphingBlob(
    progress: Float,
    color: Color,
    startShape: RoundedPolygon,
    endShape: RoundedPolygon,
    modifier: Modifier = Modifier,
    content: BoxScopeContent = {},
) {
    val morph = remember(startShape, endShape) { Morph(startShape, endShape) }
    Box(
        modifier.clip(MorphShape(morph, progress)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

typealias BoxScopeContent = @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit

class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: ComposeLayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f), Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        val bounds = path.getBounds()
        path.translate(Offset(size.width / 2f - bounds.center.x, size.height / 2f - bounds.center.y))
        return Outline.Generic(path)
    }
}
