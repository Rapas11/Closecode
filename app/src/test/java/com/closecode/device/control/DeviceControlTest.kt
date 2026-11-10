package com.closecode.device.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlTest {

    @Test
    fun capabilityNamesAreNormalised() {
        assertEquals("ui-automation", ConsentStore.normalize("ui"))
        assertEquals("files", ConsentStore.normalize("storage"))
        assertEquals("shell", ConsentStore.normalize("exec"))
        assertEquals("notifications", ConsentStore.normalize("notify"))
        assertEquals("secrets", ConsentStore.normalize("vault"))
        assertEquals(null, ConsentStore.normalize("root"))
    }

    @Test
    fun sensitivePackagesAreProtectedByDefault() {
        assertTrue(DeviceControl.isProtected("com.example.bank"))
        assertTrue(DeviceControl.isProtected("com.auth.authenticator"))
        assertTrue(DeviceControl.isProtected("com.example.passwordmanager"))
        assertFalse(DeviceControl.isProtected("com.closecode"))
    }
}
