package net.corekit.core.ext

import net.corekit.core.utils.PackageScopedCrypto

/**
 * 可空字符串自动解密扩展：
 * - null -> ""
 * - 密文 -> 解密后明文
 * - 非密文 -> 原值
 */
fun String?.autoDecryptIfNeeded(packageName: String): String {
    return PackageScopedCrypto.decryptIfNeeded(this, packageName)
}

/**
 * 可空字符串自动加密扩展：
 * - null -> ""
 * - 明文 -> 加密后密文
 * - 密文 -> 原值（避免重复加密）
 */
fun String?.autoEncryptIfNeeded(packageName: String): String {
    if (this.isNullOrBlank()) return this.orEmpty()
    if (PackageScopedCrypto.isEncryptedPayload(this)) return this

    val encrypted = PackageScopedCrypto.encrypt(this, packageName)
    return if (encrypted.isNotEmpty()) encrypted else this
}
