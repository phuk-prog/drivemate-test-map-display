package com.drivemate.app.fullapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real DriveMate app navigating along Princess Road (A5103), Manchester.
 *
 * Every test starts the same way, as a driver would from another app: healthy fixes at the start of
 * the north-east-bound carriageway (stationary, under the app's 60 s parked threshold), Android's
 * standard navigation link to a point 450 m along it, the route preview, and a tap on Start. The
 * drive then continues along the same OSM centreline at 8 m/s.
 *
 * - Navigation starts (prerequisite for every navigating check): the browse screen ("Where to?")
 *   goes and guidance appears within 60 s of the link, using DriveMate's own on-phone route finder.
 * - 01.01 while navigating: with GPS lost, "Position uncertain" appears no sooner than 3 s after the
 *   last fix and by 7 s, and clears within 1.5 s of accurate fixes resuming.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NavigationGpsTest : FullAppHarness() {
    override val checklist = "01.01"

    private val destinationM = 450.0

    private fun browsing() = text("Where to?")
    private fun uncertain() = text("Position uncertain")

    /** From launch to guidance on screen; returns once navigating. */
    private fun startNavigating() {
        travelled = 0.0
        feed(10_000, accuracy = 4f, speed = 0f, label = "start-stationary")
        captureScreen("after-start")
        requireAppInFront()
        assertTrue("Harness: DriveMate map screen not shown", browsing())
        val (dest, _) = along(destinationM)
        openNavigationLink(dest[0], dest[1])
        val preview = feedUntil(40_000, accuracy = 4f, speed = 0f, label = "route-preview") { textContains("Start") }
        captureScreen("preview")
        assertTrue("No route preview with a Start button within 40 s: $timeline", preview != null)
        val start = device.findObject(androidx.test.uiautomator.By.textContains("Start"))
        assertTrue("Start button not found", start != null)
        start!!.click()
        timeline.put(org.json.JSONObject().put("event", "tapped-start"))
        val guiding = feedUntil(20_000, accuracy = 4f, speed = 8f, label = "guidance") { !browsing() && !textContains("Start") }
        captureScreen("navigating")
        requireAppInFront()
        assertTrue("Navigation did not start within 20 s of Start: $timeline", guiding != null)
    }

    @Test fun navigationStartsFromAStandardNavigationLink() {
        startNavigating()
        feed(8_000, accuracy = 4f, label = "driving", watch = ::uncertain)
        captureScreen("driving")
        requireAppInFront()
        val falseAlarm = seen.firstOrNull()
        verdict("uncertain_seen_ms" to falseAlarm)
        assertTrue("Navigation ended or returned to browsing while driving: $timeline", !browsing())
        assertTrue("'Position uncertain' shown at $falseAlarm ms with accurate fixes while navigating", falseAlarm == null)
    }

    @Test fun pref01_01_navigatingGpsLossShowsPositionUncertainAndClears() {
        startNavigating()
        feed(4_000, accuracy = 4f, label = "driving")
        feed(9_000, accuracy = null, label = "gps-lost", watch = ::uncertain)
        val first = seen.firstOrNull()
        captureScreen("gps-lost")
        requireAppInFront()
        feed(4_000, accuracy = 4f, label = "recovered", watch = ::uncertain)
        val lastAfter = seen.lastOrNull()
        verdict("first_seen_ms" to first, "last_seen_after_recovery_ms" to lastAfter)
        assertTrue("'Position uncertain' never shown within 9 s of GPS loss while navigating: $timeline", first != null)
        assertTrue("Shown $first ms after the last fix, sooner than 3 s", first!! >= 2_900)
        assertTrue("Shown $first ms after the last fix, beyond 7 s", first <= 7_000)
        assertTrue("Still shown $lastAfter ms after accurate fixes resumed", lastAfter == null || lastAfter <= 1_500)
    }
}
