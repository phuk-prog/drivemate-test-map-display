package com.drivemate.app.nav

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

/** Automated Android simulation; exercises the production adapter, with no handset needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GpsSensorAdapterTest {
    private fun adapter(manager: SensorManager) = MotionSensors(object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getSystemService(name: String): Any? = if (name == Context.SENSOR_SERVICE) manager else super.getSystemService(name)
    })
    private fun manager(gravity: Boolean = true): SensorManager {
        val m = mock(SensorManager::class.java)
        `when`(m.getDefaultSensor(Sensor.TYPE_GYROSCOPE)).thenReturn(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE))
        `when`(m.getDefaultSensor(if (gravity) Sensor.TYPE_GRAVITY else Sensor.TYPE_ACCELEROMETER))
            .thenReturn(ShadowSensor.newInstance(if (gravity) Sensor.TYPE_GRAVITY else Sensor.TYPE_ACCELEROMETER))
        return m
    }
    @Test fun missingSensorsDoNotRegisterAndStillAllowMeasuredGpsHeading() {
        val m = mock(SensorManager::class.java); val a = adapter(m)
        assertFalse(a.available); a.start(); a.estimator.onGps(90f, 10f, 1000)
        assertEquals(90.0, a.estimator.heading!!, 0.0)
        verify(m, never()).registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), anyInt())
    }
    @Test fun stopClearsHeadingEvenWhenSensorsWereUnavailable() {
        val a = adapter(mock(SensorManager::class.java))
        a.estimator.onGps(90f, 10f, 1000); a.stop(); assertNull(a.estimator.heading)
    }
    @Test fun deniedRegistrationFallsBackWithoutCrashing() {
        val m = manager()
        doThrow(SecurityException("Synthetic sensor denial")).`when`(m).registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), eq(SensorManager.SENSOR_DELAY_FASTEST))
        doReturn(true).`when`(m).registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), eq(SensorManager.SENSOR_DELAY_GAME))
        val a = adapter(m); a.start(); a.start()
        verify(m, times(1)).registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), eq(SensorManager.SENSOR_DELAY_FASTEST))
        verify(m, times(2)).registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), eq(SensorManager.SENSOR_DELAY_GAME))
        a.stop(); verify(m).unregisterListener(a)
    }
    @Test fun bothRegistrationFailuresAreContainedAndPartialListenersRemoved() {
        val m = manager()
        `when`(m.registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), anyInt())).thenThrow(SecurityException("Synthetic denial"))
        val a = adapter(m); a.start(); assertNull(a.estimator.heading)
        verify(m).unregisterListener(a)
    }
    @Test fun accelerometerIsUsedWhenGravitySensorIsMissing() {
        val m = manager(false)
        `when`(m.registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), anyInt())).thenReturn(true)
        val a = adapter(m); assertTrue(a.available); a.start()
        verify(m).registerListener(a, m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        a.stop()
    }
    @Test fun successfulStopAndRestartDoNotReusePreviousHeading() {
        val m = manager(); `when`(m.registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), anyInt())).thenReturn(true)
        val a = adapter(m); a.start(); a.estimator.onGps(90f, 10f, 1000)
        a.stop(); assertNull(a.estimator.heading); a.start(); assertNull(a.estimator.heading)
    }
}
