package com.sour.duoremote.protocol.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceActionValidationTest {

    @Test
    fun deviceIdsRejectPathInjection() {
        assertTrue(UuApiClient.validDeviceId("abc_123-XYZ"))
        assertFalse(UuApiClient.validDeviceId(""))
        assertFalse(UuApiClient.validDeviceId("../other"))
        assertFalse(UuApiClient.validDeviceId("abc/reboot"))
        assertFalse(UuApiClient.validDeviceId("设备"))
        assertFalse(UuApiClient.validDeviceId("a".repeat(129)))
    }
}
