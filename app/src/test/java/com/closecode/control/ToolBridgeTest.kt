package com.closecode.control

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolBridgeTest {

    @Test
    fun kotlincGetsTheAndroidDefaults() {
        val home = File("/tmp/kotlin-home")

        assertEquals(
            listOf("-no-jdk", "-kotlin-home", home.absolutePath, "Main.kt"),
            ToolBridge.kotlincArgs(listOf("Main.kt"), home),
        )
    }

    @Test
    fun explicitFlagsAreNotDuplicated() {
        val home = File("/tmp/kotlin-home")

        assertEquals(
            listOf("-no-jdk", "-kotlin-home", "/custom", "Main.kt"),
            ToolBridge.kotlincArgs(
                listOf("-no-jdk", "-kotlin-home", "/custom", "Main.kt"),
                home,
            ),
        )
    }

    @Test
    fun noHomeMeansNoHomeFlag() {
        assertEquals(
            listOf("-no-jdk", "Main.kt"),
            ToolBridge.kotlincArgs(listOf("Main.kt"), null),
        )
    }
}
