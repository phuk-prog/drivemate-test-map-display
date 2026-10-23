package com.drivemate.app.nav

data class Progress(
    /** Metres travelled along the route. */
    val along: Double,
    /** How far the car is from the route line, in metres. */
    val offRoute: Double,
    /** Index of the maneuver to show and announce. */
    val nextStep: Int,
    /** Metres to that maneuver — or to the exit, while driving round a roundabout. */
    val distanceToNext: Double,
    /** True while the car is on a roundabout, heading for its exit. */
    val onRoundabout: Boolean,
    /** A maneuver coming up straight after the shown one ("Then …"), if close. */
    val thenStep: Int?,
    /** The step whose road the car is on now (for the street name). */
    val currentStep: Int,
    val remaining: Double,
    val remainingSeconds: Double,
    val snapped: LatLon,
    val segment: Int,
    /** Speed limit (km/h) of the road the car is on, if known. */
    val speedLimit: Double?,
)

