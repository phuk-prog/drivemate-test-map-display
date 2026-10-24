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
}
