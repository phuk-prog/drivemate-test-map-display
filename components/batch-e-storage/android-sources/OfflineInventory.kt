package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import kotlin.math.cos
import kotlin.math.max

/**
 * Installed versus available offline area packages, what each one contains,
 * and what is missing (backlog MX08 / WA21). Pure: callers pass in the
 * already authenticated manifests and the set of packages whose bytes were
 * SHA-256 verified on an IO thread; nothing here touches the network.
 *
 * "Ready" is only claimed after integrity and compatibility checks (a package
 * verified within one generation together with that generation's base),
 * never because a file exists or the map happens to display.
 */
internal object OfflineInventory {
    enum class Component(val label: String) {
        MAP("map detail"),
        LANES("lane arrows"),
        LIMITS("speed limits"),
        ROAD_INFO("road information"),
        PLACES("offline place search"),
        SEARCH("offline address search"),
        ROUTES("offline routes"),
        TERRAIN("hills and terrain"),
    }

    enum class Have { READY, PARTIAL, NOT_CHECKED, MISSING, NOT_PUBLISHED, NOT_SUPPORTED }

    data class Part(val component: Component, val have: Have)

    enum class State { INSTALLED, UPDATE_AVAILABLE, PARTLY_DOWNLOADED, AVAILABLE }

    data class Area(
        val name: String,
        val label: String,
        val bytes: Long,
        val state: State,
        val savedBytes: Long = 0,
        /** Component status, for installed areas only. */
        val parts: List<Part> = emptyList(),
        /** Base and map detail verified together in one generation. */
        val usable: Boolean = false,
    )

    /** One installed generation: its own manifest and the verified package names. */
    data class InstalledGeneration(
        val manifest: RegionalMapManifest,
        val verified: Set<String>,
        val installedAtMs: Long?,
    ) {
        val generation: String get() = manifest.catalog.sourceSha256
    }

    data class Inventory(
        val installedGeneration: String?,
        val installedAtMs: Long?,
        val installedBuiltDate: String?,
        val availableBuiltDate: String?,
        val baseReady: Boolean,
        val installed: List<Area>,
        val available: List<Area>,
        val notes: List<String>,
    ) {
        val installedBytes: Long get() = installed.sumOf { it.bytes }
        val availableBytes: Long get() = available.sumOf { it.bytes }
    }

    // Greater Manchester: covered by DMN's built-in map (Dmn.covers).
    val GM = doubleArrayOf(-2.75, 53.30, -1.90, 53.70)

    fun build(
        latest: RegionalMapManifest?,
        installed: InstalledGeneration?,
        companionState: (String) -> Have,
        savedBytes: (RegionMapCatalog.Pack) -> Long,
        /** Offline routes for an area's bounds, from DMN's pieces on the phone (5.2). */
        areaRoutes: (DoubleArray?) -> Have,
        uncoveredPoints: Int = 0,
    ): Inventory {
        val baseReady = installed != null && "base" in installed.verified
        val latestPacks = latest?.catalog?.packages?.associateBy { it.name }.orEmpty()
        val installedAreas = ArrayList<Area>()
        if (installed != null) {
            val packs = installed.manifest.catalog.packages.associateBy { it.name }
            for (name in installed.verified.sorted()) {
                if (name == "base") continue
                val pack = packs[name] ?: continue
                val newer = latestPacks[name]?.let { it.sha256 != pack.sha256 } == true
                val bounds = installed.manifest.bounds[name]
                installedAreas += Area(
                    name = name,
                    label = label(bounds),
                    bytes = pack.bytes,
                    state = if (newer) State.UPDATE_AVAILABLE else State.INSTALLED,
                    parts = parts(installed.manifest, name, bounds, companionState, areaRoutes),
                    usable = baseReady,
                )
            }
        }
        val installedNames = installedAreas.map { it.name }.toSet()
        val available = latestPacks.values
            .filter { it.name != "base" && it.name !in installedNames }
            .map { pack ->
                val saved = savedBytes(pack).coerceIn(0, pack.bytes)
                Area(pack.name, label(latest?.bounds?.get(pack.name)), pack.bytes,
                    if (saved > 0) State.PARTLY_DOWNLOADED else State.AVAILABLE, saved)
            }
            .sortedWith(compareByDescending<Area> { it.savedBytes }.thenBy { it.label }.thenBy { it.name })
        val notes = ArrayList<String>()
        if (latest == null) notes += "New area downloads aren't available right now (none published, or no signal)."
        if (installed == null || installedAreas.isEmpty()) notes += "No area maps are downloaded yet."
        else if (!baseReady) notes += "The nationwide base map failed its check, so area maps can't be used offline."
        if (uncoveredPoints > 0) notes += "Part of the area you chose has no detailed offline map published yet."
        if (installedAreas.any { a -> a.parts.any { it.component == Component.ROUTES && it.have != Have.READY } }) {
            notes += "Area maps show the map only. New routes with no signal need DriveMate's road pieces for the area: " +
                "they are kept after a journey there, and Greater Manchester is built in. Without them, guidance carries on " +
                "along a route already planned, but a diversion can't be worked out."
        }
        notes += "Hills and terrain are not available offline."
        return Inventory(installed?.generation, installed?.installedAtMs, installed?.manifest?.builtDate,
            latest?.builtDate, baseReady, installedAreas, available, notes)
    }

