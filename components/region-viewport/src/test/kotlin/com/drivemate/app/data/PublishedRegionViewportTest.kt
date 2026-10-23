package com.drivemate.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Synthetic region-boundary test only. No UK road or legal restriction oracle
 * can be inferred from the boundaries used here.
 */
class PublishedRegionViewportTest {
    private fun entry(id: String, west: Double, south: Double, east: Double, north: Double): JSONObject {
        val asset = JSONObject().put("tag", "map-data-region-$id-20261023")
            .put("sha256", "a".repeat(64)).put("bytes", 2000000)
        return JSONObject().put("id", id).put("name", id).put("bounds", listOf(west,south,east,north))
            .put("manifest", JSONObject(asset.toString()))
            .put("map", JSONObject(asset.toString()).put("name","drivemate-v1.pmtiles").put("layout","drivemate-v1"))
    }
    private fun catalog(): PublishedRegionCatalog {
        val values = JSONObject()
            .put("stockport", entry("stockport", -2.30,53.30,-2.10,53.50))
            .put("greater", entry("greater", -2.60,53.10,-1.90,53.80))
            .put("east", entry("east", -2.09,53.30,-1.80,53.60))
        return PublishedRegionCatalog.parse(JSONObject()
            .put("schema",1).put("coverage","partial").put("regions",values).toString())
    }
    private fun view(w:Double,s:Double,e:Double,n:Double)= PublishedRegionCatalog.Bounds(w,s,e,n)

    @Test fun centreOnlyPreservesSmallestAvailableRegion() {
        assertEquals("stockport", catalog().select(53.40,-2.20)?.id)
    }
    @Test fun widerPublishedRegionCoversEntireVisibleViewport() {
        assertEquals("greater",catalog().selectForViewport(53.40,-2.20,view(-2.45,53.20,-2.00,53.60))?.id)
    }
    @Test fun compactViewportUsesSmallestRegionToLimitDownload() {
        assertEquals("stockport",catalog().selectForViewport(53.40,-2.20,view(-2.29,53.31,-2.11,53.49))?.id)
    }
    @Test fun exactRegionBoundaryCountsAsCovered() {
        assertEquals("stockport",catalog().selectForViewport(53.40,-2.20,view(-2.30,53.30,-2.10,53.50))?.id)
    }
    @Test fun noPublishedRegionCanCoverWideViewReturnsLargestAvailableOverlap() {
        assertEquals("greater",catalog().selectForViewport(53.40,-2.20,view(-4.0,51.0,0.1,54.0))?.id)
    }
    @Test fun neverChooseBiggerRegionThatDoesNotContainActualFocus() {
        assertEquals("east",catalog().selectForViewport(53.40,-1.85,view(-2.0,53.35,-1.7,53.55))?.id)
    }
    @Test fun invalidOrReversedViewportDoesNotCrashAndFallsBack() {
        assertEquals("stockport",catalog().selectForViewport(53.40,-2.20,view(-1.8,53.6,-2.5,53.1))?.id)
        assertEquals("stockport",catalog().selectForViewport(53.40,-2.20,view(Double.NaN,53.1,-2.0,53.6))?.id)
    }
    @Test fun missingViewportKeepsPreviousSelectionSemantics() {
        assertEquals("stockport",catalog().selectForViewport(53.40,-2.20,null)?.id)
    }
    @Test fun missingGpsFocusUsesStartupFallbackNotUnwrapNull() {
        assertEquals("stockport",catalog().selectForViewport(null,null,view(-2.45,53.2,-2.0,53.6))?.id)
    }
    @Test fun noPublishedLocationIsReportedAsUnavailable() {
        assertNull(catalog().selectForViewport(51.0,0.0,view(-1.0,50.0,1.0,52.0)))
    }
    @Test fun invalidOrNonFiniteGpsNeverPicksExistingRegion() {
        assertNull(catalog().selectForViewport(Double.NaN,-2.20,view(-2.4,53.2,-2.0,53.6)))
    }
    @Test fun overlappingRegionsOrderDoesNotAffectSelection() {
        val c=catalog()
        assertEquals("greater",c.selectForViewport(53.40,-2.20,view(-2.40,53.2,-2.0,53.6))?.id)
    }
}
