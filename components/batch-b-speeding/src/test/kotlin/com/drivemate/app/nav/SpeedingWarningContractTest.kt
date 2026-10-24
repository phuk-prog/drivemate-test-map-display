package com.drivemate.app.nav
import org.junit.Assert.*
import org.junit.Test
import com.drivemate.app.nav.SpeedingWarningPolicy.Sound.*
import com.drivemate.app.nav.SpeedingWarningPolicy.Signal.*

class SpeedingWarningContractTest {
    private val candidate = SpeedingWarningPolicy()
    private val legacy = LegacySpeedingAdapter()
    private fun tick(t: Long, speed: Double? = 15.0, limit: Double? = 50.0,
                     measured: Boolean = true, sound: SpeedingWarningPolicy.Sound = FULL): SpeedingWarningPolicy.Signal =
        if (System.getProperty("warningVariant") == "baseline") legacy.update(t,speed,limit,measured,sound)
        else candidate.update(t,speed,limit,measured,sound).signal
    @Test fun immediateAtFirstMeasuredExcess() { assertEquals(SPEECH,tick(200_000)) }
    @Test fun tinyExcessNotHiddenByRoundedDisplay() { assertEquals(SPEECH,tick(200_000,50.01/3.6)) }
    @Test fun equalLimitIsSilent() { assertEquals(NONE,tick(200_000,50.0/3.6)) }
    @Test fun belowLimitIsSilent() { assertEquals(NONE,tick(200_000,49.99/3.6)) }
    @Test fun noEarlyRepeatAt59999ms() { tick(200_000); assertEquals(NONE,tick(259_999)) }
    @Test fun repeatAt60000ms() { assertEquals(SPEECH,tick(200_000)); assertEquals(SPEECH,tick(260_000)) }
    @Test fun repeatsEveryFurtherMinute() { assertEquals(SPEECH,tick(200_000)); assertEquals(SPEECH,tick(260_000)); assertEquals(SPEECH,tick(320_000)) }
    @Test fun recoveryStartsNewEpisodeImmediately() { tick(200_000);tick(201_000,10.0);assertEquals(SPEECH,tick(202_000)) }
    @Test fun missingSpeedNeverWarns() { assertEquals(NONE,tick(200_000,null)) }
    @Test fun missingLimitNeverWarns() { assertEquals(NONE,tick(200_000,limit=null)) }
    @Test fun estimatedSpeedNeverWarns() { assertEquals(NONE,tick(200_000,measured=false)) }
    @Test fun mutedNeverEmitsAudio() { assertEquals(NONE,tick(200_000,sound=MUTED)) }
    @Test fun alertsOnlyUsesTone() { assertEquals(TONE,tick(200_000,sound=ALERTS)) }
    @Test fun unmuteDoesNotWaitForMutedWarningTimer() { tick(200_000,sound=MUTED);tick(206_000,sound=MUTED);assertEquals(SPEECH,tick(207_000)) }
    @Test fun sessionClockZeroCanWarn() { assertEquals(SPEECH,tick(0)) }
    @Test fun silenceAfterDroppingToLimit() { tick(200_000);assertEquals(NONE,tick(260_000,50.0/3.6)) }
}
