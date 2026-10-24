package com.drivemate.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.nav.Shield

/** Only removes road references already rendered as shields; never derives sign or lane data. */
internal fun navigationRoadText(road: String, shields: List<Shield>): String {
    val refs = shields.map { it.ref }.toSet()
    return road.trim().split(Regex("\\s+"))
        .filterNot { token ->
            val parts = token.split(',', ';').filter { it.isNotBlank() }
            parts.isNotEmpty() && parts.all { it in refs }
        }
        .joinToString(" ")
}

/** Preserve the joined road and sign destination; omit only exact duplicates and rendered refs. */
internal fun navigationPlaces(road: String, destination: String, shields: List<Shield>): List<String> =
    listOf(road, destination).map { navigationRoadText(it, shields) }.filter { it.isNotBlank() }.distinct()

/** Keep the provider's explicit exit side; signs are rendered separately below the action. */
internal fun navigationNextExitAction(instruction: String): String =
    instruction.substringBefore(",").substringBefore(" towards ").trim()

internal val NavigationCardNavy = Color(0xFF152238)

/** The same distance/action/sign structure for integrated and diagram guidance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NavigationHeader(
    distanceText: String?,
    now: Boolean,
    action: String,
    road: String = "",
    shields: List<Shield> = emptyList(),
    exitNumber: String = "",
    hint: String = "",
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    destination: String = "",
    showArrow: Boolean = true,
) {
    val signs = shields.distinctBy { it.ref }
    val places = navigationPlaces(road, destination, signs)
    Row(modifier.fillMaxWidth().testTag("navigation-header").padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // A fixed icon column keeps the action in the same place when distance becomes Now.
        if (showArrow) Box(Modifier.width(68.dp).sizeIn(minHeight = 68.dp).testTag("navigation-arrow"), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Grows with the phone's font size up to a cap, so "200 m" never clips at 200% (MG22/MX11).
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            distanceText?.let {
                val distanceSp = Accessibility.cappedSp(40f, fontScale, DrivingTextCaps.DISTANCE)
                // Full width: a text box narrower than its laid-out paragraph is reported as clipped
                // (CI run 38387056439: box 278 px, paragraph 540 px).
                Text(it, color = if (now) Amber else Color.White, fontSize = distanceSp.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().testTag("navigation-distance"))
            }
            // Essential wording wraps instead of disappearing behind a road or an ellipsis.
            val actionSp = Accessibility.cappedSp(22f, fontScale, DrivingTextCaps.ACTION)
            Text(action, color = Color.White, fontSize = actionSp.sp, lineHeight = (actionSp * 1.18f).sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().testTag("navigation-action"))
            if (exitNumber.isNotBlank() || signs.isNotEmpty() || places.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (exitNumber.isNotBlank()) NavigationBadge("J${exitNumber.trim().removePrefix("J").removePrefix("j").trim()}", Color(0xFF141414), Color.White)
                    signs.forEach { shield ->
                        val (bg, fg) = when (shield.kind) {
                            Shield.Kind.MOTORWAY -> Color(0xFF1F5FBF) to Color.White
                            Shield.Kind.PRIMARY -> Color(0xFF00703C) to Color(0xFFFFD200)
                            Shield.Kind.OTHER -> Color.White to Color(0xFF202124)
                        }
                        NavigationBadge(shield.ref, bg, fg)
                    }
                    places.forEachIndexed { index, place ->
                        val placeSp = Accessibility.cappedSp(18f, fontScale, DrivingTextCaps.ROAD)
                        Text(place, color = Color.White, fontSize = placeSp.sp, lineHeight = (placeSp * 1.22f).sp,
                            modifier = Modifier.fillMaxWidth().testTag(if (index == 0) "navigation-road" else "navigation-destination"))
                    }
                }
            }
            if (hint.isNotBlank()) Text(hint, color = Color.White, fontSize = 18.sp, lineHeight = 22.sp,
                modifier = Modifier.testTag("navigation-hint"))
        }
    }
}

@Composable
private fun NavigationBadge(text: String, bg: Color, fg: Color) {
    val shape = RoundedCornerShape(6.dp)
    Surface(color = bg, shape = shape, modifier = Modifier.border(1.5.dp, Color.White, shape)) {
        Text(text, color = fg, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp))
    }
}
