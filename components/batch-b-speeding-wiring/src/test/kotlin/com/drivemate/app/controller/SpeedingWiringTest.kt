package com.drivemate.app.controller
import org.junit.Assert.*
import org.junit.Test
import com.drivemate.app.controller.SpeedingWiringHarness.SoundMode.*

class SpeedingWiringTest {
    private val h = SpeedingWiringHarness()
    @Test fun controllerMarksOnlyPolicyOwnedSpeechForHistoryBypass() { h.sample(); assertTrue(h.policyOwned()) }
    @Test fun fullRoutesImmediateWarningToSpeech() { assertEquals(listOf("speech:Speed limit 50:true"),h.sample()) }
    @Test fun alertsRoutesOnlyToTone() { assertEquals(listOf("tone:true"),h.sample(sound=ALERTS)) }
    @Test fun mutedHasNoSpeechOrTone() { assertTrue(h.sample(sound=OFF).isEmpty()) }
    @Test fun legacyBeepSettingDoesNotReplaceFullSpeech() { assertEquals(listOf("speech:Speed limit 50:true"),h.sample(beepSetting=true)) }
    @Test fun minuteRepeatUsesMonotonicClock() {
        assertEquals(1,h.sample().size);assertTrue(h.sample(now=259_999).isEmpty());assertEquals(1,h.sample(now=260_000).size)
    }
    @Test fun staleGpsDoesNotDispatchAudio() { assertTrue(h.sample(ageMs=5_001).isEmpty()) }
    @Test fun exactFiveSecondGpsBoundaryIsUsable() { assertEquals(1,h.sample(ageMs=5_000).size) }
    @Test fun futureGpsTimestampIsRejected() { assertTrue(h.sample(ageMs=-1).isEmpty()) }
    @Test fun missingGpsTimestampIsRejected() { assertTrue(h.sample(ageMs=null).isEmpty()) }
    @Test fun estimatedPositionAloneDoesNotProvideMeasuredSpeed() { assertTrue(h.sample(provider="estimate").isEmpty()) }
    @Test fun freshCarSpeedCanSupportEstimatedPosition() { assertEquals(1,h.sample(provider="estimate",speed=0f,car=15f).size) }
    @Test fun expiredCarSpeedCannotSupportEstimatedPosition() { assertTrue(h.sample(provider="estimate",car=15f,carAgeMs=5_000).isEmpty()) }
    @Test fun measuredCarSpeedTakesPrecedenceOverGps() { assertTrue(h.sample(speed=15f,car=10f).isEmpty()) }
    @Test fun missingSpeedAndLimitStayUnknown() { assertTrue(h.sample(speed=null).isEmpty());assertTrue(h.sample(limit=null).isEmpty()) }
    @Test fun invalidNumericInputsDoNotDispatch() {
        assertTrue(h.sample(speed=Float.NaN).isEmpty());assertTrue(h.sample(speed=Float.POSITIVE_INFINITY).isEmpty())
        assertTrue(h.sample(speed=-1f).isEmpty());assertTrue(h.sample(limit=Double.NaN).isEmpty());assertTrue(h.sample(limit=0.0).isEmpty())
    }
    @Test fun tripResetAllowsFirstWarningOnNewTrip() { assertEquals(1,h.sample().size);h.resetTrip();assertEquals(1,h.sample(now=201_000).size) }
    @Test fun invalidSampleResetsEpisodeForNextValidSpeed() {
        assertEquals(1,h.sample().size);assertTrue(h.sample(now=201_000,limit=null).isEmpty());assertEquals(1,h.sample(now=202_000).size)
    }
    @Test fun unmuteCanWarnWithoutWaitingForMutedSample() { assertTrue(h.sample(sound=OFF).isEmpty());assertEquals(1,h.sample(now=201_000).size) }
}
