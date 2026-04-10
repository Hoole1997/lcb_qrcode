package net.corekit.core.utils

import net.corekit.core.log.CoreLogger
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PackageScopedCrypto {
    private const val REMOTE_PREFIX = "rem_"
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val base32Regex = Regex("^[A-Z2-7]+$", RegexOption.IGNORE_CASE)
    private val base32DecodeTable = IntArray(128).apply {
        fill(-1)
        BASE32_ALPHABET.forEachIndexed { index, character ->
            this[character.code] = index
        }
    }
    private val hkdfPrk: ByteArray by lazy {
        val ikm = PackageScopedCryptoContract.MASTER_SECRET.toByteArray(Charsets.UTF_8)
        val salt = PackageScopedCryptoContract.HKDF_SALT.toByteArray(Charsets.UTF_8)
        hmacSha256(salt, ikm)
    }

    fun encrypt(plainText: String, packageName: String): String {
        if (plainText.isEmpty()) return ""
        if (packageName.isBlank()) return ""

        return runCatching {
            encryptWithPackageScope(plainText, packageName.trim())
        }.getOrElse { throwable ->
            safeLogError("PackageScopedCrypto encrypt failed", throwable)
            ""
        }
    }

    fun decrypt(cipherText: String, packageName: String): String {
        if (cipherText.isEmpty()) return ""
        if (packageName.isBlank()) return ""

        return runCatching {
            decryptWithPackageScope(normalizeCipherText(cipherText), packageName.trim())
        }.getOrElse { throwable ->
            safeLogError("PackageScopedCrypto decrypt failed", throwable)
            ""
        }
    }

    /**
     * SDK 本地资源使用固定包名作用域加密
     */
    fun encryptForSharedAssets(plainText: String, fixedPackageName: String): String {
        return encrypt(plainText, fixedPackageName)
    }

    /**
     * 自动判断是否密文，并在需要时自动解密。
     * - 明文：直接原样返回
     * - 密文：按 packageName 派生密钥解密
     */
    fun decryptIfNeeded(source: String?, packageName: String): String {
        if (source.isNullOrBlank()) return source.orEmpty()
        if (packageName.isBlank()) return source

        if (!isDecryptCandidate(source)) {
            return source
        }

        val plain = decrypt(source, packageName)
        return if (plain.isNotEmpty()) plain else source
    }

    fun isEncryptedPayload(source: String?): Boolean {
        if (source.isNullOrBlank()) return false

        val compact = source.replace("\\s+".toRegex(), "")
        if (!compact.startsWith(REMOTE_PREFIX) || compact.length <= REMOTE_PREFIX.length) {
            return false
        }

        val normalized = compact.removePrefix(REMOTE_PREFIX)
        if (!base32Regex.matches(normalized)) {
            return false
        }
        if (normalized.length <= minimumBase32Length()) {
            return false
        }

        val decoded = decodeBase32OrNull(normalized) ?: return false
        return decoded.size > PackageScopedCryptoContract.IV_LENGTH_BYTES
    }

    /**
     * 解密候选判断：
     * - 允许 rem_ 前缀
     * - 也允许无前缀（兼容远端只存 payload 的场景）
     */
    private fun isDecryptCandidate(source: String): Boolean {
        val compact = source.replace("\\s+".toRegex(), "")
        val normalized = if (compact.startsWith(REMOTE_PREFIX) && compact.length > REMOTE_PREFIX.length) {
            compact.removePrefix(REMOTE_PREFIX)
        } else {
            compact
        }

        if (!base32Regex.matches(normalized)) {
            return false
        }
        if (normalized.length <= minimumBase32Length()) {
            return false
        }

        val decoded = decodeBase32OrNull(normalized) ?: return false
        return decoded.size > PackageScopedCryptoContract.IV_LENGTH_BYTES
    }

    private fun normalizeCipherText(source: String): String {
        val normalized = source.replace("\\s+".toRegex(), "")
        return if (normalized.startsWith(REMOTE_PREFIX) && normalized.length > REMOTE_PREFIX.length) {
            normalized.removePrefix(REMOTE_PREFIX)
        } else {
            normalized
        }
    }

    private fun encryptWithPackageScope(plainText: String, packageName: String): String {
        return runCatching {
            val secretKey = derivePackageSecretKey(packageName)
            val iv = deriveDeterministicIv(packageName, plainText)

            val cipher = Cipher.getInstance(PackageScopedCryptoContract.AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(PackageScopedCryptoContract.GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val merged = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, merged, 0, iv.size)
            System.arraycopy(cipherBytes, 0, merged, iv.size, cipherBytes.size)

            REMOTE_PREFIX + encodeBase32(merged)
        }.getOrElse { throwable ->
            safeLogError("PackageScopedCrypto encrypt with package scope failed", throwable)
            ""
        }
    }

    private fun decryptWithPackageScope(cipherText: String, packageName: String): String {
        return runCatching {
            val secretKey = derivePackageSecretKey(packageName)
            val merged = decodeBase32(cipherText)
            require(merged.size > PackageScopedCryptoContract.IV_LENGTH_BYTES) { "Cipher text payload is invalid." }

            val iv = merged.copyOfRange(0, PackageScopedCryptoContract.IV_LENGTH_BYTES)
            val cipherBytes = merged.copyOfRange(PackageScopedCryptoContract.IV_LENGTH_BYTES, merged.size)

            val cipher = Cipher.getInstance(PackageScopedCryptoContract.AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(PackageScopedCryptoContract.GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        }.getOrElse { throwable ->
            safeLogError("PackageScopedCrypto decrypt with package scope failed", throwable)
            ""
        }
    }

    private fun derivePackageSecretKey(packageName: String): SecretKeySpec {
        val info = (PackageScopedCryptoContract.HKDF_INFO_PREFIX + packageName).toByteArray(Charsets.UTF_8)
        val okm = hkdfExpand(hkdfPrk, info, PackageScopedCryptoContract.AES_KEY_LENGTH_BYTES)
        return SecretKeySpec(okm, PackageScopedCryptoContract.AES_ALGORITHM)
    }

    private fun derivePackageIvKey(packageName: String): ByteArray {
        val info = (PackageScopedCryptoContract.IV_HKDF_INFO_PREFIX + packageName).toByteArray(Charsets.UTF_8)
        return hkdfExpand(hkdfPrk, info, PackageScopedCryptoContract.AES_KEY_LENGTH_BYTES)
    }

    private fun deriveDeterministicIv(packageName: String, plainText: String): ByteArray {
        val ivKey = derivePackageIvKey(packageName)
        val ivMaterial = hmacSha256(ivKey, plainText.toByteArray(Charsets.UTF_8))
        return ivMaterial.copyOf(PackageScopedCryptoContract.IV_LENGTH_BYTES)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(PackageScopedCryptoContract.HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, PackageScopedCryptoContract.HMAC_ALGORITHM))

        val input = ByteArray(info.size + 1)
        System.arraycopy(info, 0, input, 0, info.size)
        input[input.lastIndex] = 0x01

        return mac.doFinal(input).copyOf(length)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(PackageScopedCryptoContract.HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, PackageScopedCryptoContract.HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    private fun minimumBase32Length(): Int {
        val minimumBytes = PackageScopedCryptoContract.IV_LENGTH_BYTES + 1
        return (minimumBytes * 8 + 4) / 5
    }

    private fun encodeBase32(source: ByteArray): String {
        if (source.isEmpty()) return ""

        val encoded = StringBuilder((source.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        source.forEach { byteValue ->
            buffer = (buffer shl 8) or (byteValue.toInt() and 0xFF)
            bitsLeft += 8

            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                encoded.append(BASE32_ALPHABET[index])
                bitsLeft -= 5
            }

            if (bitsLeft > 0) {
                buffer = buffer and ((1 shl bitsLeft) - 1)
            } else {
                buffer = 0
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            encoded.append(BASE32_ALPHABET[index])
        }

        return encoded.toString()
    }

    private fun decodeBase32(source: String): ByteArray {
        return requireNotNull(decodeBase32OrNull(source)) { "Cipher text payload is invalid." }
    }

    private fun decodeBase32OrNull(source: String): ByteArray? {
        if (source.isEmpty()) return ByteArray(0)

        val normalized = source.uppercase(Locale.US)
        val expectedSize = (normalized.length * 5) / 8
        val output = ByteArray(expectedSize)

        var outputIndex = 0
        var buffer = 0
        var bitsLeft = 0

        normalized.forEach { character ->
            val codePoint = character.code
            if (codePoint >= base32DecodeTable.size) {
                return null
            }

            val decodedValue = base32DecodeTable[codePoint]
            if (decodedValue < 0) {
                return null
            }

            buffer = (buffer shl 5) or decodedValue
            bitsLeft += 5

            while (bitsLeft >= 8) {
                output[outputIndex++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }

            if (bitsLeft > 0) {
                buffer = buffer and ((1 shl bitsLeft) - 1)
            } else {
                buffer = 0
            }
        }

        return if (outputIndex == output.size) output else output.copyOf(outputIndex)
    }

    private fun safeLogError(message: String, throwable: Throwable? = null) {
        runCatching {
            CoreLogger.e(message, throwable)
        }
    }
}