    private fun parts(
        manifest: RegionalMapManifest,
        name: String,
        bounds: DoubleArray?,
        companionState: (String) -> Have,
        areaRoutes: (DoubleArray?) -> Have,
    ): List<Part> {
        val companions = manifest.companions[name].orEmpty()
        fun layer(component: Component, prefix: String): Part {
            val files = companions.filter { it.startsWith(prefix) }
            if (files.isEmpty()) return Part(component, Have.NOT_PUBLISHED)
            val states = files.map(companionState)
            val have = when {
                states.all { it == Have.READY } -> Have.READY
                states.any { it == Have.MISSING } -> Have.MISSING
                else -> Have.NOT_CHECKED
            }
            return Part(component, have)
        }
        val search = if ("search-offline-uk.tsv.gz" in manifest.nationalFiles) {
            Part(Component.SEARCH, companionState("search-offline-uk.tsv.gz"))
        } else Part(Component.SEARCH, Have.NOT_PUBLISHED)
        val routes = areaRoutes(bounds)
        return listOf(
            Part(Component.MAP, Have.READY),
            layer(Component.LANES, "lanes-"),
            layer(Component.LIMITS, "limits-"),
            layer(Component.ROAD_INFO, "roadinfo-"),
            layer(Component.PLACES, "places-"),
            search,
            Part(Component.ROUTES, routes),
            Part(Component.TERRAIN, Have.NOT_SUPPORTED),
        )
    }

    /** Offline routes for an area (west, south, east, north): DMN's built-in Greater Manchester map, else its pieces on the phone. */
    fun areaRoutes(bounds: DoubleArray?, builtIn: Boolean, pieces: (DoubleArray) -> Have): Have = when {
        bounds == null -> Have.MISSING
        builtIn && within(bounds, GM) -> Have.READY
        else -> pieces(bounds).let { if (it == Have.MISSING && builtIn && overlaps(bounds, GM)) Have.PARTIAL else it }
    }

    /** Offline routes along a journey: the built-in map when it covers every point, else the pieces on the phone. */
    fun journeyRoutes(points: List<LatLon>, builtIn: Boolean, pieces: (List<LatLon>) -> Have): Have {
        fun inGm(p: LatLon) = p.lon >= GM[0] && p.lat >= GM[1] && p.lon <= GM[2] && p.lat <= GM[3]
        if (points.isEmpty()) return Have.MISSING
        if (builtIn && points.all(::inGm)) return Have.READY
        return pieces(points).let { if (it == Have.MISSING && builtIn && points.any(::inGm)) Have.PARTIAL else it }
    }

