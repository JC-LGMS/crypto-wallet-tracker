package com.jclg.cryptowallettracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun addressesFollowTheCoinPattern() {
        val btc = Coins.byId("bitcoin")!!
        assertTrue(btc.matchesAddress("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"))
        assertFalse(btc.matchesAddress("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"))
    }
}
