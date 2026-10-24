package com.drivemate.app.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Brief local first-use help; only the acknowledgement is stored, with no journey data. */
@Composable
fun FirstUseTripHelp() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("first_use_help", Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(!preferences.getBoolean("trip_help_seen", false)) }
    fun close() {
        preferences.edit().putBoolean("trip_help_seen", true).apply()
        visible = false
    }
    if (visible) AlertDialog(
        onDismissRequest = ::close,
        title = { Text("Your first trip") },
        text = { Text("Search for a place and check its area before choosing Directions. Review your route, then tap Start when ready. During a trip, tap the journey bar to add a stop. After arrival, the map stays open so you can finish the journey or find nearby parking.") },
        confirmButton = { TextButton(onClick = ::close) { Text("Got it") } },
    )
}
