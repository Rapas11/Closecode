package com.closecode.build

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File

/**
 * Gradle's assembleRelease produces an unsigned APK on a project with no signingConfig, and its
 * debug APK is signed with a different key than CloseCode's custom engine. CloseCode's keys live
 * in the Android Keystore or encrypted app storage, so they cannot be handed to Gradle as files.
 * Sign after the fact, with the same apksig implementation the custom chain uses, so an APK from
 * either engine has the same certificate (P0-7), and verify before replacing the artifact.
 */
object ApkPostSigner {

    /**
     * True when the APK carries a signature Android accepts.
     *
     * A project with no release signing configuration produces `app-release-unsigned.apk`,
     * which Android refuses to install. Telling "unsigned" from "signed" is what lets the
     * release path sign it instead of reporting a success that cannot be installed.
     */
    fun isVerified(apk: File, minSdk: Int): Boolean = runCatching {
        ApkVerifier.Builder(apk)
            .setMinCheckedPlatformVersion(minSdk)
            .build()
            .verify()
            .isVerified
    }.getOrDefault(false)

    /**
     * The certificate an APK really carries, read out of the file.
     *
     * A command that reports a certificate has to report the one the file has, not the one
     * it intended to write. This reads the signer back after signing, so the answer and the
     * file cannot disagree.
     */
    fun certificateIn(apk: File, minSdk: Int): String? {
        val certificate = runCatching {
            ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(minSdk)
                .build()
                .verify()
                .signerCertificates
                ?.firstOrNull()
        }.getOrNull() ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        return "${certificate.subjectX500Principal.name} ($digest)"
    }

    /**
     * Signs the APK with the selected key and gives it a name that says so.
     *
     * A file called `app-release-unsigned.apk` must never be handed to the user as a
     * finished build: it is renamed once it is signed. The signature it had, if any, is
     * replaced — one key signs every build path.
     */
    fun signAndRename(apk: File, identity: SigningIdentity, minSdk: Int): File {
        sign(apk, identity, minSdk)
        val renamed = apk.name.replace("-unsigned", "")
        if (renamed != apk.name) {
            val target = File(apk.parentFile, renamed)
            if (!apk.renameTo(target)) {
                apk.copyTo(target, overwrite = true)
                apk.delete()
            }
            return target
        }
        return apk
    }

    /**
     * Signs the APK when it has no signature, and gives it a name that says so.
     *
     * A file called `app-release-unsigned.apk` must never be handed to the user as a
     * finished build: it is renamed once it is signed.
     */
    fun signIfUnsigned(apk: File, identity: SigningIdentity, minSdk: Int): File {
        if (isVerified(apk, minSdk)) return apk
        sign(apk, identity, minSdk)
        val renamed = apk.name.replace("-unsigned", "")
        if (renamed != apk.name) {
            val target = File(apk.parentFile, renamed)
            if (!apk.renameTo(target)) {
                apk.copyTo(target, overwrite = true)
                apk.delete()
            }
            return target
        }
        return apk
    }

    /** The key that signed an APK, the way `apksigner --print-certs` prints it. */
    fun describe(identity: SigningIdentity): String {
        val certificate = identity.chain.firstOrNull() ?: return "unknown"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        return "${certificate.subjectX500Principal.name} ($digest)"
    }

    fun sign(apk: File, identity: SigningIdentity, minSdk: Int, label: String = "closecode"): File {
        val temp = File(apk.parentFile, "${apk.name}.signed")
        temp.delete()
        val signer = ApkSigner.SignerConfig.Builder(label, identity.key, identity.chain).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(apk)
            .setOutputApk(temp)
            .setMinSdkVersion(minSdk)
            .setV1SigningEnabled(minSdk < 24)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
        val verification = ApkVerifier.Builder(temp)
            .setMinCheckedPlatformVersion(minSdk)
            .build()
            .verify()
        if (!verification.isVerified) {
            temp.delete()
            error("The APK was signed but did not verify.")
        }
        if (!temp.renameTo(apk)) {
            temp.copyTo(apk, overwrite = true)
            temp.delete()
        }
        return apk
    }
}
