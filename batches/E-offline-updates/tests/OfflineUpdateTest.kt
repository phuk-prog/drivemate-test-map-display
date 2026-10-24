package com.drivemate.app.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class OfflineUpdateTest {
    private fun catalog(): PublishedRegionCatalog {
        fun region(id: String, name: String, west: Double, bytes: Int): JSONObject {
            fun asset() = JSONObject().put("tag", "map-data-region-$id-v1")
                .put("sha256", "a".repeat(64)).put("bytes", bytes)
            return JSONObject().put("id", id).put("name", name)
                .put("bounds", listOf(west, 50.0, west + 1.0, 51.0))
                .put("manifest", asset()).put("map", asset().put("name", "drivemate-v1.pmtiles").put("layout", "drivemate-v1"))
        }
        return PublishedRegionCatalog.parse(JSONObject().put("schema", 1).put("coverage", "partial")
            .put("regions", JSONObject().put("alpha", region("alpha", "Alpha County", -3.0, 100))
                .put("beta", region("beta", "Beta County", 0.0, 200))).toString())
    }
    @Test fun installedCatalogSelectsOnlyCoveredBounds() { assertEquals("alpha", catalog().select(50.5, -2.5)?.id) }
    @Test fun missingCoverageNeverInvented() { assertNull(catalog().select(55.0, -5.0)) }
    @Test fun invalidPositionRejected() { assertNull(catalog().select(Double.NaN, 0.0)) }
    @Test fun declaredDownloadBytesPreserved() { assertEquals(100L, catalog().regions.first().map.bytes) }
    @Test fun partialPublicationRemainsPartial() { assertEquals("partial", catalog().coverage) }
    @Test fun invalidBoundsFailClosed() {
        val invalid = """{"schema":1,"coverage":"partial","regions":{"bad":{"id":"bad","name":"Bad","bounds":[2,50,1,51]}}}"""
        assertTrue(runCatching { PublishedRegionCatalog.parse(invalid) }.isFailure)
    }

    private fun search(query: String, lat: Double?, lon: Double?): List<PublishedRegionCatalog.Region> {
        val method = PublishedRegionCatalog::class.java.methods.singleOrNull { it.name == "search" }
        assertNotNull("No region search component exists at this source revision", method)
        @Suppress("UNCHECKED_CAST")
        return method!!.invoke(catalog(), query, lat, lon) as List<PublishedRegionCatalog.Region>
    }
    @Test fun partialNamesMatchAsTyped() { assertEquals(listOf("alpha"), search("PHA", null, null).map { it.id }) }
    @Test fun nearbyPublishedRegionsFirst() { assertEquals("beta", search("", 50.5, 0.5).first().id) }
    @Test fun searchResultsRetainByteSizes() { assertEquals(200L, search("beta", null, null).single().map.bytes) }
    @Test fun absentCountryIsNotFabricated() { assertTrue(search("Scotland", null, null).isEmpty()) }

    private fun permits(enabled: Boolean, current: Long, scheduled: Long): Boolean {
        val type = runCatching { Class.forName("com.drivemate.app.data.OfflineUpdatePolicy") }.getOrNull()
        assertNotNull("No automatic-update approval component exists at this source revision", type)
        val approvalType = Class.forName("com.drivemate.app.data.OfflineUpdatePolicy\$Approval")
        val approval = approvalType.constructors.single().newInstance(enabled, current)
        return type!!.methods.single { it.name == "permits" }.invoke(type.getField("INSTANCE").get(null), approval, scheduled) as Boolean
    }
    @Test fun neverApprovedIsDenied() { assertFalse(permits(false, 0, 0)) }
    @Test fun approvedCurrentWorkerIsAllowed() { assertTrue(permits(true, 1, 1)) }
    @Test fun revocationStopsCurrentWorker() { assertFalse(permits(false, 2, 1)) }
    @Test fun reapprovalDoesNotReviveOldWorker() { assertFalse(permits(true, 3, 1)) }
    @Test fun globalApprovalIndependentOfAreaIdentity() { repeat(20) { assertTrue(permits(true, 4, 4)) } }
}
