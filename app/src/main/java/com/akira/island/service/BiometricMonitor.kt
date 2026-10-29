package com.akira.island.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pemantau Otentikasi Biometrik Apple (Face ID & Touch ID):
 * - Mendeteksi App Lock (WhatsApp, Telegram, Perbankan, dll.) & System BiometricPrompt.
 * - Menampilkan animasi pemindaian dinamis di Dynamic Island dan morphing ke CheckBadge hijau.
 */
object BiometricMonitor {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var biometricJob: Job? = null
    private var demoJob: Job? = null

    private var activePrompt = false
    private var promptPkg: String? = null
    private var promptCls: String? = null
    private var promptStartTime = 0L
    private var isFaceActive = false
    private var lastSuccessTime = 0L
    private var lastUnlockTime = 0L

    private val SYSTEM_SECURITY_PKGS = setOf(
        "com.android.systemui",
        "com.samsung.android.biometrics.app.setting",
        "com.miui.securityadd",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.vivo.permissionmanager",
    )

    private val BIO_CLASSES = listOf(
        "AppAuthentication",
        "FingerprintDialog",
        "BiometricPrompt",
        "AuthContainer",
        "AuthBiometric",
        "BiometricDialog",
        "FingerprintView",
        "AppLock",
        "PasscodeActivity",
        "FingerprintActivity",
        "BiometricActivity",
        "FingerprintHelper",
        "AppLockActivity",
        "ConfirmDeviceCredential",
        "BiometricAuthentication",
        "FingerprintAuthentication",
        "PinActivity",
    )

    private val BIO_KEYWORDS = listOf(
        "fingerprint", "biometric", "face unlock", "touch sensor", "sentuh sensor",
        "sidik jari", "biometrik", "verifikasi identitas", "whatsapp dikunci",
        "whatsapp locked", "buka kunci", "kunci aplikasi", "отпечаток", "биометр",
        "confirm your fingerprint", "verify your identity", "touch the fingerprint sensor",
        "buka kunci dengan sidik jari", "pindai sidik jari", "sensor sidik jari",
        "kunci wa", "verifikasi sidik jari", "masuk dengan sidik jari", "login dengan sidik jari",
        "masuk dengan biometrik", "autentikasi sidik jari", "use fingerprint",
        "sign in with biometric", "scan your fingerprint", "unlock with fingerprint",
        "touch id", "face id", "tempelkan sidik jari",
    )

    fun isSystemPackage(pkg: String): Boolean = pkg in SYSTEM_SECURITY_PKGS

    private fun hasKeyword(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return BIO_KEYWORDS.any { it in lower }
    }

    private fun eventHasBioText(event: AccessibilityEvent): Boolean {
        val desc = event.contentDescription?.toString().orEmpty()
        if (hasKeyword(desc)) return true
        for (t in event.text) {
            if (hasKeyword(t.toString())) return true
        }
        return false
    }

    private fun sourceHasBio(source: AccessibilityNodeInfo?): Boolean {
        if (source == null) return false
        val id = source.viewIdResourceName?.lowercase().orEmpty()
        if (id.contains("biometric") || id.contains("fingerprint") || id.contains("auth_container")) return true
        val text = source.text?.toString().orEmpty()
        if (hasKeyword(text)) return true
        val desc = source.contentDescription?.toString().orEmpty()
        if (hasKeyword(desc)) return true
        for (i in 0 until minOf(source.childCount, 8)) {
            val child = source.getChild(i) ?: continue
            val childId = child.viewIdResourceName?.lowercase().orEmpty()
            val childText = child.text?.toString().orEmpty()
            if (childId.contains("biometric") || childId.contains("fingerprint") || hasKeyword(childText)) return true
        }
        return false
    }

    fun isBiometricEvent(pkg: String, cls: String, event: AccessibilityEvent): Boolean {
        // 1. Class name matches known app lock / biometric prompt classes (e.g. WhatsApp AppAuthenticationActivity)
        if (BIO_CLASSES.any { cls.contains(it, ignoreCase = true) }) return true

        // 2. System UI or security package with dialog/container/text
        if (isSystemPackage(pkg)) {
            if (cls.contains("Dialog", ignoreCase = true) || cls.contains("Auth", ignoreCase = true) ||
                cls.contains("Biometric", ignoreCase = true) || cls.contains("Frame", ignoreCase = true)
            ) {
                if (eventHasBioText(event) || sourceHasBio(event.source)) return true
            }
        }

        // 3. Any app window that explicitly displays biometric lock keywords
        if (eventHasBioText(event) || sourceHasBio(event.source)) return true

        return false
    }

    fun isLauncher(context: Context, pkg: String): Boolean {
        if (pkg.contains("launcher", ignoreCase = true) || pkg.contains("home", ignoreCase = true)) return true
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == pkg
    }

