package com.drivemate.app.ui
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstUseTripHelpTest {
 @get:Rule val compose=createComposeRule()
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private val prefs get()=context.getSharedPreferences("first_use_help",Context.MODE_PRIVATE)
 @Before fun clear() { prefs.edit().clear().commit() }
 @Test fun firstUseShowsShortTripHelpAndOneAcknowledgement() {
  compose.setContent { MaterialTheme { FirstUseTripHelp() } }
  compose.onNodeWithText("Your first trip").assertIsDisplayed()
  compose.onNodeWithText("Got it").assertIsDisplayed()
  assertFalse(prefs.getBoolean("trip_help_seen",false))
 }
 @Test fun gotItDismissesAndPersistsAcknowledgement() {
  compose.setContent { MaterialTheme { FirstUseTripHelp() } }
  compose.onNodeWithText("Got it").performClick()
  compose.onNodeWithText("Your first trip").assertDoesNotExist()
  compose.runOnIdle { assertTrue(prefs.getBoolean("trip_help_seen",false)) }
 }
 @Test fun acknowledgedHelpRemainsAbsentWhenComponentRemounts() {
  val mounted=mutableStateOf(true)
  compose.setContent { MaterialTheme { if(mounted.value) FirstUseTripHelp() } }
  compose.onNodeWithText("Got it").performClick()
  compose.runOnIdle { mounted.value=false }
  compose.waitForIdle()
  compose.runOnIdle { mounted.value=true }
  compose.onNodeWithText("Your first trip").assertDoesNotExist()
 }
 @Test fun tutorialAcknowledgementNeverChangesJourneyHistoryConsent() {
  val privacy=context.getSharedPreferences("drivemate_local_privacy",Context.MODE_PRIVATE)
  privacy.edit().clear().commit()
  compose.setContent { MaterialTheme { FirstUseTripHelp() } }
  compose.onNodeWithText("Got it").performClick()
  compose.runOnIdle { assertFalse(privacy.contains("journeyHistoryEnabled")); assertEquals(setOf("trip_help_seen"),prefs.all.keys) }
 }
}