    private fun overlaps(a: DoubleArray, b: DoubleArray) = a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]
    private fun within(a: DoubleArray, b: DoubleArray) = a[0] >= b[0] && a[2] <= b[2] && a[1] >= b[1] && a[3] <= b[3]

    // Plain-English labels: the nearest of these UK places to the package centre.
    private val PLACES = listOf(
        "London" to (51.507 to -0.128), "Birmingham" to (52.486 to -1.890), "Manchester" to (53.481 to -2.243),
        "Liverpool" to (53.408 to -2.992), "Leeds" to (53.801 to -1.549), "Sheffield" to (53.381 to -1.470),
        "Newcastle" to (54.978 to -1.618), "Bristol" to (51.454 to -2.588), "Nottingham" to (52.954 to -1.158),
        "Leicester" to (52.637 to -1.140), "Norwich" to (52.630 to 1.297), "Cambridge" to (52.205 to 0.122),
        "Oxford" to (51.752 to -1.258), "Southampton" to (50.910 to -1.404), "Brighton" to (50.823 to -0.137),
        "Plymouth" to (50.376 to -4.143), "Exeter" to (50.718 to -3.534), "Cardiff" to (51.481 to -3.179),
        "Swansea" to (51.621 to -3.944), "Aberystwyth" to (52.415 to -4.083), "Bangor" to (53.227 to -4.129),
        "Hull" to (53.768 to -0.333), "York" to (53.960 to -1.087), "Carlisle" to (54.893 to -2.936),
        "Lancaster" to (54.047 to -2.801), "Kendal" to (54.328 to -2.746), "Stoke-on-Trent" to (53.003 to -2.180),
        "Lincoln" to (53.234 to -0.538), "Ipswich" to (52.057 to 1.148), "Canterbury" to (51.280 to 1.079),
        "Glasgow" to (55.864 to -4.252), "Edinburgh" to (55.953 to -3.188), "Aberdeen" to (57.150 to -2.094),
        "Dundee" to (56.462 to -2.971), "Inverness" to (57.478 to -4.225), "Fort William" to (56.820 to -5.105),
        "Dumfries" to (55.070 to -3.605), "Stornoway" to (58.209 to -6.387), "Kirkwall" to (58.981 to -2.960),
        "Lerwick" to (60.155 to -1.145), "Belfast" to (54.597 to -5.930), "Derry" to (54.997 to -7.309),
        "Enniskillen" to (54.344 to -7.632), "Truro" to (50.263 to -5.051), "Shrewsbury" to (52.707 to -2.754),
        "Hereford" to (52.056 to -2.716), "Gloucester" to (51.864 to -2.244), "Middlesbrough" to (54.574 to -1.235),
    )

    fun label(bounds: DoubleArray?): String {
        if (bounds == null) return "Unnamed area"
        val lat = (bounds[1] + bounds[3]) / 2
        val lon = (bounds[0] + bounds[2]) / 2
        val k = max(cos(Math.toRadians(lat)), 0.01)
        val nearest = PLACES.minBy { (_, p) ->
            val dy = p.first - lat
            val dx = (p.second - lon) * k
            dy * dy + dx * dx
        }
        return "Around ${nearest.first}"
    }

    /** Short plain-English status of one area. */
    fun areaLine(a: Area): String {
        val size = OfflineStorage.mb(a.bytes)
        return when (a.state) {
            State.INSTALLED -> "${a.label} · $size · " + if (a.usable) "installed and checked" else "installed, not usable yet"
            State.UPDATE_AVAILABLE -> "${a.label} · $size · installed and checked; a newer version is available"
            State.PARTLY_DOWNLOADED -> "${a.label} · $size · partly downloaded (${OfflineStorage.mb(a.savedBytes)} saved)"
            State.AVAILABLE -> "${a.label} · $size · available to download"
        }
    }

    /** "Ready offline: …. Missing: …. Not available offline: …." */
    fun partsLine(parts: List<Part>): String {
        fun names(vararg haves: Have) = parts.filter { it.have in haves }.joinToString(", ") { it.component.label }
        return listOf(
            "Ready offline" to names(Have.READY),
            "Only partly" to names(Have.PARTIAL),
            "Not yet checked" to names(Have.NOT_CHECKED),
            "Missing" to names(Have.MISSING),
            "Not available offline" to names(Have.NOT_PUBLISHED, Have.NOT_SUPPORTED),
        ).filter { it.second.isNotEmpty() }.joinToString(". ", postfix = ".") { "${it.first}: ${it.second}" }
    }

    /** Offline coverage of a planned route, disclosed before travel (MX08). */
    data class RouteCoverage(
        val neededAreas: List<String>,
        val installedAreas: List<String>,
        val missingLabels: List<String>,
        val unpublishedPoints: Int,
        val routes: Have,
        val wholeUkMap: Boolean,
    )

    fun routeCoverage(
        manifest: RegionalMapManifest?,
        installed: InstalledGeneration?,
        points: List<LatLon>,
        wholeUkMap: Boolean,
        /** Whether DMN, which plans the routes, could find a new one along this journey with no signal (5.2). */
        dmnRoutes: Have,
    ): RouteCoverage? {
        if (points.isEmpty()) return null
        val sampled = sample(points, 2000)
        val routes = dmnRoutes
        val m = manifest ?: installed?.manifest
            ?: return RouteCoverage(emptyList(), emptyList(), emptyList(), 0, routes, wholeUkMap)
        val plan = RegionDownloadPlanner.alongRoute(m, sampled, 2.0)
        val needed = plan.packages.filter { it != "base" }
        val have = installed?.takeIf { "base" in it.verified }?.verified.orEmpty()
        val got = needed.filter { it in have }
        val missing = needed.filter { it !in have }.map { label(m.bounds[it]) }.distinct()
        return RouteCoverage(needed, got, missing, plan.uncoveredPoints, routes, wholeUkMap)
    }

    fun routeCoverageText(c: RouteCoverage): String = buildString {
        append("Before you go: ")
        if (c.wholeUkMap) append("the whole UK map is on this phone. ")
        else if (c.neededAreas.isEmpty() && c.unpublishedPoints == 0) append("no area maps cover this route. ")
        else if (c.neededAreas.isNotEmpty()) {
            append("${c.installedAreas.size} of ${c.neededAreas.size} detailed map areas on this route are downloaded. ")
            if (c.missingLabels.isNotEmpty()) append("Not downloaded: ${c.missingLabels.joinToString(", ")}. ")
        }
        if (!c.wholeUkMap && c.unpublishedPoints > 0) append("Part of this route has no detailed offline map published. ")
        append(when (c.routes) {
            Have.READY -> "Offline routes cover this journey."
            Have.PARTIAL -> "Offline routes cover only part of this journey. Without signal, guidance carries on along this route, but a diversion may not be found."
            else -> "Without signal, guidance carries on along this route, but a new route can't be worked out."
        })
    }.trim()

    private fun sample(points: List<LatLon>, maxPoints: Int): List<LatLon> {
        if (points.size <= maxPoints) return points
        val step = points.size.toDouble() / maxPoints
        return (0 until maxPoints).map { points[(it * step).toInt()] } + points.last()
    }
}