    fun onWindowState(event: AccessibilityEvent, context: Context) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evBiometric) return

        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        if (pkg.isBlank()) return

        val isBio = isBiometricEvent(pkg, cls, event)

        if (isBio) {
            if (!activePrompt) {
                activePrompt = true
                promptStartTime = SystemClock.elapsedRealtime()
                promptPkg = if (isSystemPackage(pkg)) (IslandBus.foregroundPackage.value ?: pkg) else pkg
                promptCls = cls

                val mode = s.biometricMode
                val textLower = (event.text.joinToString(" ") + " " + cls).lowercase()
                isFaceActive = when (mode) {
                    BiometricMode.FACE -> true
                    BiometricMode.FINGERPRINT -> false
                    BiometricMode.AUTO -> "face" in textLower || "wajah" in textLower || "muka" in textLower
                }

                val appName = promptPkg?.let { getAppName(context, it) }
                showScanning(context, isFaceActive, appName)
            }
            return
        }

        // Jika sebelumnya sedang prompt aktif:
        if (activePrompt) {
            val elapsed = SystemClock.elapsedRealtime() - promptStartTime
            val sameApp = (pkg == promptPkg)
            val dialogClosed = isSystemPackage(promptCls.orEmpty()) && !isSystemPackage(pkg)

            if (isLauncher(context, pkg)) {
                // Pengguna keluar / membatalkan ke Home
                cancelPrompt()
            } else if ((sameApp && cls != promptCls) || dialogClosed) {
                // Otentikasi sukses! WhatsApp/aplikasi berpindah ke tampilan utama
                if (elapsed > 200) {
                    val appName = promptPkg?.let { getAppName(context, it) }
                    showSuccess(context, isFaceActive, appName)
                } else {
                    cancelPrompt()
                }
                activePrompt = false
            }
        }
    }

    fun onContentChanged(event: AccessibilityEvent, context: Context) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evBiometric || activePrompt) return
        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        if (pkg.isBlank() || isLauncher(context, pkg)) return

        if (isBiometricEvent(pkg, cls, event)) {
            activePrompt = true
            promptStartTime = SystemClock.elapsedRealtime()
            promptPkg = if (isSystemPackage(pkg)) (IslandBus.foregroundPackage.value ?: pkg) else pkg
            promptCls = cls

            val mode = s.biometricMode
            val textLower = (event.text.joinToString(" ") + " " + cls).lowercase()
            isFaceActive = when (mode) {
                BiometricMode.FACE -> true
                BiometricMode.FINGERPRINT -> false
                BiometricMode.AUTO -> "face" in textLower || "wajah" in textLower || "muka" in textLower
            }

            val appName = promptPkg?.let { getAppName(context, it) }
            showScanning(context, isFaceActive, appName)
        }
    }

    fun onWindowsChanged(context: Context) {
        if (!activePrompt) return
        val elapsed = SystemClock.elapsedRealtime() - promptStartTime
        if (elapsed > 350) {
            val fg = IslandBus.foregroundPackage.value
            if (fg != null && fg == promptPkg && !isLauncher(context, fg)) {
                activePrompt = false
                val appName = promptPkg?.let { getAppName(context, it) }
                showSuccess(context, isFaceActive, appName)
            }
        }
    }

    fun onUnlockBiometric(context: Context) {
        val s = SettingsRepo.current
        if (!s.enabled || !s.evBiometric) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastUnlockTime < 2_500) return
        lastUnlockTime = now

        val isFace = s.biometricMode != BiometricMode.FINGERPRINT
        showSuccess(context, isFace, tr("Разблокировано", "Unlocked"))
    }

    fun showScanning(context: Context, isFace: Boolean, appName: String?) {
        biometricJob?.cancel()
        val title = appName ?: if (isFace) "Face ID" else "Touch ID"
        val state = tr("Сканирование...", "Scanning...")
        val notice = Notice(
            title = title,
            state = state,
            caption = appName,
            icon = Ios.load(context, R.drawable.ios_lock_closed),
            tint = Tints.Teal,
            style = NoticeStyle.BIOMETRIC,
            biometric = BiometricInfo(
                status = BiometricStatus.SCANNING,
                isFace = isFace,
                appName = appName,
            ),
            extraMs = 8_000L,
        )
        // Pembaruan nilai langsung untuk responsivitas instan tanpa antrean
        IslandBus.notice.value = notice
    }

    fun showSuccess(context: Context, isFace: Boolean, appName: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSuccessTime < 1_200) return
        lastSuccessTime = now

        biometricJob?.cancel()
        val title = appName ?: if (isFace) "Face ID" else "Touch ID"
        val verified = tr("Успешно", "Verified")
        val successNotice = Notice(
            title = title,
            state = verified,
            caption = appName,
            icon = Ios.load(context, R.drawable.ios_checkmark_circle),
            tint = Tints.Green,
            style = NoticeStyle.BIOMETRIC,
            biometric = BiometricInfo(
                status = BiometricStatus.SUCCESS,
                isFace = isFace,
                appName = appName,
            ),
            extraMs = 1_500L,
        )
        IslandBus.notice.value = successNotice
        biometricJob = scope.launch {
            delay(1600)
            if (IslandBus.notice.value?.id == successNotice.id) {
                IslandBus.notice.value = null
            }
        }
    }

    fun cancelPrompt() {
        activePrompt = false
        biometricJob?.cancel()
        IslandBus.notice.value = null
    }

    fun demo(context: Context, isFace: Boolean, appName: String? = null) {
        demoJob?.cancel()
        demoJob = scope.launch {
            val app = appName ?: if (isFace) "WhatsApp" else "BCA mobile"
            showScanning(context, isFace, app)
            delay(1400)
            showSuccess(context, isFace, app)
        }
    }

    private fun getAppName(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }
}
