package com.drivemate.app.fullapp

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.drivemate.app.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.cos

/**
 * Checklist 01.01 on the real combined DriveMate app: "Show 'Position uncertain' after three
 * seconds of continuous uncertainty."
 *
 * Runs inside the private app build on an emulator image without Google Play services, so the
 * app's own GPS-provider path receives every fix. Fixes come from Android's test-provider API
 * along Princess Road (A5103), Manchester (public OSM fixture), moving at 8 m/s unless parked.
 * The banner is read from the screen through accessibility (UiAutomator), as a driver sees it.
 *
 * Timing rules (independent of the app's own constants):
 * - "Uncertain" fixes report 35 m accuracy: worse than a lane-level fix, still a GPS fix.
 * - The banner must not appear before 3 s of continuous uncertainty (100 ms clock tolerance) and
 *   must appear within a 1 s display budget after that.
 * - GPS loss: no fixes at all. The banner must not appear sooner than 3 s after the last fix and
 *   must appear within 7 s (three missed 1 Hz fixes, then 3 s, then the 1 s budget).
 * - A healthy position must never show the banner, including after 60 s parked.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PositionUncertainBannerTest {
    @get:Rule val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    @get:Rule val name = TestName()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val locations = context.getSystemService(LocationManager::class.java)
    private var scenario: ActivityScenario<MainActivity>? = null
    private val timeline = JSONArray()

    private val road: List<DoubleArray> by lazy {
        val doc = InstrumentationRegistry.getInstrumentation().context.assets.open("princess-road-a5103-osm.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        val c = doc.getJSONObject("carriageways").getJSONObject("own").getJSONArray("coordinates")
        (0 until c.length()).map { doubleArrayOf(c.getJSONArray(it).getDouble(1), c.getJSONArray(it).getDouble(0)) }
    }
    private var travelled = 0.0

    @Before fun setUp() {
        shell("appops set ${context.packageName} android:mock_location allow")
        runCatching { locations.removeTestProvider(LocationManager.GPS_PROVIDER) }
        if (Build.VERSION.SDK_INT >= 31) {
            locations.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE)
        } else {
            @Suppress("DEPRECATION")
            locations.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
                android.location.Criteria.POWER_HIGH, android.location.Criteria.ACCURACY_FINE)
        }
        locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Before the first fix the position really is uncertain, so the banner may show at launch.
        // Timing starts only once accurate fixes have kept it away for 4 s.
        feed(12_000, accuracy = 4f, label = "start-healthy")
        val late = seen.lastOrNull()
        assertTrue("Banner still shown at $late ms of a healthy start: $timeline", late == null || late < 8_000)
    }

    @After fun tearDown() {
        writeResult()
        scenario?.close()
        runCatching { locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false) }
        runCatching { locations.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).close()

    /** The point [metres] along the north-east-bound carriageway, and its bearing. */
    private fun along(metres: Double): Pair<DoubleArray, Double> {
        var left = metres % 550.0
        for (i in 0 until road.size - 1) {
            val a = road[i]; val b = road[i + 1]
            val dy = (b[0] - a[0]) * 111_320.0
            val dx = (b[1] - a[1]) * 111_320.0 * cos(Math.toRadians(a[0]))
            val len = Math.hypot(dx, dy)
            if (left <= len) {
                val t = left / len
                val bearing = (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360
                return doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t) to bearing
            }
            left -= len
        }
        return road.last() to 0.0
    }

    private fun sendFix(accuracy: Float, speed: Float) {
        val (p, bearing) = along(travelled)
        locations.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
            latitude = p[0]; longitude = p[1]; this.accuracy = accuracy; this.speed = speed
            this.bearing = bearing.toFloat(); altitude = 40.0
            if (Build.VERSION.SDK_INT >= 26) { bearingAccuracyDegrees = 5f; speedAccuracyMetersPerSecond = 0.5f; verticalAccuracyMeters = 5f }
            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
    }

    private var phaseStart = 0L
    private val seen = ArrayList<Long>()

    private fun bannerVisible() = device.hasObject(By.text("Position uncertain"))

    /**
     * For [durationMs]: a fix every [everyMs] ms (null = none: GPS lost) while sampling the screen
     * about every 100 ms. Records when the banner was visible, relative to the phase start.
     */
    private fun feed(durationMs: Long, accuracy: Float?, everyMs: Long = 1_000, speed: Float = 8f, label: String = "phase") {
        phaseStart = SystemClock.elapsedRealtime()
        seen.clear()
        var nextFix = phaseStart
        val samples = JSONArray()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val t = now - phaseStart
            if (t >= durationMs) break
            if (accuracy != null && now >= nextFix) {
                travelled += speed * everyMs / 1000.0
                sendFix(accuracy, speed)
                nextFix += everyMs
            }
            val visible = bannerVisible()
            val sampledAt = SystemClock.elapsedRealtime() - phaseStart
            if (visible) seen += sampledAt
            if (samples.length() == 0 || (samples.getJSONObject(samples.length() - 1).getBoolean("visible") != visible))
                samples.put(JSONObject().put("t_ms", sampledAt).put("visible", visible))
            SystemClock.sleep(100)
        }
        timeline.put(JSONObject().put("event", label).put("duration_ms", durationMs)
            .put("accuracy_m", accuracy ?: JSONObject.NULL).put("fix_every_ms", everyMs).put("speed_mps", speed)
            .put("banner_changes", samples))
    }

    private fun firstSeen(): Long? = seen.firstOrNull()

    private fun writeResult() {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: context.getExternalFilesDir("full-app") ?: return
        dir.mkdirs()
        File(dir, "${name.methodName}.json").writeText(JSONObject().put("test", name.methodName)
            .put("checklist", "01.01").put("timeline", timeline).toString(1))
    }

    @Test fun pref01_01_bannerAppearsThreeSecondsIntoContinuousPoorAccuracy() {
        feed(6_000, accuracy = 35f, label = "poor-accuracy")
        val first = firstSeen()
        timeline.put(JSONObject().put("event", "verdict").put("first_seen_ms", first ?: JSONObject.NULL))
        assertTrue("Banner never appeared in 6 s of 35 m fixes: $timeline", first != null)
        assertTrue("Banner appeared after $first ms, before 3 s of uncertainty", first!! >= 2_900)
        assertTrue("Banner took $first ms, beyond 3 s + 1 s budget", first <= 4_000)
    }

    @Test fun pref01_01_bannerAppearsAfterGpsIsLost() {
        feed(9_000, accuracy = null, label = "gps-lost")
        val first = firstSeen()
        timeline.put(JSONObject().put("event", "verdict").put("first_seen_ms", first ?: JSONObject.NULL))
        assertTrue("Banner never appeared within 9 s of GPS loss: $timeline", first != null)
        assertTrue("Banner appeared $first ms after the last fix, sooner than 3 s", first!! >= 2_900)
        assertTrue("Banner took $first ms after the last fix, beyond 7 s", first <= 7_000)
    }

    @Test fun pref01_01_bannerClearsWhenPositionRecovers() {
        feed(6_000, accuracy = 35f, label = "poor-accuracy")
        assertTrue("Banner never appeared, so recovery cannot be judged: $timeline", firstSeen() != null)
        feed(4_000, accuracy = 4f, label = "recovered")
        val lastSeen = seen.lastOrNull()
        timeline.put(JSONObject().put("event", "verdict").put("last_seen_after_recovery_ms", lastSeen ?: JSONObject.NULL))
        assertTrue("Banner still shown ${lastSeen} ms after accurate fixes resumed", lastSeen == null || lastSeen <= 1_500)
    }

    @Test fun pref01_01_shortGlitchUnderThreeSecondsNeverShowsBanner() {
        feed(2_000, accuracy = 35f, label = "glitch-2s")
        val duringGlitch = firstSeen()
        feed(4_000, accuracy = 4f, label = "recovered")
        val after = firstSeen()
        timeline.put(JSONObject().put("event", "verdict").put("seen_during_ms", duringGlitch ?: JSONObject.NULL)
            .put("seen_after_ms", after ?: JSONObject.NULL))
        assertTrue("Banner shown for a 2 s glitch: $timeline", duringGlitch == null && after == null)
    }

    @Test fun pref01_01_parkedWithHealthyGpsNeverShowsBanner() {
        // Stopped with accurate fixes every second for 80 s: past the app's 60 s parked threshold.
        feed(80_000, accuracy = 4f, speed = 0f, label = "parked-healthy")
        val first = firstSeen()
        timeline.put(JSONObject().put("event", "verdict").put("first_seen_ms", first ?: JSONObject.NULL))
        assertTrue("Banner shown at $first ms while parked with accurate GPS every second: $timeline", first == null)
    }

}
