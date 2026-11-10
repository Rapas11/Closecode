package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackIndexTest {

    @Test
    fun relativePackUrlsResolveAgainstTheIndex() {
        val index = """
            {"packs":[
              {"id":"lucide","name":"Lucide","count":2322,"bytes":527564,
               "url":"lucide.json","style":"stroke","licence":"ISC"}
            ]}
        """.trimIndent()
        val packs = parsePackIndex(
            index,
            "https://rapas11.github.io/iconpacks/packs.json",
        )
        assertEquals(1, packs.size)
        val pack = packs.first()
        assertEquals("lucide", pack.id)
        assertEquals("Lucide", pack.name)
        assertEquals("2322", pack.countText)
        assertEquals("515 KB", pack.sizeText)
        assertEquals("stroke", pack.style)
        assertEquals(
            "https://rapas11.github.io/iconpacks/lucide.json",
            pack.url,
        )
    }

    @Test
    fun absolutePackUrlsAreKept() {
        val packs = parsePackIndex(
            """{"packs":[{"id":"x","name":"X","url":"https://cdn.example/x.json"}]}""",
            "https://rapas11.github.io/iconpacks/packs.json",
        )
        assertEquals("https://cdn.example/x.json", packs.single().url)
    }

    @Test
    fun aBareListIndexIsStillReadable() {
        val packs = parsePackIndex(
            """[{"id":"m","name":"M","count":3,"bytes":100}]""",
            "https://example.test/packs.json",
        )
        assertEquals("https://example.test/m.json", packs.single().url)
        assertEquals("100 B", packs.single().sizeText)
    }

    @Test
    fun byteFormattingIsStable() {
        assertEquals("unknown", formatPackBytes(0))
        assertEquals("512 B", formatPackBytes(512))
        assertEquals("2 KB", formatPackBytes(2048))
        assertTrue(formatPackBytes(3_000_000).endsWith("MB"))
    }
}
