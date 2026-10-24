package com.drivemate.app.nav
import org.junit.Assert.*
import org.junit.Test
import com.drivemate.app.nav.VoiceDeliveryHarness.SoundMode.*

class VoiceDeliveryTest {
    @Test fun legacyAlertHistoryRemainsSuppressed() {
        val q=VoiceMessageQueue();q.add("Speed limit 50",true,false,200_000);val m=q.next(200_000)!!;q.markSpoken(m,200_000)
        assertFalse(q.add("Speed limit 50",true,false,201_000))
    }
    @Test fun policyRepeatBypassesOnlyHistory() {
        val q=VoiceMessageQueue();q.add("Speed limit 50",true,false,200_000);q.markSpoken(q.next(200_000)!!,200_000)
        assertTrue(q.add("Speed limit 50",true,false,201_000,policyRepeat=true))
        assertFalse(q.add("Speed limit 50",true,false,201_001,policyRepeat=true))
    }
    @Test fun policyRepeatDoesNotRestartCurrentAnnouncement() {
        val h=VoiceDeliveryHarness();h.say("Speed limit 50",alert=true,policyRepeat=true);h.say("Speed limit 50",alert=true,policyRepeat=true)
        assertEquals(listOf("Speed limit 50"),h.dispatched)
    }
    @Test fun policyRepeatActuallyReachesSpeechAfterRecovery() {
        val h=VoiceDeliveryHarness();h.say("Speed limit 50",alert=true,policyRepeat=true);h.begin();h.complete();h.advance(1_000)
        h.say("Speed limit 50",alert=true,policyRepeat=true);assertEquals(2,h.dispatched.size)
    }
    @Test fun defaultOtherAlertRemainsHistorySuppressed() {
        val h=VoiceDeliveryHarness();h.say("GPS lost",alert=true);h.begin();h.complete();h.advance(1_000);h.say("GPS lost",alert=true)
        assertEquals(listOf("GPS lost"),h.dispatched)
    }
    @Test fun alertsRejectsNewSpeechEvenWhenAlertTrue() {
        val h=VoiceDeliveryHarness();h.sound=ALERTS;h.say("Several speed bumps ahead",alert=true)
        assertTrue(h.dispatched.isEmpty())
    }
    @Test fun alertsConvertsExplicitCameraMainAlertToTone() {
        val h=VoiceDeliveryHarness();h.sound=ALERTS;h.say("Speed camera ahead",alert=true,mainAlert=VoicePlaybackPolicy.mainHazard("CAMERA"))
        assertEquals(listOf("TONE"),h.dispatched)
    }
    @Test fun fullMainAlertStillSpeaks() {
        val h=VoiceDeliveryHarness();h.say("Speed camera ahead",alert=true,mainAlert=true);assertEquals(listOf("Speed camera ahead"),h.dispatched)
    }
    @Test fun muteHasNoNewSpeechOrTone() {
        val h=VoiceDeliveryHarness();h.sound=OFF;h.say("Speed camera ahead",alert=true,mainAlert=true);h.beep(true)
        assertTrue(h.dispatched.isEmpty())
    }
    @Test fun queuedAlertSpeechIsDiscardedOnAlertsOnly() {
        val h=VoiceDeliveryHarness();h.waitingSpeech(true);h.say("Old spoken camera alert",alert=true);h.sound=ALERTS
        h.sound=ALL;h.waitingSpeech(false);h.pumpAgain();assertTrue(h.dispatched.isEmpty())
    }
    @Test fun queuedSpeechIsDiscardedOnMute() {
        val h=VoiceDeliveryHarness();h.waitingSpeech(true);h.say("Old spoken alert",alert=true);h.sound=OFF
        h.sound=ALL;h.waitingSpeech(false);h.pumpAgain();assertTrue(h.dispatched.isEmpty())
    }
    @Test fun alertsKeepsQueuedTonesWhileCurrentSentenceFinishes() {
        val h=VoiceDeliveryHarness();h.say("Playing sentence");h.begin();h.beep(true);h.say("Queued speech",alert=true)
        h.sound=ALERTS;assertTrue(h.hasCurrent());h.complete()
        assertEquals(listOf("Playing sentence","TONE"),h.dispatched)
    }
    @Test fun playingSentenceCanFinishOnMute() {
        val h=VoiceDeliveryHarness();h.say("Playing sentence");h.begin();h.sound=OFF
        assertTrue(h.hasCurrent());assertTrue(h.playing());h.complete();assertFalse(h.hasCurrent())
    }
    @Test fun waitingSpeechIsCancelledOnModeChange() {
        val h=VoiceDeliveryHarness();h.say("Waiting for onStart");assertFalse(h.playing());h.sound=ALERTS;assertFalse(h.hasCurrent())
    }
    @Test fun lateOldOnStartCannotMarkNewUtterancePlaying() {
        val h=VoiceDeliveryHarness();h.say("Old");val old=h.currentId();h.sound=ALERTS;h.sound=ALL;h.say("New")
        h.begin(old);assertFalse(h.playing());h.sound=ALERTS;assertFalse(h.hasCurrent())
    }
    @Test fun nullOnStartCannotMarkWaitingUtterancePlaying() {
        val h=VoiceDeliveryHarness();h.say("Waiting");h.begin(null);assertFalse(h.playing());h.sound=OFF;assertFalse(h.hasCurrent())
    }
    @Test fun phoneSynthesisStartDoesNotCountAsAudiblePlayback() {
        val h=VoiceDeliveryHarness();h.say("Synthesis");h.begin(synthesizing=true);assertFalse(h.playing());h.sound=ALERTS;assertFalse(h.hasCurrent())
    }
    @Test fun pumpCannotSelectInjectedSpeechInAlertsOnly() {
        val h=VoiceDeliveryHarness();h.sound=ALERTS;h.queueDirect("Late speech");h.pumpAgain();assertTrue(h.dispatched.isEmpty())
    }
    @Test fun queuedDuplicatesStillSuppressed() {
        val h=VoiceDeliveryHarness();h.waitingSpeech(true);h.say("Speed limit 50",alert=true,policyRepeat=true);h.say("Speed limit 50",alert=true,policyRepeat=true)
        h.waitingSpeech(false);h.pumpAgain();h.complete();assertEquals(listOf("Speed limit 50"),h.dispatched)
    }
    @Test fun mainClassificationExcludesMinorAndUnknownHazards() {
        listOf("CAMERA","RED_LIGHT","AVERAGE_SPEED","CRASH","CLOSURE","OBJECT","BROKEN_CAR","ANIMALS","HAZARD").forEach { assertTrue(VoicePlaybackPolicy.mainHazard(it)) }
        listOf("SPEED_BUMP","SCHOOL","POTHOLE","SHARP_BEND","UNKNOWN").forEach { assertFalse(VoicePlaybackPolicy.mainHazard(it)) }
        assertTrue(VoicePlaybackPolicy.mainRoadEvent("ROAD_CLOSED"));assertTrue(VoicePlaybackPolicy.mainRoadEvent("LANES_CLOSED"))
        assertFalse(VoicePlaybackPolicy.mainRoadEvent("SPEED_LIMIT"));assertFalse(VoicePlaybackPolicy.mainRoadEvent("SIGN"))
    }
}
