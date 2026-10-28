package com.closecode.tools

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebArchiveTest {

    @Test
    fun readsTheDataTarFromAnArArchive() {
        val payload = "tar bytes".toByteArray()
        val deb = ar(
            "debian-binary" to "2.0\n".toByteArray(),
            "data.tar.gz" to gzip(payload),
        )

        val read = DebArchive.dataTar(deb).use { it.readBytes() }

        assertEquals("tar bytes", String(read))
    }

    @Test
    fun rejectsAFileThatIsNotAnArArchive() {
        assertThrows(IllegalArgumentException::class.java) {
            DebArchive.findMember("not an archive".toByteArray(), "data.tar")
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun ar(vararg members: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("!<arch>\n".toByteArray())
        members.forEach { (name, data) ->
            val header = ByteArray(60) { ' '.code.toByte() }
            name.toByteArray().copyInto(header, 0)
            "0".toByteArray().copyInto(header, 16)
            "0".toByteArray().copyInto(header, 28)
            "0".toByteArray().copyInto(header, 34)
            "100644".toByteArray().copyInto(header, 40)
            data.size.toString().toByteArray().copyInto(header, 48)
            "`\n".toByteArray().copyInto(header, 58)
            out.write(header)
            out.write(data)
            if (data.size % 2 == 1) out.write('\n'.code)
        }
        return out.toByteArray()
    }
}
