package com.jclg.cryptowallettracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jclg.cryptowallettracker.ui.UnlockResult
import com.jclg.cryptowallettracker.ui.addressKey
import com.jclg.cryptowallettracker.ui.resolveUnlock
import java.io.File
import java.math.BigInteger
import java.nio.file.Files

/** Formats critiques : export chiffré, verrou, import des anciennes sauvegardes, adresses. */
class FormatsTest {

    @Test
    fun encryptedExportRoundTrips() {
        val plain = """[{"c":"bitcoin","a":"bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"}]"""
        val encrypted = ExportCodec.encrypt(plain, "1234")
        assertTrue(ExportCodec.isEncrypted(encrypted))
        assertEquals(plain, ExportCodec.decrypt(encrypted, "1234"))
    }

    @Test
    fun wrongCodeIsRejected() {
        val encrypted = ExportCodec.encrypt("[]", "1234")
        assertThrows(IllegalArgumentException::class.java) {
            ExportCodec.decrypt(encrypted, "4321")
        }
    }

    @Test
    fun lockCodeVerifiesOnlyTheRightPin() {
        val (salt, hash) = LockCode.hash("4821")
        assertTrue(LockCode.verify("4821", salt, hash))
        assertFalse(LockCode.verify("4822", salt, hash))
    }

    @Test
    fun legacyCoinNamesStillImport() {
        assertEquals("ethereum", WalletPayload(c = "ETHEREUM", a = "0x1").toWallet()?.coinId)
        assertEquals("monero", WalletPayload(c = "MONERO", a = "4abc").toWallet()?.coinId)
        assertNull(WalletPayload(c = "inconnu", a = "x").toWallet())
    }

    @Test
    fun decoyCodeIsRecognisedSeparatelyFromTheRealCode() {
        val (salt, hash) = LockCode.hash("1234")
        val (decoySalt, decoyHash) = LockCode.hash("5678")
        val settings = Settings(
            lockType = LockType.PIN,
            lockSalt = salt,
            lockCodeHash = hash,
            decoyCodeSalt = decoySalt,
            decoyCodeHash = decoyHash,
        )
        assertEquals(UnlockResult.REAL, resolveUnlock("1234", settings))
        assertEquals(UnlockResult.DECOY, resolveUnlock("5678", settings))
        assertEquals(UnlockResult.WRONG, resolveUnlock("0000", settings))
    }

    @Test
    fun decoyWalletsUseRealAddressesForTheirCoin() {
        assertEquals(4, Decoy.wallets.size)
        Decoy.wallets.forEach { wallet ->
            val coin = wallet.coin ?: error("crypto inconnue : ${wallet.coinId}")
            assertTrue("${coin.symbol} ${wallet.address}", coin.matchesAddress(wallet.address))
        }
        assertTrue(Decoy.wallets.none(Decoy::isLegacy))
        assertTrue(Decoy.isLegacy(Decoy.wallets.first().copy(address = "bc1q" + "0".repeat(38))))
    }

    @Test
    fun unreadableWalletFileIsSetAsideAndReported() {
        val dir = Files.createTempDirectory("cwt-bad").toFile()
        val store = WalletStore(File(dir, "wallets.json"))
        File(dir, "wallets.json").writeText("{ pas du json")

        assertTrue(store.load().isEmpty())
        assertEquals("wallets.json.bad", store.badCopy)
        assertTrue(File(dir, "wallets.json.bad").exists())

        // Le fichier n'existe plus : plus rien à signaler, et aucune donnée n'est inventée.
        assertTrue(store.load().isEmpty())
        assertNull(store.badCopy)
    }

    @Test
    fun decoyFilesNeverTouchTheRealOnes() {
        val dir = Files.createTempDirectory("cwt").toFile()

        val realWallets = WalletStore(File(dir, "wallets.json"))
        val decoyWallets = WalletStore(File(dir, "decoy-wallets.json"))
        realWallets.save(listOf(Wallet(coinId = "bitcoin", address = "bc1qvrai")))
        decoyWallets.save(Decoy.wallets)
        decoyWallets.save(emptyList())
        assertEquals(1, realWallets.load().size)

        val realSnapshot = SnapshotStore(File(dir, "snapshot.json"))
        val decoySnapshot = SnapshotStore(File(dir, "decoy-snapshot.json"))
        realSnapshot.save(BalanceSnapshot(balances = mapOf("w" to BalanceEntry(1.0, 1))))
        decoySnapshot.save(BalanceSnapshot())
        assertEquals(1, realSnapshot.load().balances.size)
    }

    @Test
    fun weiConvertsToUnits() {
        assertEquals(1.0, BigInteger.TEN.pow(18).units(18), 0.0)
        assertEquals(1.5, BigInteger.valueOf(1_500_000_000).units(9), 0.0)
    }

    @Test
    fun addressesFollowTheCoinPattern() {
        val btc = Coins.byId("bitcoin")!!
        assertTrue(btc.matchesAddress("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"))
        assertFalse(btc.matchesAddress("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"))
    }

    @Test
    fun snapshotMergeKeepsTheNewestBalanceAndQuotes() {
        val dir = Files.createTempDirectory("cwt-merge").toFile()
        val store = SnapshotStore(File(dir, "snapshot.json"))
        store.save(
            BalanceSnapshot(
                balances = mapOf("w" to BalanceEntry(2.0, 2_000)),
                markets = mapOf("bitcoin" to Market(priceUsd = 10.0)),
                quotesUpdatedAt = 2_000,
            ),
        )
        // L'interface écrit un état plus ancien et sans cours : rien de récent ne doit être perdu.
        store.save(
            BalanceSnapshot(
                balances = mapOf("w" to BalanceEntry(1.0, 1_000)),
                rates = Rates(mapOf(Currency.USD to 1.0)),
            ),
        )
        val merged = store.load()
        assertEquals(2.0, merged.balances.getValue("w").amount, 0.0)
        assertEquals(2_000L, merged.quotesUpdatedAt)
        assertEquals(10.0, merged.markets.getValue("bitcoin").priceUsd!!, 0.0)
        assertEquals(1.0, merged.rates.rate(Currency.USD), 0.0)
    }

    @Test
    fun evmAddressesDeduplicateAcrossCaseButBase58DoesNot() {
        val evm = Wallet(coinId = "ethereum", address = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01")
        assertEquals(addressKey(evm), addressKey(evm.copy(address = evm.address.lowercase())))
        val solana = Wallet(coinId = "solana", address = "AbCdEf0123456789")
        assertNotEquals(addressKey(solana), addressKey(solana.copy(address = "abcdef0123456789")))
    }

    @Test
    fun lockAttemptsSlowDownAfterFiveFailures() {
        assertEquals(0L, LockAttempts.delayAfter(5))
        assertEquals(30_000L, LockAttempts.delayAfter(6))
        assertEquals(60_000L, LockAttempts.delayAfter(7))
        assertEquals(15 * 60_000L, LockAttempts.delayAfter(20))
    }
}
