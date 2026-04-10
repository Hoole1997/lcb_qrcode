package net.corekit.core.utils

import net.corekit.core.ext.autoDecryptIfNeeded
import net.corekit.core.ext.autoEncryptIfNeeded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageScopedCryptoTest {

    private val packageA = "com.demo.app1"
    private val packageB = "com.demo.app2"

    @Test
    fun autoEncryptIfNeeded_should_encrypt_when_plain_text() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""

        val encrypted = plain.autoEncryptIfNeeded(packageA)
        val decrypted = encrypted.autoDecryptIfNeeded(packageA)

        assertTrue(encrypted.isNotEmpty())
        assertNotEquals(plain, encrypted)
        assertEquals(plain, decrypted)
    }

    @Test
    fun autoEncryptIfNeeded_should_keep_cipher_text_unchanged() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""
        val cipher = PackageScopedCrypto.encryptForSharedAssets(plain, packageA)

        val encryptedAgain = cipher.autoEncryptIfNeeded(packageA)
        val decrypted = encryptedAgain.autoDecryptIfNeeded(packageA)

        assertEquals(cipher, encryptedAgain)
        assertEquals(plain, decrypted)
    }

    @Test
    fun autoDecryptIfNeeded_should_return_original_when_plain_text() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""

        val result = plain.autoDecryptIfNeeded(packageA)

        assertEquals(plain, result)
    }

    @Test
    fun autoDecryptIfNeeded_should_decrypt_when_cipher_text() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""
        val cipher = PackageScopedCrypto.encryptForSharedAssets(plain, packageA)

        val result = cipher.autoDecryptIfNeeded(packageA)

        assertTrue(cipher.startsWith("rem_"))
        assertEquals(plain, result)
    }

    @Test
    fun autoDecryptIfNeeded_should_decrypt_when_cipher_text_without_prefix() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""
        val cipherWithPrefix = PackageScopedCrypto.encryptForSharedAssets(plain, packageA)
        val cipherWithoutPrefix = cipherWithPrefix.removePrefix("rem_")

        val result = cipherWithoutPrefix.autoDecryptIfNeeded(packageA)

        assertEquals(plain, result)
    }

    @Test
    fun autoDecryptIfNeeded_should_not_crash_and_should_return_original_when_package_mismatch() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""
        val cipher = PackageScopedCrypto.encrypt(plain, packageA)

        val result = cipher.autoDecryptIfNeeded(packageB)

        assertEquals(cipher, result)
    }

    @Test
    fun encrypt_should_be_deterministic_for_same_package_and_plain_text() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""

        val cipher1 = PackageScopedCrypto.encrypt(plain, packageA)
        val cipher2 = PackageScopedCrypto.encrypt(plain, packageA)

        System.out.println("cipher1=$cipher1")
        System.out.println("cipher2=$cipher2")
        System.out.println("sameCipher=${cipher1 == cipher2}")

        assertEquals(cipher1, cipher2)
    }

    @Test
    fun encrypt_should_generate_different_cipher_for_different_package() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""

        val cipherA = PackageScopedCrypto.encrypt(plain, packageA)
        val cipherB = PackageScopedCrypto.encrypt(plain, packageB)

        System.out.println("packageA=$packageA, cipherA=$cipherA")
        System.out.println("packageB=$packageB, cipherB=$cipherB")

        assertNotEquals(cipherA, cipherB)
        assertEquals(plain, PackageScopedCrypto.decrypt(cipherA, packageA))
        assertEquals(plain, PackageScopedCrypto.decrypt(cipherB, packageB))
    }

    @Test
    fun encrypt_should_generate_different_cipher_for_different_plain_text_in_same_package() {
        val plainA = """{"adConfigJson":{"enabled":true,"ratio":3}}"""
        val plainB = """{"adConfigJson":{"enabled":true,"ratio":4}}"""

        val cipherA = PackageScopedCrypto.encrypt(plainA, packageA)
        val cipherB = PackageScopedCrypto.encrypt(plainB, packageA)

        System.out.println("samePackage=$packageA, plainA=$plainA, cipherA=$cipherA")
        System.out.println("samePackage=$packageA, plainB=$plainB, cipherB=$cipherB")

        assertNotEquals(cipherA, cipherB)
        assertEquals(plainA, PackageScopedCrypto.decrypt(cipherA, packageA))
        assertEquals(plainB, PackageScopedCrypto.decrypt(cipherB, packageA))
    }

    @Test
    fun decrypt_should_work_with_web_generated_ciphertext() {
        val expectedKey = "adConfigJson"
        val expectedValue = """{"ad_strategies":{"fullscreen_native_after_interstitial":3,"show_interstitial_after_app_open_failure":1}}"""
        val packageName = "com.demo.app1"

        val encryptedKeyFromWeb = "rem_VJIVIFKD5WLXMOGV27ITX44ENZSCKWSF6YFCUZGUEB5TLM4F7KYD64EW23TBIRQ5"
        val encryptedValueFromWeb = "rem_ZNZFZY22XDQIUJDYXEHVKJ4UTQNLTZ3IKFX527OSNCN2GKXVP2ETN6ITXUKPOSJE5OAMPFZJHMJPVYO5UHH5JCOB5MAAUMBZ7P44253NKBJTIXYYOEXYETHWRN5ACEIKB4C73COTADMSLT2NRTWTWQC7PD5NKL2LNZUMTOXHEIPF2S6EON5BS5LPKZCWKNUUICHUOEN4O4JCTEEUENTO2"

        val decryptedKey = PackageScopedCrypto.decrypt(encryptedKeyFromWeb, packageName)
        val decryptedValue = PackageScopedCrypto.decrypt(encryptedValueFromWeb, packageName)

        System.out.println("webEncryptedKey=$encryptedKeyFromWeb")
        System.out.println("webEncryptedValue=$encryptedValueFromWeb")
        System.out.println("decryptedKey=$decryptedKey")
        System.out.println("decryptedValue=$decryptedValue")

        assertEquals(expectedKey, decryptedKey)
        assertEquals(expectedValue, decryptedValue)
    }

    @Test
    fun decrypt_should_work_for_all_defined_remote_keys_with_same_package() {
        val packages = listOf(
            "com.pdf.reader.viewer.word.sheet.ppt.reader.document",
            "com.qr.scanner.barcode.reader.app.tool"
        )
        val plainKeys = listOf(
            "rev_adj",
            "rev_fir",
            "shortVideoConfigJson",
            "lockScreenShowConfig",
            "pushConfigJson",
            "pushContentJson",
            "homeFloatDialogContentJson",
            "homeFloatDialogConfigJson",
            "launcher_homePlacementJson",
            "Grouping",
            "adConfigNaturalJson",
            "adConfigPaidJson",
            "adClickProtectionConfig"
        )

        packages.forEach { packageName ->
            System.out.println("verify package=$packageName, keyCount=${plainKeys.size}")
            plainKeys.forEach { plainKey ->
                val cipherText = PackageScopedCrypto.encrypt(plainKey, packageName)
                val decrypted = PackageScopedCrypto.decrypt(cipherText, packageName)
                System.out.println("plainKey=$plainKey, cipherText=$cipherText, decrypted=$decrypted")
                assertEquals(plainKey, decrypted)
            }
        }
    }

    @Test
    fun isEncryptedPayload_should_only_accept_rem_underscore_base32_payload() {
        val plain = """{"adConfigJson":{"enabled":true,"ratio":3}}"""
        val cipher = PackageScopedCrypto.encrypt(plain, packageA)

        assertTrue(PackageScopedCrypto.isEncryptedPayload(cipher))
        assertFalse(PackageScopedCrypto.isEncryptedPayload(cipher.removePrefix("rem_")))
        assertFalse(PackageScopedCrypto.isEncryptedPayload(plain))
    }

    @Test
    fun empty_input_should_not_crash_and_return_safe_defaults() {
        assertEquals("", PackageScopedCrypto.encrypt("", packageA))
        assertEquals("", PackageScopedCrypto.encrypt("abc", ""))
        assertEquals("", PackageScopedCrypto.encrypt("abc", "   "))

        assertEquals("", PackageScopedCrypto.decrypt("", packageA))
        assertEquals("", PackageScopedCrypto.decrypt("remABCDEFG", ""))
        assertEquals("", PackageScopedCrypto.decrypt("not_cipher_text", packageA))

        assertEquals("", PackageScopedCrypto.decryptIfNeeded(null, packageA))
        assertEquals("", PackageScopedCrypto.decryptIfNeeded("", packageA))
        assertEquals("plain_text", PackageScopedCrypto.decryptIfNeeded("plain_text", packageA))
        assertEquals("plain_text", PackageScopedCrypto.decryptIfNeeded("plain_text", ""))

        assertFalse(PackageScopedCrypto.isEncryptedPayload(null))
        assertFalse(PackageScopedCrypto.isEncryptedPayload(""))
        assertFalse(PackageScopedCrypto.isEncryptedPayload("   "))
        assertFalse(PackageScopedCrypto.isEncryptedPayload("rem_***"))
    }

    @Test
    fun auto_extensions_should_not_crash_for_plain_or_invalid_cipher_or_empty_package() {
        val plain = """{"x":1}"""
        val invalidCipher = "remNOT_VALID_*"
        val validCipherWithoutPrefix = PackageScopedCrypto.encrypt(plain, packageA).removePrefix("rem_")

        assertEquals(plain, plain.autoDecryptIfNeeded(packageA))
        assertEquals(invalidCipher, invalidCipher.autoDecryptIfNeeded(packageA))
        assertEquals(plain, validCipherWithoutPrefix.autoDecryptIfNeeded(packageA))
        assertEquals(plain, plain.autoDecryptIfNeeded(""))

        assertEquals("", (null as String?).autoEncryptIfNeeded(packageA))
        assertEquals("", "".autoEncryptIfNeeded(packageA))
        assertEquals(plain, plain.autoEncryptIfNeeded(""))
    }

    @Test
    fun autoDecryptIfNeeded_should_keep_original_for_plain_cipher_mixed_strings() {
        val plain = """{"x":1}"""
        val cipherWithPrefix = PackageScopedCrypto.encrypt(plain, packageA)
        val cipherWithoutPrefix = cipherWithPrefix.removePrefix("rem_")

        val mixedCases = listOf(
            "rem_$plain",
            "${plain}__${cipherWithPrefix}",
            "${cipherWithPrefix}__${plain}",
            "${plain}__${cipherWithoutPrefix}",
            "${cipherWithoutPrefix}__${plain}"
        )

        mixedCases.forEach { source ->
            val extResult = source.autoDecryptIfNeeded(packageA)
            val directResult = PackageScopedCrypto.decryptIfNeeded(source, packageA)
            assertEquals(source, extResult)
            assertEquals(source, directResult)
        }
    }
}
