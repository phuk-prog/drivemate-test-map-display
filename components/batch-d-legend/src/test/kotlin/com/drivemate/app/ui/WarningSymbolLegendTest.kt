package com.drivemate.app.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.drivemate.app.data.HazardType
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
class WarningSymbolLegendTest {
 @get:Rule val compose = createComposeRule()
 @Test fun speedCamerasComeFirstInFixedOrder() { assertEquals(listOf(HazardType.CAMERA,HazardType.RED_LIGHT,HazardType.AVERAGE_SPEED),warningLegendEntries(false).take(3)) }
 @Test fun everyExistingMapTypeAppearsExactlyOnce() { val rows=warningLegendEntries(false);assertEquals(HazardType.entries.filter{it.onMap}.toSet(),rows.toSet());assertEquals(rows.size,rows.distinct().size) }
 @Test fun nonMapTypesAreExcluded() { assertTrue(warningLegendEntries(false).all{it.onMap}); assertFalse(warningLegendEntries(false).contains(HazardType.SHARP_BEND)) }
 @Test fun camerasPrecedeEveryNonCameraWarning() { val types=warningLegendEntries(false); assertTrue(types.take(3).all { it.isCamera }); assertTrue(types.drop(3).none { it.isCamera }) }
 @Test fun settingsDirectoryFindsLegendOutsideNavigation() { assertEquals(listOf(SettingsSection.SYMBOLS),settingsSectionsMatching("warning symbols",false)) }
 @Test fun settingsDirectoryHidesLegendDuringNavigation() { assertFalse(SettingsSection.SYMBOLS in settingsSectionsMatching("",true)); assertTrue(settingsSectionsMatching("warning symbols",true).isEmpty()) }
 @Test fun navigatingHasNoLegendEntries() { assertTrue(warningLegendEntries(true).isEmpty()) }
 @Test fun actualComposeShowsExactExistingSymbolsAndLabels() {
  compose.setContent { MaterialTheme { WarningSymbolLegend(false) } }
  compose.onNodeWithTag("warning-symbol-legend").assertExists()
  warningLegendEntries(false).forEach { type ->
   compose.onNodeWithTag("warning-symbol-${type.name}").assertExists().assertHasNoClickAction()
   compose.onNodeWithTag("warning-label-${type.name}").assertTextEquals(type.label)
   compose.onAllNodesWithText(type.emoji).assertCountEquals(warningLegendEntries(false).count{it.emoji==type.emoji})
  }
 }
 @Test fun actualComposeHasNoLegendWhileNavigating() { compose.setContent { MaterialTheme { WarningSymbolLegend(true) } }; compose.onNodeWithTag("warning-symbol-legend").assertDoesNotExist() }
 @Test fun enteringNavigationImmediatelyRemovesAnOpenLegend() {
  val navigating=mutableStateOf(false)
  compose.setContent { MaterialTheme { WarningSymbolLegend(navigating.value) } }
  compose.onNodeWithTag("warning-symbol-legend").assertExists()
  compose.runOnIdle { navigating.value=true }
  compose.onNodeWithTag("warning-symbol-legend").assertDoesNotExist()
 }
}
