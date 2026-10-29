package com.akira.island.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
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
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings

/**
 * Анимация биометрии в стиле Apple Face ID / Touch ID:
 * - Форма острова: закругленная карточка-квадрат (Rounded Square Card ~158x154 dp).
 * - Центр: крупный глиф Face ID с лазером или подлинный вектор Touch ID с анимированным лазером.
 * - Упругий морфинг: при подтверждении глиф плавно сжимается, а зеленый CheckBadge выпрыгивает с физической пружиной.
 */
@Composable
fun BiometricNotice(n: Notice, s: IslandSettings) {
    val haptic = rememberHaptic()
    val info = n.biometric ?: BiometricInfo()
    val isSuccess = info.status == BiometricStatus.SUCCESS
    val isFace = info.isFace

    LaunchedEffect(n.id, info.status) {
        if (isSuccess) haptic()
    }

    val cardWidth = 158.dp
    val topInset = cameraInset(s).coerceAtLeast(18.dp)
    val cardHeight = 126.dp + topInset
    val glyphSize = 56.dp

    Column(
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .padding(top = topInset, bottom = 14.dp, start = 14.dp, end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier.size(glyphSize),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isSuccess,
                transitionSpec = {
                    if (targetState) {
                        // Упругий эластичный морфинг в зеленый CheckBadge Apple:
                        // Старый глиф быстро уменьшается (scaleOut 0.7) и тает за 120 мс
                        // Зеленый бейдж выпрыгивает с пружиной от 0.4 до 1.0 (spring bounce)
                        (fadeIn(tween(180, delayMillis = 50)) + scaleIn(
                            spring(dampingRatio = 0.58f, stiffness = 320f),
                            initialScale = 0.4f,
                        )).togetherWith(
                            fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f)
                        )
                    } else {
                        fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                    }
                },
                label = "bio_glyph_morph",
            ) { success ->
                if (success) {
                    CheckBadge(
                        color = Tints.Green,
                        animate = !s.reduceEffects,
                        modifier = Modifier.size(glyphSize),
                    )
                } else if (isFace) {
                    AppleFaceIdGlyph(glyphSize, s.reduceEffects)
                } else {
                    AppleTouchIdGlyph(glyphSize, s.reduceEffects)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val titleText = n.caption ?: n.title.ifBlank { if (isFace) "Face ID" else "Touch ID" }
            FitText(
                text = titleText,
                color = Color.White,
                max = 16.sp,
                min = 12.sp,
                weight = FontWeight.SemiBold.island,
            )

            Spacer(Modifier.height(3.dp))

            AnimatedContent(
                targetState = isSuccess,
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically { it / 2 } + fadeIn(tween(180))).togetherWith(
                            slideOutVertically { -it / 2 } + fadeOut(tween(120))
                        )
                    } else {
                        fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                    }
                },
                label = "bio_status_anim",
            ) { success ->
                val statusColor = if (success) Tints.Green else Color.White.copy(alpha = 0.65f)
                val statusText = if (success) {
                    tr("Успешно", "Verified")
                } else {
                    n.state ?: tr("Сканирование...", "Scanning...")
                }
                FitText(
                    text = statusText,
                    color = statusColor,
                    max = 12.sp,
                    min = 10.sp,
                    weight = FontWeight.Medium.island,
                    style = Digits,
                )
            }
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

        // Глаза (две вертикальные капсулы)
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

/** Иконический глиф отпечатка Touch ID с подлинным вектором и анимированным лазерным лучом */
@Composable
fun AppleTouchIdGlyph(size: Dp, reduceEffects: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "finger_scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan_y",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Подлинный векторный отпечаток Material Rounded
        Icon(
            imageVector = Icons.Rounded.Fingerprint,
            contentDescription = "Touch ID",
            tint = Color.White,
            modifier = Modifier.fillMaxSize(),
        )

        // Анимированный лазерный луч и радарное кольцо сканирования
        if (!reduceEffects) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height

                // Лазерный луч
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
                    strokeWidth = w * 0.055f,
                    cap = StrokeCap.Round,
                )

                // Пульсирующая волна
                val ringRadius = (w * 0.28f) + (pulse * w * 0.22f)
                val ringAlpha = (1f - pulse) * 0.5f
                drawCircle(
                    color = Tints.Teal.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    style = Stroke(width = w * 0.04f),
                )
            }
        }
    }
}
