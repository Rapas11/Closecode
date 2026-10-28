package com.closecode.ai

import android.content.Context
import com.closecode.debug.DebugLog
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64

internal object OpenCodeCa {

    fun file(context: Context): File = File(OpenCodeBinary.installDir(context), "ca.pem")

    fun ensure(context: Context): File {
        val target = file(context)
        if (target.isFile && target.length() > 0L) return target
        val certificates = runCatching {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null, null) }
            val aliases = store.aliases()
            buildList {
                while (aliases.hasMoreElements()) {
                    val certificate = store.getCertificate(aliases.nextElement())
                    if (certificate is X509Certificate) add(certificate.encoded)
                }
            }
        }.onFailure {
            DebugLog.crumb("AICO", "CA export failed: ${it.javaClass.simpleName}")
        }.getOrDefault(emptyList())
        if (certificates.isEmpty()) return target
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.new")
        temp.writeText(pem(certificates))
        temp.setReadable(true, false)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    fun environment(context: Context): Map<String, String> {
        val target = ensure(context)
        if (!target.isFile || target.length() == 0L) return emptyMap()
        return environment(target.absolutePath)
    }

    internal fun environment(caFile: String): Map<String, String> = mapOf(
        "SSL_CERT_FILE" to caFile,
        "NODE_EXTRA_CA_CERTS" to caFile,
        "CURL_CA_BUNDLE" to caFile,
        "GIT_SSL_CAINFO" to caFile,
        "NODE_USE_SYSTEM_CA" to "1",
    )

    internal fun pem(certificates: List<ByteArray>): String {
        if (certificates.isEmpty()) return ""
        val encoder = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.US_ASCII))
        return buildString {
            certificates.forEach { certificate ->
                appendLine("-----BEGIN CERTIFICATE-----")
                appendLine(encoder.encodeToString(certificate))
                appendLine("-----END CERTIFICATE-----")
            }
        }
    }
}
