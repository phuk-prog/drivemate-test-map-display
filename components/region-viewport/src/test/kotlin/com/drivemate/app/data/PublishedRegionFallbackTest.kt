package com.drivemate.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Independent geometric coverage expectations, NOT verified OSM road or GPU tile content. */
class PublishedRegionFallbackTest {
    private val asset = JSONObject().put("tag", "map-data-region-demo-20261023")
        .put("sha256", "a".repeat(64)).put("bytes", 2000000)
    private fun region(id: String, w: Double, s: Double, e: Double, n: Double) =
        JSONObject().put("id", id).put("name", id).put("bounds", listOf(w, s, e, n))
            .put("manifest", JSONObject(asset.toString()).put("tag", "map-data-region-$id-20261023"))
            .put("map", JSONObject(asset.toString()).put("tag", "map-data-region-$id-20261023")
                .put("name","drivemate-v1.pmtiles").put("layout","drivemate-v1"))
    private fun catalog(): PublishedRegionCatalog {
        val regions = JSONObject()
            .put("stockport", region("stockport",-2.3,53.3,-2.1,53.5))
            .put("greater", region("greater",-2.6,53.1,-1.9,53.8))
            .put("east", region("east",-2.09,53.3,-1.8,53.6))
        return PublishedRegionCatalog.parse(JSONObject()
            .put("schema",1).put("coverage","partial").put("regions",regions).toString())
    }
    private fun v(w:Double,s:Double,e:Double,n:Double) = PublishedRegionCatalog.Bounds(w,s,e,n)

    @Test fun partialViewportUsesLargestPublishedCoverageRatherThanSmallestCentreRegion() {
        assertEquals("greater",catalog().selectForViewport(53.4,-2.2,v(-3.0,53.0,-1.6,54.0))?.id)
    }
    @Test fun partialCoverageRemainsHelpfulWhenNoArchiveCoversWholeViewport() {
        assertEquals("greater",catalog().selectForViewport(53.4,-2.2,v(-2.9,52.9,-1.7,54.0))?.id)
    }
    @Test fun fullyCoveredCompactViewRetainsSmallestRegion() {
        assertEquals("stockport",catalog().selectForViewport(53.4,-2.2,v(-2.28,53.32,-2.12,53.48))?.id)
    }
    @Test fun noPublishedFocusRemainsUnavailable() {
        assertNull(catalog().selectForViewport(50.1,-4.1,v(-5.0,50.0,-3.5,50.5)))
    }
    @Test fun invalidViewportDoesNotChangeDefaultFocusRegion() {
        assertEquals("stockport",catalog().selectForViewport(53.4,-2.2,v(Double.NaN,53.1,-1.7,53.8))?.id)
    }
    @Test fun noAlternativeRegionContainingFocusKeepsAvailableRegion() {
        assertEquals("east",catalog().selectForViewport(53.4,-1.82,v(-2.4,53.0,-1.5,54.0))?.id)
    }
}
