package com.closecode.build

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File

/**
 * Gradle's assembleRelease produces an unsigned APK on a project with no signingConfig.
 * CloseCode's keys live in the Android Keystore or encrypted app storage, so they cannot be
 * handed to Gradle as files. Sign after the fact, with the same apksig implementation the
 * custom chain uses, and verify before replacing the artifact.
 */
object ApkPostSigner {

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
            error("The release APK was signed but did not verify.")
        }
        if (!temp.renameTo(apk)) {
            temp.copyTo(apk, overwrite = true)
            temp.delete()
        }
        return apk
    }
}
