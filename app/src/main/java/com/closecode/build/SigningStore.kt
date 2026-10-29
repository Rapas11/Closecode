package com.closecode.build

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar

enum class SigningKeySource { Debug, Imported, Generated }

/** What the Signing keys page shows about one key, without ever exposing private material. */
data class SigningKeyInfo(
    val id: String,
    val label: String,
    val source: SigningKeySource,
    val subject: String,
    val expiresAt: Long?,
    val exportable: Boolean,
)

/**
 * The signing keys a build can use.
 *
 * The debug key is generated in the Android Keystore and reset on demand. Imported PKCS#12/JKS
 * keys live in app-private storage with their password in EncryptedSharedPreferences; a phone
 * whose Keystore is broken refuses the import rather than writing the password in the clear. A
 * generated release key is also device-bound: it can sign an APK, but it cannot be exported for
 * Play Store upload. The selected key is remembered separately from [Settings] because signing
 * material is not a preference.
 */
class SigningStore(private val context: Context) {

    private val dir = File(context.filesDir, "signing")
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secure: SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    fun selectedId(): String {
        val id = prefs.getString(KEY_SELECTED, DEBUG_ID).orEmpty()
        return if (id == DEBUG_ID || id == GENERATED_ID || importedFile(id) != null) {
            id
        } else {
            DEBUG_ID
        }
    }

    fun select(id: String) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun debugInfo(): SigningKeyInfo {
        val pair = runCatching { DebugKey.ensure(context) }.getOrNull()
        val cert = pair?.chain?.firstOrNull()
        return SigningKeyInfo(
            id = DEBUG_ID,
            label = "Debug key",
            source = SigningKeySource.Debug,
            subject = cert?.subjectX500Principal?.name ?: "Not generated",
            expiresAt = cert?.notAfter?.time,
            exportable = false,
        )
    }

