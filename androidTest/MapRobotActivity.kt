package com.drivemate.app.map

import android.app.Activity
import android.os.Bundle
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

/** Test-only host: built solely in androidTest, never in the shipped app. */
class MapRobotActivity : Activity() {
    lateinit var mapView: MapView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        MapLibre.getInstance(applicationContext)
        mapView = MapView(this)
        mapView.onCreate(state)
        setContentView(mapView)
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}
