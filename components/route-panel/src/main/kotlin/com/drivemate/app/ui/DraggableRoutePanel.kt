package com.drivemate.app.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Presentation bounds, not a navigation or legal rule. Keep the handle reachable at every size. */
internal object RoutePanelDrag {
    fun limit(heightPx: Int,handlePx: Int): Float = (heightPx.coerceAtLeast(0)-handlePx.coerceAtLeast(0)).coerceAtLeast(0).toFloat()
    fun move(offset: Float,delta: Float,limit: Float): Float {
        val max=limit.takeIf{it.isFinite() && it>=0f}?:0f
        val start=offset.takeIf{it.isFinite()}?:0f
        return (start+(delta.takeIf{it.isFinite()}?:0f)).coerceIn(0f,max)
    }
}

/** Drag only the handle, leaving route selection and the panel's own scrolling independent. */
@Composable
fun DraggableRoutePanel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    val handlePx=with(LocalDensity.current){48.dp.roundToPx()}
    var height by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    val limit=RoutePanelDrag.limit(height,handlePx)
    val visibleOffset=RoutePanelDrag.move(offset,0f,limit)
    val drag=rememberDraggableState { delta -> offset=RoutePanelDrag.move(offset,delta,limit) }
    Surface(shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),shadowElevation=12.dp,
        modifier=modifier.offset{IntOffset(0,visibleOffset.roundToInt())}.onSizeChanged{height=it.height}
            .testTag("route-panel-surface")) {
        Column {
            Box(Modifier.fillMaxWidth().height(48.dp).testTag("route-panel-handle")
                .draggable(drag,Orientation.Vertical)
                .semantics {
                    contentDescription="Drag route options down to see the map, or up to show options"
                    customActions=listOf(
                        CustomAccessibilityAction("Show map"){offset=limit;true},
                        CustomAccessibilityAction("Show route options"){offset=0f;true})
                },contentAlignment=Alignment.Center) {
                HorizontalDivider(Modifier.width(40.dp),thickness=4.dp)
            }
            content()
        }
    }
}
