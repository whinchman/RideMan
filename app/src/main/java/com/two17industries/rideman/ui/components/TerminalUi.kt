package com.two17industries.rideman.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.BorderCyan
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.HotPink
import com.two17industries.rideman.ui.theme.Muted

/**
 * The 217 glow: a single low-radius shadow layer. Deliberately weaker than the three-layer web
 * glow — sunlight legibility comes first. Never apply this to small mono labels.
 */
fun TextStyle.glow(color: Color, blurRadius: Float = 8f): TextStyle =
    copy(shadow = Shadow(color = color.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = blurRadius))

/** The wordmark's block cursor. The only animation in the app: a hard 1s step blink. */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = HotPink,
    width: Dp = 10.dp,
    height: Dp = 21.dp,
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0 using LinearEasing
                1f at 499 using LinearEasing
                0f at 500 using LinearEasing
                0f at 999 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "cursor-alpha",
    )
    Box(
        modifier
            .size(width = width, height = height)
            .alpha(alpha)
            .background(color, RectangleShape)
    )
}

enum class TerminalButtonStyle { PRIMARY, SECONDARY }

/**
 * Sharp, hairline-bordered button. PRIMARY carries a faint accent fill and a glow; SECONDARY is a
 * bare outline. Text is centred when [trailing] is null, otherwise pushed apart from it.
 *
 * `heightIn(min = 48.dp)` floors the button at Android's minimum touch target regardless of
 * [fontSize] — small instances (e.g. 12sp) would otherwise come out under 48dp from padding +
 * line height alone. It sits before `border`/`background` so the border and fill actually grow to
 * fill the floor (not just an invisible touch-target margin), and `verticalAlignment =
 * Alignment.CenterVertically` on the Row keeps content centred once that floor kicks in.
 */
@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TerminalButtonStyle = TerminalButtonStyle.SECONDARY,
    accent: Color = Cyan,
    trailing: String? = null,
    fontSize: TextUnit = 15.sp,
    /**
     * Inner horizontal padding. Narrow it for buttons in tight containers — at the default 16.dp,
     * the ride screen's 96dp side rail leaves only ~44dp of content box and clips "END" to "EN".
     */
    horizontalPadding: Dp = 16.dp,
) {
    val primary = style == TerminalButtonStyle.PRIMARY
    val borderColor = when {
        !enabled -> accent.copy(alpha = 0.15f)
        primary -> accent
        else -> accent.copy(alpha = 0.25f)
    }
    val fill = if (primary && enabled) accent.copy(alpha = 0.12f) else Color.Transparent
    val contentColor = if (enabled) accent else accent.copy(alpha = 0.35f)
    val textStyle = MaterialTheme.typography.titleLarge
        .copy(fontSize = fontSize, letterSpacing = 1.2.sp)
        .let { if (enabled && primary) it.glow(accent, blurRadius = 6f) else it }

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .border(1.dp, borderColor, RectangleShape)
            .background(fill, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (trailing == null) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        Text(text, color = contentColor, style = textStyle, maxLines = 1)
        if (trailing != null) {
            Text(
                trailing,
                color = if (primary) contentColor else Muted,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            )
        }
    }
}

/** A sharp hairline panel. Zero radius, 1px border, optional faint fill. */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = BorderCyan,
    fill: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .border(1.dp, borderColor, RectangleShape)
            .background(fill, RectangleShape),
        content = content,
    )
}

/** A 1px full-width rule. */
@Composable
fun HairLine(modifier: Modifier = Modifier, color: Color = BorderCyanDim) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color, RectangleShape))
}

/** `> LABEL` — grey prompt prefix, accent label, optional dim trailing hint. Small mono, no glow. */
@Composable
fun PromptLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Muted,
    fontSize: TextUnit = 11.sp,
    letterSpacing: TextUnit = 1.7.sp,
    trailing: String? = null,
    trailingColor: Color = Color.Unspecified,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Muted, fontWeight = FontWeight.Normal)) { append("> ") }
            withStyle(SpanStyle(color = color)) { append(text) }
            if (trailing != null) {
                withStyle(
                    SpanStyle(
                        color = trailingColor,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                    )
                ) { append(" $trailing") }
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, letterSpacing = letterSpacing),
    )
}

/** `◀ TITLE` — grey back chevron, accent Orbitron title. */
@Composable
fun BackLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    fontSize: TextUnit = 18.sp,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Muted, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)) {
                append("◀ ")
            }
            withStyle(SpanStyle(color = color)) { append(text) }
        },
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge
            .copy(fontSize = fontSize, letterSpacing = 0.8.sp)
            .glow(color, blurRadius = 6f),
    )
}

/** Square status / toggle box. Filled accent with a black ✓ when checked, hairline outline when not. */
@Composable
fun CheckSquare(
    checked: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Cyan,
    size: Dp = 18.dp,
    glyphSize: TextUnit = 12.sp,
) {
    Box(
        modifier
            .size(size)
            .then(
                if (checked) Modifier.background(accent, RectangleShape)
                else Modifier.border(1.5.dp, accent.copy(alpha = 0.5f), RectangleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                "✓",
                color = Background,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = glyphSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                ),
            )
        }
    }
}
