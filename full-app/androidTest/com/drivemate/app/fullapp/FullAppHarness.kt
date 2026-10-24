package com.drivemate.app.fullapp

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.drivemate.app.MainActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.cos

/**
 * Shared full-app harness: launches the real DriveMate app, feeds identical synthetic fixes to the
 * GPS test provider and fused location along Princess Road's north-east-bound carriageway (public
 * OSM way 526771299), reads the screen through accessibility, and records a timeline per test.
 * A run where DriveMate is not on screen fails as a harness fault, never as a pass.
 */
abstract class FullAppHarness {
    @get:Rule val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    @get:Rule val name = TestName()

    protected abstract val checklist: String

    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val device: UiDevice = UiDevice.getInstance(instrumentation)
    protected val context = instrumentation.targetContext
    private val locations = context.getSystemService(LocationManager::class.java)
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private var scenario: ActivityScenario<MainActivity>? = null
    protected val timeline = JSONArray()

    /** The north-east-bound carriageway, [lat, lon] pairs in travel order. */
    protected val road: List<DoubleArray> by lazy {
        val doc = instrumentation.context.assets.open("princess-road-a5103-osm.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        val c = doc.getJSONObject("carriageways").getJSONObject("own").getJSONArray("coordinates")
        (0 until c.length()).map { doubleArrayOf(c.getJSONArray(it).getDouble(1), c.getJSONArray(it).getDouble(0)) }
    }
    /** Metres travelled along [road]; fixes are placed there. */
    protected var travelled = 0.0

    @Before fun startApp() {
        shell("appops set ${context.packageName} android:mock_location allow")
        val mock = shell("appops get ${context.packageName} android:mock_location")
        assertTrue("Mock location not allowed for the app: $mock", mock.contains("allow"))
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
        Tasks.await(fused.setMockMode(true), 10, TimeUnit.SECONDS)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @After fun stopApp() {
        captureScreen("end")
        writeResult()
        scenario?.close()
        runCatching { Tasks.await(fused.setMockMode(false), 10, TimeUnit.SECONDS) }
        runCatching { locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false) }
        runCatching { locations.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    /** Runs [cmd] as the shell user and waits for it to finish (closing early can cancel it). */
    protected fun shell(cmd: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd))
            .bufferedReader().use { it.readText() }

    /** The point [metres] along [road] and the road's bearing there. */
    protected fun along(metres: Double): Pair<DoubleArray, Double> {
        var left = metres.coerceAtLeast(0.0)
        for (i in 0 until road.size - 1) {
            val a = road[i]; val b = road[i + 1]
            val dy = (b[0] - a[0]) * 111_320.0
            val dx = (b[1] - a[1]) * 111_320.0 * cos(Math.toRadians(a[0]))
            val len = Math.hypot(dx, dy)
            val bearing = (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360
            if (left <= len) {
                val t = left / len
                return doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t) to bearing
            }
            left -= len
        }
        val a = road[road.size - 2]; val b = road.last()
        return b to (Math.toDegrees(Math.atan2((b[1] - a[1]) * cos(Math.toRadians(a[0])), b[0] - a[0])) + 360) % 360
    }

    protected fun sendFix(accuracy: Float, speed: Float) {
        val (p, bearing) = along(travelled)
        fun fix(provider: String) = Location(provider).apply {
            latitude = p[0]; longitude = p[1]; this.accuracy = accuracy; this.speed = speed
            this.bearing = bearing.toFloat(); altitude = 40.0
            if (Build.VERSION.SDK_INT >= 26) { bearingAccuracyDegrees = 5f; speedAccuracyMetersPerSecond = 0.5f; verticalAccuracyMeters = 5f }
            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locations.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix(LocationManager.GPS_PROVIDER))
        Tasks.await(fused.setMockLocation(fix("fused")), 5, TimeUnit.SECONDS)
    }

    /** Sample times (ms into the phase) at which [watch] was on screen during the last [feed]. */
    protected val seen = ArrayList<Long>()

    /**
     * For [durationMs]: a fix every [everyMs] ms (accuracy null = GPS lost) while sampling the screen
     * about every 100 ms for [watch]. Records when it was visible, relative to the phase start.
     */
    protected fun feed(durationMs: Long, accuracy: Float?, everyMs: Long = 1_000, speed: Float = 8f,
                       label: String = "phase", watch: (() -> Boolean)? = null) {
        val start = SystemClock.elapsedRealtime()
        seen.clear()
        var nextFix = start
        val samples = JSONArray()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (now - start >= durationMs) break
            if (accuracy != null && now >= nextFix) {
                travelled += speed * everyMs / 1000.0
                sendFix(accuracy, speed)
                nextFix += everyMs
            }
            if (watch != null) {
                val visible = watch()
                val t = SystemClock.elapsedRealtime() - start
                if (visible) seen += t
                if (samples.length() == 0 || samples.getJSONObject(samples.length() - 1).getBoolean("visible") != visible)
                    samples.put(JSONObject().put("t_ms", t).put("visible", visible))
            }
            SystemClock.sleep(100)
        }
        timeline.put(JSONObject().put("event", label).put("duration_ms", durationMs)
            .put("accuracy_m", accuracy ?: JSONObject.NULL).put("fix_every_ms", everyMs).put("speed_mps", speed)
            .put("travelled_m", travelled).put("watch_changes", samples))
    }

    /**
     * Feeds [accuracy] fixes until [done] holds or [timeoutMs] passes; returns ms taken, or null.
     * The screen is read once first: until the accessibility tree has been walked, Compose text
     * can be invisible to UiAutomator (run 40086626435).
     */
    protected fun feedUntil(timeoutMs: Long, accuracy: Float, speed: Float, label: String, done: () -> Boolean): Long? {
        captureScreen("before-$label")
        val start = SystemClock.elapsedRealtime()
        var nextFix = start
        var took: Long? = null
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            val now = SystemClock.elapsedRealtime()
            if (now >= nextFix) { travelled += speed; sendFix(accuracy, speed); nextFix += 1_000 }
            if (done()) { took = SystemClock.elapsedRealtime() - start; break }
            SystemClock.sleep(200)
        }
        timeline.put(JSONObject().put("event", label).put("took_ms", took ?: JSONObject.NULL)
            .put("timeout_ms", timeoutMs).put("travelled_m", travelled))
        return took
    }

