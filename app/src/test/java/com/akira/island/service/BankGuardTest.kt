package com.akira.island.service

import android.content.ContextWrapper
import com.akira.island.data.SettingsRepo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BankGuardTest {

    private class FakeContext(private val pkg: String = "com.akira.island") : ContextWrapper(null) {
        override fun getPackageName(): String = pkg
    }

    private val context = FakeContext()

    @Before
    fun setUp() {
        BankGuard.clearCache()
        SettingsRepo.update {
            copy(
                customBankingApps = emptySet(),
                bankingAppExclusions = emptySet()
            )
        }
    }

    @Test
    fun testSeaBankAndIndonesianBanksDetected() {
        val seaBankAndIndoBanks = listOf(
            // SeaBank variations (Indonesia, PH, VN)
            "id.co.bankbkemobile.digitalbank",
            "ph.seabank.seabank",
            "vn.com.seabank.mb1",

            // BCA
            "com.bca",
            "id.co.bca.mybca",
            "com.bca.klikbca",
            "com.bcas.mobile",

            // Mandiri, BRI, BNI
            "id.bmri.livin",
            "id.co.bankmandiri.mandirionline",
            "id.co.bri.brimo",
            "id.co.bni.wondr",
            "src.com.bni",

            // Private & Digital Banks
            "id.co.cimbniaga.octomobile",
            "com.jago.digitalbanking",
            "com.btpn.dc", // Jenius
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

            // BPDs
            "id.co.bankbjb.digi",
            "com.bankdki.jakone",
            "com.bankjatim.jconnect",
            "com.bankjateng.bima",
            "com.banknagari.ollin",
            "id.co.bpddiy.mobile",
            "id.co.banksumut.mobile",
            "id.co.bankkalbar.mobile",

            // Wallets & Fintech
            "id.dana",
            "com.telkom.mwallet",
            "com.shopeepay.id",
            "ovo.id",
            "com.gojek.app",
            "com.gopay.wallet",
            "com.astrapay.android",
            "com.kredivo",
            "com.akulaku"
        )
        for (pkg in seaBankAndIndoBanks) {
            assertTrue("Expected bank/wallet $pkg to be detected", BankGuard.isBankingApp(context, pkg))
        }
    }

    @Test
    fun testGlobalBankingAppsDetected() {
        val globalBanks = listOf(
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
            "com.voyagerinnovation.paymaya"
        )
        for (pkg in globalBanks) {
            assertTrue("Expected global bank/wallet $pkg to be detected", BankGuard.isBankingApp(context, pkg))
        }
    }

    @Test
    fun testDynamicPatternMatches() {
        val dynamicBanks = listOf(
            "com.example.mobilebank",
            "net.regional.banking",
            "com.innovative.fintech",
            "org.community.mbanking",
            "com.unknown.digitalbank",
            "id.co.randomruralbank.app",
            "com.custom.ewallet"
        )
        for (pkg in dynamicBanks) {
            assertTrue("Expected dynamic pattern match for $pkg", BankGuard.isBankingApp(context, pkg))
        }
    }

    @Test
    fun testNonBankingAppsNotDetected() {
        val nonBanks = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.spotify.music",
            "com.google.android.youtube",
            "com.android.chrome",
            "com.instagram.android",
            "com.google.android.apps.maps"
        )
        for (pkg in nonBanks) {
            assertFalse("Expected non-banking app $pkg to NOT be detected", BankGuard.isBankingApp(context, pkg))
        }
    }

    @Test
    fun testUserCustomBankingAppsAndExclusions() {
        val customApp = "com.obscure.cooperative"
        assertFalse(BankGuard.isBankingApp(context, customApp))

        // Add custom app
        SettingsRepo.update { copy(customBankingApps = setOf(customApp)) }
        BankGuard.clearCache()
        assertTrue("Custom app must be detected when added", BankGuard.isBankingApp(context, customApp))

        // Exclude a bank
        val seaBank = "id.co.bankbkemobile.digitalbank"
        assertTrue(BankGuard.isBankingApp(context, seaBank))

        SettingsRepo.update { copy(bankingAppExclusions = setOf(seaBank)) }
        BankGuard.clearCache()
        assertFalse("Excluded bank must return false", BankGuard.isBankingApp(context, seaBank))
    }

    @Test
    fun testBoundaryAndAdversarialInputs() {
        assertFalse("Null package must return false", BankGuard.isBankingApp(context, null))
        assertFalse("Empty package must return false", BankGuard.isBankingApp(context, ""))
        assertFalse("Whitespace package must return false", BankGuard.isBankingApp(context, "   "))
        assertFalse("Self package must return false", BankGuard.isBankingApp(context, "com.akira.island"))

        // Adversarial string injections
        assertFalse(BankGuard.isBankingApp(context, "com.whatsapp\u0000payload"))
        assertFalse(BankGuard.isBankingApp(context, "'; DROP TABLE apps; --"))
        assertFalse(BankGuard.isBankingApp(context, "<script>alert(1)</script>"))
        assertFalse(BankGuard.isBankingApp(context, "A".repeat(1024)))
    }

    @Test
    fun testCachingAndClearCache() {
        val testPkg = "id.co.bri.brimo"
        assertTrue(BankGuard.isBankingApp(context, testPkg))
        assertTrue(BankGuard.isBankingApp(context, testPkg))

        BankGuard.clearCache()
        assertTrue(BankGuard.isBankingApp(context, testPkg))
    }
}
