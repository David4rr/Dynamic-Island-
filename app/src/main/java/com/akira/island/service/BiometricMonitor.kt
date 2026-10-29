package com.akira.island.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.akira.island.R
import com.akira.island.core.BiometricInfo
import com.akira.island.core.BiometricStatus
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.data.BiometricMode
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Отслеживание биометрической аутентификации (BiometricPrompt и разблокировка экрана):
 * - Ловит диалоги сканирования лица и пальца из com.android.systemui.
 * - Показывает сканирование в острове и подтверждение при успехе.
 */
object BiometricMonitor {

    private var activePrompt = false
    private var activePkg: String? = null
    private var isFaceActive = true
    private var promptShownTime = 0L
    private var demoJob: Job? = null
    private var lastUnlockTime = 0L

    /** Проверяет, относится ли событие com.android.systemui к биометрии. */
    fun isBiometricEvent(event: AccessibilityEvent): Boolean {
        val cls = event.className?.toString().orEmpty()
        return cls.contains("AuthContainerView", ignoreCase = true) ||
            cls.contains("AuthBiometric", ignoreCase = true) ||
            cls.contains("BiometricPrompt", ignoreCase = true) ||
            cls.contains("FingerprintDialog", ignoreCase = true) ||
            cls.contains("BiometricDialog", ignoreCase = true) ||
            cls.contains("FingerprintView", ignoreCase = true)
    }

    /** Вызывается из IslandService при изменении состояния окон */
    fun onWindowState(event: AccessibilityEvent, context: Context) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evBiometric) return

        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()

        if (pkg == "com.android.systemui" && isBiometricEvent(event)) {
            if (!activePrompt) {
                activePrompt = true
                promptShownTime = SystemClock.elapsedRealtime()
                activePkg = IslandBus.foregroundPackage.value

                val mode = s.biometricMode
                isFaceActive = when (mode) {
                    BiometricMode.FACE -> true
                    BiometricMode.FINGERPRINT -> false
                    BiometricMode.AUTO -> "face" in cls.lowercase()
                }

                val appName = activePkg?.let { getAppName(context, it) }
                showScanning(context, isFaceActive, appName)
            }
            return
        }

        // Если диалог биометрии закрылся и фокус вернулся в приложение — аутентификация прошла успешно
        if (activePrompt && pkg.isNotBlank() && pkg != "com.android.systemui") {
            val elapsed = SystemClock.elapsedRealtime() - promptShownTime
            activePrompt = false
            // Если диалог пробыл на экране хотя бы 300мс перед уходом — считаем успехом
            if (elapsed > 300) {
                val appName = activePkg?.let { getAppName(context, it) }
                showSuccess(context, isFaceActive, appName)
            } else {
                IslandBus.notice.value = null
            }
        }
    }

    /** Вызывается, когда экран разблокировали биометрией */
    fun onUnlockBiometric(context: Context) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evBiometric) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastUnlockTime < 2_500) return
        lastUnlockTime = now

        val isFace = s.biometricMode != BiometricMode.FINGERPRINT
        showSuccess(context, isFace, tr("Разблокировано", "Unlocked"))
    }

    private fun showScanning(context: Context, isFace: Boolean, appName: String?) {
        val title = if (isFace) "Face ID" else "Touch ID"
        val state = tr("Сканирование...", "Scanning...")
        IslandBus.post(
            Notice(
                title = title,
                state = state,
                icon = Ios.load(context, if (isFace) R.drawable.ios_lock_closed else R.drawable.ios_checkmark_circle),
                tint = Tints.Teal,
                style = NoticeStyle.BIOMETRIC,
                biometric = BiometricInfo(
                    status = BiometricStatus.SCANNING,
                    isFace = isFace,
                    appName = appName,
                ),
                extraMs = 4_000L,
            ),
        )
    }

    private fun showSuccess(context: Context, isFace: Boolean, captionText: String?) {
        val title = if (isFace) "Face ID" else "Touch ID"
        val verified = tr("Успешно", "Verified")
        IslandBus.post(
            Notice(
                title = title,
                state = verified,
                caption = captionText,
                icon = Ios.load(context, R.drawable.ios_checkmark_circle),
                tint = Tints.Green,
                style = NoticeStyle.BIOMETRIC,
                biometric = BiometricInfo(
                    status = BiometricStatus.SUCCESS,
                    isFace = isFace,
                    appName = captionText,
                ),
                extraMs = 900L,
            ),
        )
    }

    /** Демо-проверка для экрана настроек и проверки острова */
    fun demo(context: Context, isFace: Boolean) {
        demoJob?.cancel()
        demoJob = IslandBus.scope.launch {
            val appTitle = tr("Остров", "Island")
            showScanning(context, isFace, appTitle)
            delay(1200)
            showSuccess(context, isFace, appTitle)
        }
    }

    private fun getAppName(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrNull() ?: packageName
    }
}
