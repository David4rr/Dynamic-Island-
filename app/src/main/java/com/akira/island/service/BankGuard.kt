package com.akira.island.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * Deteksi aplikasi perbankan, dompet digital, dan aplikasi finansial.
 * Memastikan Dynamic Island tidak memicu deteksi keamanan perbankan,
 * mencegah tapjacking / obscured window flags, dan menyembunyikan overlay.
 */
object BankGuard {

    private val cache = ConcurrentHashMap<String, Boolean>(64)

    private val BANK_PACKAGES = setOf(
        // Bank & Dompet Digital Indonesia
        "com.bca",
        "id.co.bca.mybca",
        "com.bca.klikbca",
        "id.bmri.livin",
        "id.co.bankmandiri.mandirionline",
        "id.co.bri.brimo",
        "id.co.bni.wondr",
        "src.com.bni",
        "id.co.cimbniaga.octomobile",
        "com.jago.digitalbanking",
        "com.btpn.dc", // Jenius
        "com.seabank.id",
        "id.allobank.mobile",
        "id.co.permatabank.mobile",
        "com.danamon.dbank",
        "id.co.bsi.mobile",
        "id.co.btn.mobile",
        "com.panin.mobile",
        "com.bankneo.neobank",
        "id.dana",
        "com.telkom.mwallet", // LinkAja
        "com.shopeepay.id",
        "ovo.id",
        "com.gojek.app", // GoPay

        // Global Banks & Wallets
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        "ru.nspk.mirpay",
        "com.idamob.tinkoff.android",
        "ru.alfabank.mobile.android",
        "ru.sberbankmobile",
        "ru.vtb24.mobilebanking.android",
        "ru.raiffeisennews",
        "ru.gazprombank.android.mobilebank.app",
        "ru.sovcomcard.halva.v1",
        "ru.mts.money",
        "com.paypal.android.p2pmobile",
        "com.revolut.revolut",
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "com.infonow.bofa",
        "com.citi.citimobile",
        "com.barclays.android.barclaysmobilebanking",
    )

    private val BANK_REGEX = Regex(
        "(^|\\.)(bank|banking|fintech|mbanking|mobilebank|brimo|livin|mybca|octomobile)(\\.|_|$)",
        RegexOption.IGNORE_CASE
    )

    fun isBankingApp(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg == context.packageName) return false

        cache[pkg]?.let { return it }

        val isBank = checkIsBank(context, pkg)
        if (cache.size < 256) {
            cache[pkg] = isBank
        }
        return isBank
    }

    private fun checkIsBank(context: Context, pkg: String): Boolean {
        if (pkg in BANK_PACKAGES) return true
        if (BANK_REGEX.containsMatchIn(pkg)) return true

        return runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            BANK_REGEX.containsMatchIn(appLabel)
        }.getOrDefault(false)
    }

    fun clearCache() {
        cache.clear()
    }
}
