package com.akira.island.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.akira.island.data.SettingsRepo
import java.util.concurrent.ConcurrentHashMap

data class BankingAppInfo(
    val packageName: String,
    val label: String,
    val isAutoDetected: Boolean,
    val isCustom: Boolean,
    val isExcluded: Boolean,
)

/**
 * Deteksi dinamis aplikasi perbankan, dompet digital, dan aplikasi finansial.
 * Menggabungkan fast lookup, analisis token nama paket, analisis label aplikasi dari PackageManager,
 * deteksi layanan NFC Host APDU payment, serta konfigurasi kustom pengguna.
 */
object BankGuard {

    private val cache = ConcurrentHashMap<String, Boolean>(128)

    private val EXEMPT_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.google.android.youtube",
        "com.spotify.music",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.facebook.katana",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.google.android.apps.maps",
        "com.google.android.gm",
        "com.google.android.googlequicksearchbox",
        "com.android.systemui",
        "com.android.vending",
    )

    private val BANK_PACKAGES = setOf(
        // Bank & Dompet Digital Indonesia
        "com.bca",
        "id.co.bca.mybca",
        "com.bca.klikbca",
        "com.bcas.mobile",
        "id.bmri.livin",
        "id.co.bankmandiri.mandirionline",
        "id.co.bri.brimo",
        "id.co.bni.wondr",
        "src.com.bni",
        "id.co.cimbniaga.octomobile",
        "com.jago.digitalbanking",
        "com.btpn.dc", // Jenius
        "id.co.bankbkemobile.digitalbank", // SeaBank Indonesia
        "ph.seabank.seabank", // SeaBank Philippines / MariBank
        "vn.com.seabank.mb1", // SeaBank Vietnam
        "id.allobank.mobile",
        "com.linecorp.linebank.id",
        "com.superbank.id",
        "id.co.superbank",
        "com.bankneo.neobank",
        "com.krom.bank",
        "id.co.aladinbank",
        "com.amarbank.tunaiku",
        "com.muamalat.din",
        "id.co.bsi.mobile",
        "id.co.btn.mobile",
        "com.danamon.dbank",
        "com.danamon.dbankreg",
        "id.co.permatabank.mobile",
        "com.panin.mobile",
        "com.bankmega.msmile",
        "com.woorisaudarabank",
        "com.maybank.id",
        "id.co.maybank",
        "com.ocbc.mobile",
        "com.uob.mighty.id",
        "id.co.bankbjb.digi",
        "com.bankdki.jakone",
        "com.bankjatim.jconnect",
        "com.bankjateng.bima",
        "com.banknagari.ollin",
        "id.co.bpddiy.mobile",
        "id.co.banksumut.mobile",
        "id.co.bankkalbar.mobile",
        "id.co.bankkalsel.mobile",
        "id.co.banksulselbar.mobile",
        "id.co.bankntbsyariah.mobile",
        "id.co.bankpapua.mobile",
        "com.mnc.motionbank",
        "com.nobubank.nobuneopay",
        "com.bank.sinarmas",
        "com.commbank.commbankid",
        "id.dana",
        "ovo.id",
        "com.gojek.app", // GoPay
        "com.gopay.wallet",
        "com.shopeepay.id",
        "com.telkom.mwallet", // LinkAja
        "com.astrapay.android",
        "com.indomaret.isaku",
        "com.kredivo",
        "com.akulaku",
        "com.indodax",
        "com.tokocrypto",
        "com.pintu.android",
        "com.pluang.android",
        "com.bibit.bid",
        "com.bareksa",
        "com.ajaib.invest",
        "com.stockbit.android",

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
        "com.globe.gcash.android",
        "com.voyagerinnovation.paymaya",
    )

    fun isBankingApp(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg == context.packageName) return false

        val s = SettingsRepo.current
        if (pkg in s.bankingAppExclusions) return false
        if (pkg in s.customBankingApps) return true

        cache[pkg]?.let { return it }

        val isBank = checkIsBank(context, pkg)
        if (cache.size < 512) {
            cache[pkg] = isBank
        }
        return isBank
    }

    private fun checkIsBank(context: Context, pkg: String): Boolean {
        if (pkg in EXEMPT_PACKAGES) return false
        if (pkg in BANK_PACKAGES) return true

        val p = pkg.lowercase()
        if (matchesPackage(p)) return true

        val labelMatched = runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            matchesLabel(label)
        }.getOrDefault(false)
        if (labelMatched) return true

        return runCatching {
            val nfcIntent = Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE").setPackage(pkg)
            context.packageManager.queryIntentServices(nfcIntent, 0).isNotEmpty()
        }.getOrDefault(false)
    }

    private fun matchesPackage(p: String): Boolean {
        if (p.contains("powerbank") || p.contains("bloodbank")) return false

        // Dinamis mendeteksi segala variasi kata bank di nama paket (contoh: id.co.bankbkemobile.digitalbank)
        if (p.contains("bank") || p.contains("banking") || p.contains("fintech") || p.contains("mbanking") ||
            p.contains("mobilebank") || p.contains("mwallet") || p.contains("ewallet") || p.contains("payment")
        ) return true

        return p.contains("brimo") || p.contains("livin") || p.contains("mybca") || p.contains("klikbca") ||
            p.contains("wondr") || p.contains("octomobile") || p.contains("jenius") || p.contains("neobank") ||
            p.contains("motionbank") || p.contains("simobi") || p.contains("jakone") || p.contains("jconnect") ||
            p.contains("tunaiku") || p.contains("shopeepay") || p.contains("astrapay") || p.contains("seabank") ||
            p.contains("superbank") || p.contains("allobank") || p.contains("linebank") || p.contains("revolut")
    }

    private fun matchesLabel(label: String): Boolean {
        if (label.contains("powerbank") || label.contains("power bank") || label.contains("blood bank")) return false

        // Dinamis mendeteksi teks nama aplikasi dari PackageManager
        if (label.contains("bank") || label.contains("perbankan") || label.contains("banking") ||
            label.contains("fintech") || label.contains("dompet") || label.contains("wallet") ||
            label.contains("pembayaran") || label.contains("reksadana") || label.contains("sekuritas") ||
            label.contains("kripto") || label.contains("crypto") || label.contains("pinjaman")
        ) return true

        return label.contains("bca") || label.contains("bri") || label.contains("bni") ||
            label.contains("mandiri") || label.contains("cimb") || label.contains("btn") ||
            label.contains("bsi") || label.contains("danamon") || label.contains("permata") ||
            label.contains("jenius") || label.contains("jago") || label.contains("seabank") ||
            label.contains("livin") || label.contains("brimo") || label.contains("wondr") ||
            label.contains("octo") || label.contains("dana") || label.contains("ovo") ||
            label.contains("gopay") || label.contains("shopeepay") || label.contains("linkaja") ||
            label.contains("neobank") || label.contains("superbank") || label.contains("allo bank")
    }

    fun getInstalledBankingApps(context: Context): List<BankingAppInfo> {
        return runCatching {
            val pm = context.packageManager
            val s = SettingsRepo.current
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            installed.mapNotNull { appInfo ->
                val pkg = appInfo.packageName
                if (pkg == context.packageName || pkg in EXEMPT_PACKAGES) return@mapNotNull null
                val isCustom = pkg in s.customBankingApps
                val isExcluded = pkg in s.bankingAppExclusions
                val isAuto = checkIsBank(context, pkg)
                if (isAuto || isCustom || isExcluded) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    BankingAppInfo(pkg, label, isAuto, isCustom, isExcluded)
                } else null
            }.sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun clearCache() {
        cache.clear()
    }
}
