package com.akira.island.island

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.BiometricInfo
import com.akira.island.core.BiometricStatus
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.data.IslandSettings

/**
 * Анимация биометрии в стиле Apple (Face ID / Touch ID):
 * - Face ID: видоискатель с лицом и вертикальным лазерным лучom сканирования.
 * - Touch ID: концентрические отпечатки с пульсирующей волной.
 * - При подтверждении: трансформация в зеленый бейдж CheckBadge + тактильный отклик.
 */
@Composable
fun BiometricNotice(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val haptic = rememberHaptic()
    val info = n.biometric ?: BiometricInfo()
    val isSuccess = info.status == BiometricStatus.SUCCESS
    val isFace = info.isFace

    LaunchedEffect(n.id, info.status) {
        if (isSuccess) haptic()
    }

    val height = noticeHeight(s)
    val glyphSize = height * 0.44f

    Row(
        modifier = Modifier
            .width(wideWidth)
            .height(height)
            .padding(horizontal = height * 0.28f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(targetState = isSuccess, label = "bio_badge") { success ->
                if (success) {
                    CheckBadge(Tints.Green, animate = !s.reduceEffects, modifier = Modifier.size(glyphSize))
                } else if (isFace) {
                    AppleFaceIdGlyph(glyphSize, s.reduceEffects)
                } else {
                    AppleTouchIdGlyph(glyphSize, s.reduceEffects)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = n.title.ifBlank { if (isFace) "Face ID" else "Touch ID" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold.island,
                    maxLines = 1,
                )
                if (!info.appName.isNullOrBlank()) {
                    Text(
                        text = info.appName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal.island,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.width(compactGap(s)))

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusColor = when (info.status) {
                BiometricStatus.SUCCESS -> Tints.Green
                BiometricStatus.ERROR -> Tints.Red
                BiometricStatus.SCANNING -> Tints.Teal
            }
            val statusText = n.state ?: when (info.status) {
                BiometricStatus.SUCCESS -> if (n.caption.isNullOrBlank()) "Verified" else n.caption
                BiometricStatus.ERROR -> "Try again"
                BiometricStatus.SCANNING -> if (isFace) "Face ID" else "Scan"
            }
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold.island,
                maxLines = 1,
            )
        }
    }
}

/** Иконический видоискатель Apple Face ID с анимированным лазерным лучом */
@Composable
fun AppleFaceIdGlyph(size: Dp, reduceEffects: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "face_scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan_y",
    )

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.082f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cornerLen = w * 0.22f
        val r = w * 0.14f

        // 4 угловые скобки видоискателя
        val brackets = Path().apply {
            // Верхняя левая скобка
            moveTo(0f, r + cornerLen)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            lineTo(r + cornerLen, 0f)

            // Верхняя правая скобка
            moveTo(w - r - cornerLen, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            lineTo(w, r + cornerLen)

            // Нижняя правая скобка
            moveTo(w, h - r - cornerLen)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(w - r - cornerLen, h)

            // Нижняя левая скобка
            moveTo(r + cornerLen, h)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            lineTo(0f, h - r - cornerLen)
        }
        drawPath(brackets, Color.White, style = stroke)

        // Глаза (две аккуратные вертикальные капсулы)
        val eyeH = h * 0.11f
        val eyeW = w * 0.055f
        drawLine(Color.White, Offset(w * 0.35f, h * 0.38f), Offset(w * 0.35f, h * 0.38f + eyeH), strokeWidth = eyeW, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.65f, h * 0.38f), Offset(w * 0.65f, h * 0.38f + eyeH), strokeWidth = eyeW, cap = StrokeCap.Round)

        // Нос
        val nosePath = Path().apply {
            moveTo(w * 0.50f, h * 0.49f)
            lineTo(w * 0.50f, h * 0.58f)
            lineTo(w * 0.56f, h * 0.58f)
        }
        drawPath(nosePath, Color.White, style = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Улыбка (дуга)
        val smilePath = Path().apply {
            moveTo(w * 0.33f, h * 0.70f)
            quadraticTo(w * 0.50f, h * 0.81f, w * 0.67f, h * 0.70f)
        }
        drawPath(smilePath, Color.White, style = Stroke(width = w * 0.065f, cap = StrokeCap.Round))

        // Анимированный лазерный луч сканирования
        if (!reduceEffects) {
            val beamY = h * scanY
            val laserBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Tints.Teal.copy(alpha = 0.85f),
                    Color.White,
                    Tints.Teal.copy(alpha = 0.85f),
                    Color.Transparent,
                ),
            )
            drawLine(
                brush = laserBrush,
                start = Offset(w * 0.08f, beamY),
                end = Offset(w * 0.92f, beamY),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Иконический глиф отпечатка Touch ID с мягкой волной */
@Composable
fun AppleTouchIdGlyph(size: Dp, reduceEffects: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "finger_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round)

        // Концентрические гребни отпечатка
        val ridge1 = Path().apply {
            moveTo(w * 0.36f, h * 0.82f)
            quadraticTo(w * 0.30f, h * 0.55f, w * 0.50f, h * 0.40f)
            quadraticTo(w * 0.70f, h * 0.55f, w * 0.64f, h * 0.82f)
        }
        drawPath(ridge1, Color.White, style = stroke)

        val ridge2 = Path().apply {
            moveTo(w * 0.24f, h * 0.86f)
            quadraticTo(w * 0.16f, h * 0.44f, w * 0.50f, h * 0.24f)
            quadraticTo(w * 0.84f, h * 0.44f, w * 0.76f, h * 0.86f)
        }
        drawPath(ridge2, Color.White, style = stroke)

        // Пульсирующее кольцо сканирования
        if (!reduceEffects) {
            val ringRadius = (w * 0.22f) + (pulse * w * 0.30f)
            val ringAlpha = (1f - pulse) * 0.7f
            drawCircle(
                color = Tints.Teal.copy(alpha = ringAlpha),
                radius = ringRadius,
                style = Stroke(width = w * 0.05f),
            )
        }
    }
}
