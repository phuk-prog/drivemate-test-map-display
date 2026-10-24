package com.drivemate.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.drivemate.app.data.OfflineAutoUpdates
import com.drivemate.app.data.MapData
import com.drivemate.app.data.OfflineStorage
import androidx.compose.material3.OutlinedTextField

/** This control lives inside Offline Maps and saves the choice immediately. */
@Composable
internal fun OfflineAutoUpdateControl() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(OfflineAutoUpdates.enabled(ctx)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Automatic area updates over Wi-Fi", Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { requested ->
            if (OfflineAutoUpdates.setEnabled(ctx, requested)) enabled = requested
        })
    }
    OfflineRegionSearchControl()
}

/** Published names and sizes only; an unpublished county or country is never offered. */
@Composable
internal fun OfflineRegionSearchControl(
    options: (String) -> List<Pair<String, Long>> = { query ->
        MapData.offlineRegionOptions(query).map { it.name to it.map.bytes }
    },
) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
        label = { Text("Search regions") }, modifier = Modifier.fillMaxWidth())
    val matches = options(query)
    matches.take(20).forEach { (name, bytes) -> Text("$name · ${OfflineStorage.mb(bytes)}") }
    if (matches.isEmpty()) Text("No published regions match.")
    if (matches.size > 20) Text("Showing the nearest 20 regions. Type a name to narrow the list.")
}
