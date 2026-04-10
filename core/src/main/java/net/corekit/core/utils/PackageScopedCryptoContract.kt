package net.corekit.core.utils

/**
 * PackageScopedCrypto 跨端加解密协议定义。
 *
 * 方案：
 * - 主密钥固定。
 * - 按包名派生 AES 密钥。
 * - IV 使用“包名 + 明文”确定性派生（非随机）。
 * - 同一包名 + 同一明文 => 密文固定不变。
 */
internal object PackageScopedCryptoContract {

    /**
     * 主密钥（Master Secret）
     * - 作用：作为 HKDF 的输入密钥材料（IKM）。
     * - 要求：Web 与 Kotlin 必须完全一致。
     */
    const val MASTER_SECRET = "remax2025"

    /**
     * HKDF salt
     * - 作用：增强派生密钥稳定性与域隔离。
     * - 要求：Web 与 Kotlin 必须完全一致。
     */
    const val HKDF_SALT = "remax_sdk_demo_salt"

    const val HKDF_INFO_PREFIX = "pkg:"
    const val IV_HKDF_INFO_PREFIX = "iv:"

    /**
     * HMAC 算法（用于 HKDF-Extract / HKDF-Expand）
     */
    const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * 对称加密算法
     */
    const val AES_ALGORITHM = "AES"

    /**
     * 加密模式：AES-GCM（无填充）
     */
    const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * AES-256 密钥长度（字节）
     */
    const val AES_KEY_LENGTH_BYTES = 32

    /**
     * GCM IV 长度（字节）
     * - 推荐 12 bytes
     */
    const val IV_LENGTH_BYTES = 12

    /**
     * GCM Tag 长度（位）
     */
    const val GCM_TAG_LENGTH_BITS = 128

    const val CIPHER_PAYLOAD_FORMAT = "rem_ + base32(iv + ciphertextWithTag)"
}
