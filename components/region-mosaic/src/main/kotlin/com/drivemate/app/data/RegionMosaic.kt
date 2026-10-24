package com.drivemate.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Several published regions drawn as one map. Each region's archive is its own source, and every
 * layer that reads the main map source is repeated for it, just below the original, so the style's
 * drawing order holds across regions and the active region stays on top where they overlap.
 */
internal object RegionMosaic {
    const val SOURCE = "drivemate"
    /** Separates a layer's own id from the region its copy draws: `road-primary@cheshire`. */
    const val SEPARATOR = '@'

    fun sourceId(region: String) = "$SOURCE$SEPARATOR$region"

    /** A layer's id without any region suffix. */
    fun baseId(id: String) = id.substringBefore(SEPARATOR)

    /** Adds [neighbours] (region id to archive address) to [style]; returns the same object. */
    fun add(style: JSONObject, neighbours: List<Pair<String, String>>): JSONObject {
        if (neighbours.isEmpty()) return style
        val sources = style.getJSONObject("sources")
        val own = sources.getJSONObject(SOURCE)
        for ((id, url) in neighbours) sources.put(sourceId(id), JSONObject(own.toString()).put("url", "pmtiles://$url"))
        val layers = style.getJSONArray("layers")
        val out = JSONArray()
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            if (layer.optString("source") == SOURCE) for ((id, _) in neighbours)
                out.put(JSONObject(layer.toString()).put("id", "${layer.getString("id")}$SEPARATOR$id").put("source", sourceId(id)))
            out.put(layer)
        }
        style.put("layers", out)
        return style
    }
}
