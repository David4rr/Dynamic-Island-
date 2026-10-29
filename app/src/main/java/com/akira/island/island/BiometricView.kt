package com.akira.island.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
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
import kotlinx.coroutines.delay

/**
 * Анимация биометрии в стиле Apple Face ID / Touch ID:
 * - Форма острова: закругленная карточка-квадрат (Rounded Square Card ~158x154 dp, скругление 52 dp).
 * - Центр: крупный глиф Face ID с лазером или подлинный вектор Touch ID с анимированным лазером.
 * - Упругий морфинг: при подтверждении глиф плавно сжимается, а зеленый круг AppleCheckBadge выпрыгивает с физической пружиной без квадратных рамок.
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
                    val transform = if (targetState) {
                        // Плавный упругий переход в Apple CheckBadge:
                        // Иконка плавно уменьшается и растворяется (130мс)
                        // Зеленый бейдж вырастает от центра с мягкой пружиной Apple (0.5 to 1.0)
                        (fadeIn(tween(180, delayMillis = 30)) + scaleIn(
                            spring(dampingRatio = 0.65f, stiffness = 280f),
                            initialScale = 0.5f,
                        )).togetherWith(
                            fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.8f)
                        )
                    } else {
                        fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                    }
                    transform.using(SizeTransform(clip = false))
                },
                label = "bio_glyph_morph",
            ) { success ->
                if (success) {
                    AppleCheckBadge(glyphSize, s.reduceEffects)
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
                    val transform = if (targetState) {
                        (slideInVertically { it / 2 } + fadeIn(tween(180))).togetherWith(
                            slideOutVertically { -it / 2 } + fadeOut(tween(120))
                        )
                    } else {
                        fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                    }
                    transform.using(SizeTransform(clip = false))
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

/** Зеленый бейдж Apple с плавной прорисовкой галочки без квадратных рамок */
@Composable
fun AppleCheckBadge(size: Dp, reduceEffects: Boolean) {
    val drawTick = remember { Animatable(if (reduceEffects) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceEffects) {
            delay(70)
            drawTick.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val radius = w * 0.44f
        // Идеальный круг цвета сочного зеленого яблока
        drawCircle(color = Tints.Green, radius = radius)

        // Чистая белая галочка Apple
        val tick = Path().apply {
            moveTo(w * 0.33f, w * 0.50f)
            lineTo(w * 0.45f, w * 0.63f)
            lineTo(w * 0.68f, w * 0.38f)
        }
        val part = Path()
        val measure = PathMeasure()
        measure.setPath(tick, false)
        measure.getSegment(0f, measure.length * drawTick.value, part, true)
        drawPath(
            part,
            Color.White,
            style = Stroke(width = w * 0.082f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Иконический видоискатель Apple Face ID с мягкими скругленными скобками и лазерным лучом */
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
        val pad = w * 0.07f
        val innerW = w - 2 * pad
        val innerH = h - 2 * pad
        val strokeWidth = w * 0.075f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cornerLen = innerW * 0.22f
        val r = innerW * 0.16f

        // 4 мягкие угловые скобки видоискателя
        val brackets = Path().apply {
            // Верхняя левая
            moveTo(pad, pad + r + cornerLen)
            lineTo(pad, pad + r)
            arcTo(Rect(pad, pad, pad + 2 * r, pad + 2 * r), 180f, 90f, false)
            lineTo(pad + r + cornerLen, pad)

            // Верхняя правая
            moveTo(w - pad - r - cornerLen, pad)
            lineTo(w - pad - r, pad)
            arcTo(Rect(w - pad - 2 * r, pad, w - pad, pad + 2 * r), 270f, 90f, false)
            lineTo(w - pad, pad + r + cornerLen)

            // Нижняя правая
            moveTo(w - pad, h - pad - r - cornerLen)
            lineTo(w - pad, h - pad - r)
            arcTo(Rect(w - pad - 2 * r, h - pad - 2 * r, w - pad, h - pad), 0f, 90f, false)
            lineTo(w - pad - r - cornerLen, h - pad)

            // Нижняя левая
            moveTo(pad + r + cornerLen, h - pad)
            lineTo(pad + r, h - pad)
            arcTo(Rect(pad, h - pad - 2 * r, pad + 2 * r, h - pad), 90f, 90f, false)
            lineTo(pad, h - pad - r - cornerLen)
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
            moveTo(w * 0.33f, h * 0.69f)
            quadraticTo(w * 0.50f, h * 0.80f, w * 0.67f, h * 0.69f)
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
                start = Offset(w * 0.10f, beamY),
                end = Offset(w * 0.90f, beamY),
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
        // Подлинный векторный отпечаток Material Rounded с мягкими отступами
        Icon(
            imageVector = Icons.Rounded.Fingerprint,
            contentDescription = "Touch ID",
            tint = Color.White,
            modifier = Modifier.size(size * 0.82f),
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
                    start = Offset(w * 0.12f, beamY),
                    end = Offset(w * 0.88f, beamY),
                    strokeWidth = w * 0.05f,
                    cap = StrokeCap.Round,
                )

                // Пульсирующая волна
                val ringRadius = (w * 0.28f) + (pulse * w * 0.20f)
                val ringAlpha = (1f - pulse) * 0.45f
                drawCircle(
                    color = Tints.Teal.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    style = Stroke(width = w * 0.035f),
                )
            }
        }
    }
}
