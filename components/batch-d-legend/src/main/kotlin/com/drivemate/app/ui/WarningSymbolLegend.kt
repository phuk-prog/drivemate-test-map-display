package com.drivemate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.data.HazardType

/** Fixed settings order. Symbols and labels come from the same types used by map pins. */
internal fun warningLegendEntries(navigating: Boolean): List<HazardType> {
    if (navigating) return emptyList()
    return listOf(
        HazardType.CAMERA, HazardType.RED_LIGHT, HazardType.AVERAGE_SPEED,
        HazardType.CLOSURE, HazardType.CRASH, HazardType.OBJECT, HazardType.BROKEN_CAR,
        HazardType.HAZARD, HazardType.WEATHER, HazardType.ANIMALS, HazardType.POLICE,
        HazardType.TRAFFIC, HazardType.LEVEL_CROSSING, HazardType.ROAD_SIGN_HAZARD,
        HazardType.ANIMAL_SIGN, HazardType.SCHOOL, HazardType.SPEED_BUMP,
        HazardType.SHARP_BEND, HazardType.POTHOLE, HazardType.TOLL_BOOTH,
    ).filter { it.onMap }
}

/** Read-only legend: no symbol tap handlers, dialogs or navigation side effects. */
@Composable
internal fun WarningSymbolLegend(navigating: Boolean) {
    val entries = warningLegendEntries(navigating)
    if (entries.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("warning-symbol-legend"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { type ->
            Row(Modifier.fillMaxWidth().testTag("warning-symbol-${type.name}").padding(vertical = 4.dp)) {
                Text(type.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Text(type.label, modifier = Modifier.testTag("warning-label-${type.name}"))
            }
        }
    }
}
