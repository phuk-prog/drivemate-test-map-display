package com.drivemate.app.nav

/** A GPS gap must not pull the camera away from a driver's chosen view. */
object PositionCameraPolicy {
    fun mayAutoRecenter(navigating: Boolean, following: Boolean, reliable: Boolean): Boolean =
        navigating && !following && reliable

    fun mayInitiallyCenter(hasPriorView: Boolean, reliable: Boolean): Boolean =
        !hasPriorView || reliable
}
