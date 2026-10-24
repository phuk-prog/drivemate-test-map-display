package com.drivemate.app.ui
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drivemate.app.nav.Shield
internal val Amber = Color(0xFFFFB300)
@Composable internal fun RenderCard(distance:Double,speed:Float?,age:Long,roundabout:Boolean,arrived:Boolean) {
 NavigationHeader(distanceText=if(distance==0.0) "Now" else "1.2 km",now=distance==0.0,
 action="Continue straight",road="M60 Manchester",shields=listOf(Shield("M60", Shield.Kind.MOTORWAY)),
 icon={Text("→")},modifier=Modifier.width(360.dp),showArrow=showTurnArrow(arrived,"turn",distance,roundabout,speed,age))
}
