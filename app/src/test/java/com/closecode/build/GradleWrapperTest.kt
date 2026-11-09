package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleWrapperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theDistributionUrlNamesTheVersion() {
        val file = File(tmp.root, "gradle/wrapper/gradle-wrapper.properties")
        file.parentFile.mkdirs()
        file.writeText(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.12-bin.zip\n",
        )
        assertEquals("8.12", GradleWrapper.version(tmp.root))
    }

    @Test
    fun aMissingWrapperIsNotAVersion() {
        assertNull(GradleWrapper.version(tmp.root))
    }
}