    fun importedKeys(): List<SigningKeyInfo> = dir.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(KEYSTORE_SUFFIX) }
        .mapNotNull { file -> runCatching { infoFor(file.nameWithoutExtension) }.getOrNull() }
        .sortedBy { it.label.lowercase() }

    fun generatedInfo(): SigningKeyInfo? = runCatching {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = store.getEntry(GENERATED_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: return null
        val cert = entry.certificate as? X509Certificate
        SigningKeyInfo(
            id = GENERATED_ID,
            label = "Generated release key",
            source = SigningKeySource.Generated,
            subject = cert?.subjectX500Principal?.name ?: "Closecode Release",
            expiresAt = cert?.notAfter?.time,
            exportable = false,
        )
    }.getOrNull()

    fun allKeys(): List<SigningKeyInfo> = buildList {
        add(debugInfo())
        addAll(importedKeys())
        generatedInfo()?.let { add(it) }
    }

    fun identity(): SigningIdentity {
        val id = selectedId()
        return when {
            id == DEBUG_ID -> DebugKey.ensure(context).let {
                SigningIdentity(it.key, it.chain)
            }
            id == GENERATED_ID -> generatedIdentity()
            else -> importedIdentity(id)
        }
    }

    fun importKey(
        bytes: ByteArray,
        originalName: String,
        alias: String,
        password: CharArray,
    ): Result<SigningKeyInfo> = runCatching {
        val securePrefs = secure
            ?: throw IllegalStateException("This phone cannot store signing passwords securely.")
        val base = alias.trim().ifBlank {
            originalName.substringBeforeLast('.').ifBlank { "imported" }
        }
        val safe = base.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .ifBlank { "imported" }
        val id = "imported-$safe"
        val file = File(dir, "$id$KEYSTORE_SUFFIX")
        dir.mkdirs()
        val loaded = load(bytes, password)
        val entryAlias = loaded.first
        val entry = loaded.second
        file.writeBytes(bytes)
        securePrefs.edit().putString(passwordKey(id), String(password)).apply()
        prefs.edit()
            .putString(labelKey(id), base)
            .putString(aliasKey(id), entryAlias)
            .apply()
        val cert = entry.certificate as X509Certificate
        SigningKeyInfo(
            id = id,
            label = base,
            source = SigningKeySource.Imported,
            subject = cert.subjectX500Principal.name,
            expiresAt = cert.notAfter.time,
            exportable = true,
        )
    }

    fun deleteImported(id: String): Boolean {
        if (id == DEBUG_ID || id == GENERATED_ID) return false
        importedFile(id)?.delete()
        secure?.edit()?.remove(passwordKey(id))?.apply()
        prefs.edit()
            .remove(labelKey(id))
            .remove(aliasKey(id))
            .apply()
        if (selectedId() == id) select(DEBUG_ID)
        return true
    }

    fun resetDebug(): Result<Unit> = runCatching {
        DebugKey.delete(context)
        DebugKey.ensure(context)
        select(DEBUG_ID)
    }

    fun generateRelease(): Result<SigningKeyInfo> = runCatching {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(GENERATED_ALIAS)) {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            val spec = KeyGenParameterSpec.Builder(
                GENERATED_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(
                    javax.security.auth.x500.X500Principal("CN=Closecode Release"),
                )
                .setCertificateSerialNumber(BigInteger.valueOf(2))
                .setKeyValidityStart(start.time)
                .setKeyValidityEnd(end.time)
                .build()
            KeyPairGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
                .apply { initialize(spec) }
                .generateKeyPair()
        }
        select(GENERATED_ID)
        generatedInfo() ?: error("The release key was not created.")
    }

    fun deleteGenerated(): Boolean = runCatching {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(GENERATED_ALIAS)) store.deleteEntry(GENERATED_ALIAS)
        if (selectedId() == GENERATED_ID) select(DEBUG_ID)
        true
    }.getOrDefault(false)

    private fun importedFile(id: String): File? =
        File(dir, "$id$KEYSTORE_SUFFIX").takeIf { it.isFile }

    private fun infoFor(id: String): SigningKeyInfo {
        val file = importedFile(id) ?: error("Missing keystore")
        val password = secure?.getString(passwordKey(id), null)
            ?: error("Missing password")
        val loaded = load(file.readBytes(), password.toCharArray())
        val cert = loaded.second.certificate as X509Certificate
        return SigningKeyInfo(
            id = id,
            label = prefs.getString(labelKey(id), id).orEmpty().ifBlank { id },
            source = SigningKeySource.Imported,
            subject = cert.subjectX500Principal.name,
            expiresAt = cert.notAfter.time,
            exportable = true,
        )
    }

    private fun importedIdentity(id: String): SigningIdentity {
        val file = importedFile(id) ?: error("The selected signing key is missing.")
        val password = secure?.getString(passwordKey(id), null)
            ?: error("The selected signing key has no stored password.")
        val loaded = load(file.readBytes(), password.toCharArray())
        return SigningIdentity(loaded.second.privateKey, listOf(loaded.second.certificate as X509Certificate))
    }

    private fun generatedIdentity(): SigningIdentity {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = store.getEntry(GENERATED_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("The generated release key is missing.")
        return SigningIdentity(
            entry.privateKey,
            entry.certificateChain.map { it as X509Certificate },
        )
    }

    private fun load(bytes: ByteArray, password: CharArray): Pair<String, KeyStore.PrivateKeyEntry> {
        val errors = mutableListOf<String>()
        for (type in listOf("PKCS12", "JKS")) {
            val store = runCatching { KeyStore.getInstance(type) }.getOrNull() ?: continue
            val ok = runCatching { store.load(ByteArrayInputStream(bytes), password) }.isSuccess
            if (!ok) {
                errors += type
                continue
            }
            val alias = store.aliases().asSequence()
                .firstOrNull { store.isKeyEntry(it) }
                ?: continue
            val entry = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: continue
            return alias to entry
        }
        throw IllegalArgumentException(
            "The keystore could not be opened with that password (tried ${errors.joinToString()}).",
        )
    }

    private fun passwordKey(id: String) = "password_$id"
    private fun labelKey(id: String) = "label_$id"
    private fun aliasKey(id: String) = "alias_$id"

    companion object {
        const val DEBUG_ID = "debug"
        const val GENERATED_ID = "generated-release"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GENERATED_ALIAS = "closecode-release"
        private const val PREFS = "closecode_signing"
        private const val SECURE_PREFS = "closecode_signing_secure"
        private const val KEY_SELECTED = "selected"
        private const val KEYSTORE_SUFFIX = ".keystore"
    }
}
