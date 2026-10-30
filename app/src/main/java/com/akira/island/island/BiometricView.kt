package com.akira.island.island

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
 * - Форма острова: закругленная карточка-квадрат (Rounded Square Card ~158x154 dp, скругление 52 dp).
 * - Центр: непрерывный морфинг глифа в Apple CheckBadge без перекрытия и срезаний.
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

        // Крупный центральный глиф с непрерывным векторным морфингом в Apple CheckBadge
        Box(
            modifier = Modifier.size(glyphSize),
            contentAlignment = Alignment.Center,
        ) {
            AppleBiometricGlyph(
                isFace = isFace,
                isSuccess = isSuccess,
                size = glyphSize,
                reduceEffects = s.reduceEffects,
            )
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

            Crossfade(
                targetState = isSuccess,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "bio_status_fade",
            ) { success ->
                val statusColor = if (success) Tints.Green else Color.White.copy(alpha = 0.65f)
                val statusText = if (success) tr("Успешно", "Verified") else (n.state ?: tr("Сканирование...", "Scanning..."))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium.island,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Единый контроллер морфинга: сканирующий глиф плавно тает, а зеленый круг Apple всплывает из центра */
@Composable
fun AppleBiometricGlyph(
    isFace: Boolean,
    isSuccess: Boolean,
    size: Dp,
    reduceEffects: Boolean,
) {
    // Прогресс морфинга от 0f (сканирование) до 1f (успех) по пружине Apple
    val morphProgress by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 260f,
        ),
        label = "morph",
    )

    // Прорисовка белой галочки после раскрытия зеленого круга
    val tickProgress by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = tween(
            durationMillis = 280,
            delayMillis = 70,
            easing = FastOutSlowInEasing,
        ),
        label = "tick",
    )

    // Анимация лазерного луча
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
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
        // 1. Слой сканирующего глифа (Face ID или Touch ID)
        // Растворяется до нуля в начале хода морфинга (0.0 -> 0.4), исключая обрывы и резкие срезания
        val scanAlpha = (1f - morphProgress * 2.5f).coerceIn(0f, 1f)
        val scanScale = 1f - morphProgress * 0.25f

        if (scanAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        alpha = scanAlpha
                        scaleX = scanScale
                        scaleY = scanScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isFace) {
                    AppleFaceIdVector(size, scanY, reduceEffects)
                } else {
                    AppleTouchIdVector(size, scanY, pulse, reduceEffects)
                }
            }
        }

        // 2. Слой зеленого бейджа Apple CheckBadge
        // Плавное раскрытие из центра (0.15 -> 1.0) с пружинящей физикой Apple
        val badgeScale = ((morphProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val badgeAlpha = (morphProgress * 2.0f).coerceIn(0f, 1f)

        if (badgeScale > 0f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                        alpha = badgeAlpha
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val radius = w * 0.44f

                    // Круг цвета зеленого яблока Apple
                    drawCircle(color = Tints.Green, radius = radius)

                    // Чистая белая галочка Apple
                    if (tickProgress > 0f) {
                        val tick = Path().apply {
                            moveTo(w * 0.33f, w * 0.50f)
                            lineTo(w * 0.45f, w * 0.63f)
                            lineTo(w * 0.68f, w * 0.38f)
                        }
                        val part = Path()
                        val measure = PathMeasure()
                        measure.setPath(tick, false)
                        measure.getSegment(0f, measure.length * tickProgress, part, true)
                        drawPath(
                            path = part,
                            color = Color.White,
                            style = Stroke(
                                width = w * 0.082f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Иконический видоискатель Face ID с мягкими скругленными скобками и лазерным лучом */
@Composable
private fun AppleFaceIdVector(size: Dp, scanY: Float, reduceEffects: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height
        val pad = w * 0.08f
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

        // Лазерный луч
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
                start = Offset(w * 0.12f, beamY),
                end = Offset(w * 0.88f, beamY),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Иконический глиф отпечатка Touch ID с подлинным вектором и анимированным лазерным лучом */
@Composable
private fun AppleTouchIdVector(size: Dp, scanY: Float, pulse: Float, reduceEffects: Boolean) {
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
