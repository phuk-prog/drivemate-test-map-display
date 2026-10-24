package com.drivemate.app.ui
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.drivemate.app.nav.Shield
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class NavigationCardRenderTest {
 @get:Rule val compose = createComposeRule()
 private fun render(distance:Double, speed:Float?=0f, age:Long=0L, roundabout:Boolean=false, arrived:Boolean=false) {
  compose.setContent {
   RenderCard(distance, speed, age, roundabout, arrived)
  }
  compose.onNodeWithTag("navigation-distance").assertExists().assertIsDisplayed()
  compose.onNodeWithText("Manchester").assertIsDisplayed()
  compose.onNodeWithText("M60").assertIsDisplayed()
 }
 @Test fun betweenJunctionsDistanceWithoutArrow() { render(1200.0); compose.onNodeWithTag("navigation-arrow").assertDoesNotExist() }
 @Test fun townApproachRevealsArrow() { render(120.0); compose.onNodeWithTag("navigation-arrow").assertIsDisplayed() }
 @Test fun nowRevealsArrow() { render(0.0); compose.onNodeWithTag("navigation-arrow").assertIsDisplayed() }
 @Test fun staleSpeedDoesNotRevealEarly() { render(300.0,30f,6000); compose.onNodeWithTag("navigation-arrow").assertDoesNotExist() }
 @Test fun motorwayApproachRevealsArrow() { render(450.0,30f); compose.onNodeWithTag("navigation-arrow").assertIsDisplayed() }
 @Test fun beyondApproachDoesNotShowArrow() { render(601.0,60f); compose.onNodeWithTag("navigation-arrow").assertDoesNotExist() }
 @Test fun roundaboutKeepsArrow() { render(1200.0,roundabout=true); compose.onNodeWithTag("navigation-arrow").assertIsDisplayed() }
 @Test fun arrivalRemovesTurnArrow() { render(0.0,arrived=true); compose.onNodeWithTag("navigation-arrow").assertDoesNotExist() }
 @Test fun invalidDistanceDoesNotInventApproach() { render(Double.NaN); compose.onNodeWithTag("navigation-arrow").assertDoesNotExist() }
 @Test fun compactSignLayoutHasArrowOnLeft() {
  render(100.0)
  val arrow=compose.onNodeWithTag("navigation-arrow").fetchSemanticsNode().boundsInRoot
  val distance=compose.onNodeWithTag("navigation-distance").fetchSemanticsNode().boundsInRoot
  org.junit.Assert.assertTrue(arrow.right <= distance.left)
  org.junit.Assert.assertTrue(arrow.width >= 68f)
 }
}