    /** Opens DriveMate with Android's standard navigation link to [lat],[lon], as another app would. */
    protected fun openNavigationLink(lat: Double, lon: Double) {
        val uri = Uri.parse(String.format(java.util.Locale.US, "google.navigation:q=%.6f,%.6f", lat, lon))
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setClassName(context, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        timeline.put(JSONObject().put("event", "navigation-link").put("uri", uri.toString()))
    }

    protected fun text(t: String) = device.hasObject(By.text(t))
    protected fun textContains(t: String) = device.hasObject(By.textContains(t))

    /** The checks only mean something while DriveMate is the screen being read. */
    protected fun requireAppInFront() {
        val blocker = device.findObject(By.textContains("isn't responding"))?.text
        assertTrue("Harness: system dialog over the app: $blocker", blocker == null)
        assertTrue("Harness: ${device.currentPackageName} in front, not ${context.packageName}",
            device.currentPackageName == context.packageName)
    }

    protected fun captureScreen(tag: String) {
        val dir = outputDir() ?: return
        runCatching { device.takeScreenshot(File(dir, "${name.methodName}-$tag.png")) }
        runCatching { File(dir, "${name.methodName}-$tag.xml").outputStream().use { device.dumpWindowHierarchy(it) } }
        val texts = runCatching {
            device.findObjects(By.textContains("")).mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }.distinct().take(40)
        }.getOrDefault(emptyList())
        timeline.put(JSONObject().put("event", "screen-$tag").put("package", device.currentPackageName ?: JSONObject.NULL)
            .put("visible_texts", JSONArray(texts)))
    }

    protected fun verdict(vararg pairs: Pair<String, Any?>) {
        val v = JSONObject().put("event", "verdict")
        pairs.forEach { (k, x) -> v.put(k, x ?: JSONObject.NULL) }
        timeline.put(v)
    }

    private fun outputDir(): File? = (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        ?: context.getExternalFilesDir("full-app"))?.also { it.mkdirs() }

    private fun writeResult() {
        val dir = outputDir() ?: return
        File(dir, "${name.methodName}.json").writeText(JSONObject().put("test", name.methodName)
            .put("checklist", checklist).put("timeline", timeline).toString(1))
    }
}
