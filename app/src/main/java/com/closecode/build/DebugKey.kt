package com.closecode.build

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar

/**
 * The app's debug signing key, generated once into the AndroidKeyStore and reused.
 * A self-signed RSA-2048 certificate valid for 30 years — the same shape as a
 * `keytool -genkeypair` debug key, without shelling out to anything.
 */
object DebugKey {
    const val ALIAS = "closecode-debug"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    data class Pair(val key: PrivateKey, val chain: List<X509Certificate>)

    fun ensure(context: Context): Pair {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { entry ->
            val chain = entry.certificateChain.map { it as X509Certificate }
            if (chain.isNotEmpty()) return Pair(entry.privateKey, chain)
        }
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=Closecode Debug"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setKeyValidityStart(start.time)
            .setKeyValidityEnd(end.time)
            .build()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        generator.initialize(spec)
        generator.generateKeyPair()
        val entry = store.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return Pair(entry.privateKey, entry.certificateChain.map { it as X509Certificate })
    }
}
