package com.drivemate.app.data

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon

/**
 * Kinds of place you can look for along your route, or near your destination. [categories]: as in
 * DriveMate's place list (OpenStreetMap's tags, "amenity fuel" and so on).
 */
enum class PlaceKind(val label: String, val emoji: String, val categories: Set<String>) {
    FUEL("Petrol", "⛽", setOf("amenity fuel")),
    EV("EV charging", "🔌", setOf("amenity charging station")),
    FOOD("Food", "🍔", setOf("amenity fast food", "amenity restaurant")),
    COFFEE("Coffee", "☕", setOf("amenity cafe")),
    PARKING("Parking", "🅿️", setOf("amenity parking")),
    TOILETS("Toilets", "🚻", setOf("amenity toilets")),
    SERVICES("Services", "🛣️", setOf("highway services")),
}

/** A place found along the route: how far ahead it is and how far off the road. */
class RoutePlace(
    val place: Place,
    val kind: PlaceKind,
    val ahead: Double,
    val offRoute: Double,
    /** Petrol prices, for fuel stations that publish them. */
    val prices: String = "",
    /**
     * Estimated extra driving time to call here and rejoin the route, by road (usual traffic);
     * null when it couldn't be worked out (shown as "detour not checked", never guessed).
     */
    val detourSeconds: Double? = null,
)

/**
 * Finds petrol stations, chargers, food etc. near your route from DriveMate's place list on the phone
 * (1.14: nothing is sent; it used to ask Overpass with your route's box). [places] is swapped in tests.
 */
object AlongRoute {
    internal var places: (Set<String>, Double, Double, Double, Double) -> List<Place> = OwnPlaces::ofCategories

    /**
     * @param line the road ahead (a few km to a few tens of km), thinned to a point every ~100 m.
     * @param maxOff how far from the road a place may be, in metres.
     */
    fun search(kind: PlaceKind, line: List<LatLon>, maxOff: Double = 600.0): List<RoutePlace> {
        if (line.size < 2) return emptyList()
        val pad = maxOff / 111_000.0 + 0.002
        val found = places(kind.categories, line.minOf { it.lat } - pad, line.minOf { it.lon } - pad * 1.7,
            line.maxOf { it.lat } + pad, line.maxOf { it.lon } + pad * 1.7)

        // Distance along the line, for "2.3 mi ahead".
        val cum = DoubleArray(line.size)
        for (i in 1 until line.size) cum[i] = cum[i - 1] + Geo.distance(line[i - 1], line[i])

        val out = ArrayList<RoutePlace>()
        for (place in found) {
            val p = place.pos
            var best = Double.MAX_VALUE
            var at = 0.0
            for (j in 0 until line.size - 1) {
                val pr = Geo.project(p, line[j], line[j + 1])
                if (pr.distance < best) {
                    best = pr.distance
                    at = cum[j] + (cum[j + 1] - cum[j]) * pr.t
                }
            }
            if (best > maxOff) continue
            out += RoutePlace(Place(place.name.ifBlank { kind.label }, place.address, p), kind, at, best)
        }
        return out.sortedBy { it.ahead }.take(30)
    }

    /** Places of a kind around a point (e.g. parking near your destination), nearest first. */
    fun near(kind: PlaceKind, center: LatLon, radius: Double = 800.0): List<RoutePlace> {
        val dLat = radius / 111_000.0
        val dLon = dLat / Math.cos(Math.toRadians(center.lat)).coerceAtLeast(0.1)
        return places(kind.categories, center.lat - dLat, center.lon - dLon, center.lat + dLat, center.lon + dLon)
            .map { RoutePlace(Place(it.name.ifBlank { kind.label }, it.address, it.pos), kind, 0.0, Geo.distance(center, it.pos)) }
            .filter { it.offRoute <= radius }
            .sortedBy { it.offRoute }.take(20)
    }

    /** Where to go at the very end: the nearest car park, if listed. (Entrances are not in the place list.) */
    class ArrivalHelp(val entrance: LatLon?, val parking: Place?)

    fun arrivalHelp(dest: LatLon): ArrivalHelp =
        ArrivalHelp(null, near(PlaceKind.PARKING, dest, radius = 250.0).firstOrNull()?.place?.let { Place(it.name.ifBlank { "Car park" }, it.address, it.pos) })
}
