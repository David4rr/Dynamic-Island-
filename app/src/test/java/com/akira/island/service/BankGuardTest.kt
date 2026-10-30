package com.akira.island.service

import android.content.ContextWrapper
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
    }

    @Test
    fun testIndonesianBankingAppsDetected() {
        val indoBanks = listOf(
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
            "com.btpn.dc",
            "com.seabank.id",
            "id.allobank.mobile",
            "id.co.permatabank.mobile",
            "com.danamon.dbank",
            "id.co.bsi.mobile",
            "id.co.btn.mobile",
            "com.panin.mobile",
            "com.bankneo.neobank",
            "id.dana",
            "com.telkom.mwallet",
            "com.shopeepay.id",
            "ovo.id",
            "com.gojek.app"
        )
        for (pkg in indoBanks) {
            assertTrue("Expected Indonesian bank/wallet $pkg to be detected", BankGuard.isBankingApp(context, pkg))
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
            "com.barclays.android.barclaysmobilebanking"
        )
        for (pkg in globalBanks) {
            assertTrue("Expected global bank/wallet $pkg to be detected", BankGuard.isBankingApp(context, pkg))
        }
    }

    @Test
    fun testRegexPatternMatches() {
        val regexBanks = listOf(
            "com.example.mobilebank",
            "net.regional.banking",
            "com.innovative.fintech",
            "org.community.mbanking"
        )
        for (pkg in regexBanks) {
            assertTrue("Expected regex pattern match for $pkg", BankGuard.isBankingApp(context, pkg))
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
        // Second call hits cache
        assertTrue(BankGuard.isBankingApp(context, testPkg))

        BankGuard.clearCache()
        // Repopulate
        assertTrue(BankGuard.isBankingApp(context, testPkg))
    }
}
