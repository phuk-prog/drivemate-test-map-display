package com.drivemate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoutePanelTest {
    @get:Rule val compose=createComposeRule()
    private fun show() { compose.setContent {
        MaterialTheme { Box(Modifier.width(360.dp).height(600.dp)) {
            Text("Route map",Modifier.testTag("map"))
            DraggableRoutePanel(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(240.dp)) { Text("Route options",Modifier.testTag("options")) }
            }
        } }
    } }
    @Test fun draggingDownRevealsMoreOfTheMapAndKeepsTheHandleVisible() {
        show();val before=compose.onNodeWithTag("route-panel-surface").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("route-panel-handle").performTouchInput{swipeDown()}
        val after=compose.onNodeWithTag("route-panel-surface").fetchSemanticsNode().boundsInRoot.top
        assertTrue(after>before);compose.onNodeWithTag("route-panel-handle").assertIsDisplayed()
    }
    @Test fun draggingBackUpRestoresTheRouteOptions() {
        show();val before=compose.onNodeWithTag("route-panel-surface").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("route-panel-handle").performTouchInput{swipeDown()}
        compose.onNodeWithTag("route-panel-handle").performTouchInput{swipeUp()}
        assertTrue(compose.onNodeWithTag("route-panel-surface").fetchSemanticsNode().boundsInRoot.top<=before+1f)
        compose.onNodeWithTag("options").assertIsDisplayed()
    }
    @Test fun accessibleActionsRevealTheMapAndRestoreOptionsWithoutDragging() {
        show();val before=compose.onNodeWithTag("route-panel-surface").fetchSemanticsNode().boundsInRoot.top
        val actions=compose.onNodeWithTag("route-panel-handle").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first{it.label=="Show map"}.action()) }
        assertTrue(compose.onNodeWithTag("route-panel-surface").fetchSemanticsNode().boundsInRoot.top>before)
        compose.runOnIdle { assertTrue(actions.first{it.label=="Show route options"}.action()) }
        assertEquals(before,compose.onNodeWithTag("route-panel-surface").fetchSemanticsNode().boundsInRoot.top,1f)
    }
    @Test fun dragBoundsNeverHideTheHandleOrAllowNegativeOffset() {
        for(height in listOf(0,20,48,288,900)) {
            val max=RoutePanelDrag.limit(height,48)
            assertEquals((height-48).coerceAtLeast(0).toFloat(),max,0f)
            assertEquals(0f,RoutePanelDrag.move(0f,-1000f,max),0f)
            assertEquals(max,RoutePanelDrag.move(0f,10000f,max),0f)
        }
    }
    @Test fun invalidMotionCannotCorruptPanelPlacement() {
        for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) assertEquals(20f,RoutePanelDrag.move(20f,v,100f),0f)
    }
}
