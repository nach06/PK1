package com.nacho.kartlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreRideChecksTest {
    private val ok = DeviceSnapshot(80, false, true, true, 255, false)
    private fun state(d: DeviceSnapshot, mount: Boolean = true, id: PreRideChecks.Id) = PreRideChecks.evaluate(d, mount).first { it.id == id }.state

    @Test
    fun everythingFineIsGreen() {
        assertTrue(PreRideChecks.allOk(PreRideChecks.evaluate(ok, true)))
        assertFalse(PreRideChecks.allOk(PreRideChecks.evaluate(ok, false)))
    }

    @Test
    fun eachProblemTurnsItsOwnItemAmber() {
        assertEquals(PreRideChecks.State.WARN, state(ok.copy(batteryPercent = 49), id = PreRideChecks.Id.BATTERY))
        assertEquals(PreRideChecks.State.OK, state(ok.copy(batteryPercent = 10, charging = true), id = PreRideChecks.Id.BATTERY))
        assertEquals(PreRideChecks.State.WARN, state(ok.copy(locationEnabled = false), id = PreRideChecks.Id.LOCATION))
        assertEquals(PreRideChecks.State.WARN, state(ok.copy(locationPermission = false), id = PreRideChecks.Id.PERMISSION))
        assertEquals(PreRideChecks.State.WARN, state(ok.copy(brightness = 40), id = PreRideChecks.Id.BRIGHTNESS))
        assertEquals(PreRideChecks.State.OK, state(ok.copy(brightness = null), id = PreRideChecks.Id.BRIGHTNESS))
        assertEquals(PreRideChecks.State.WARN, state(ok.copy(powerSaveOn = true), id = PreRideChecks.Id.POWER_SAVE))
    }
}
