package com.drivemate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import com.drivemate.app.data.OfflineStorage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.Mode
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.OfflinePackagesService
import com.drivemate.app.data.RegionalDownloadStage
import com.drivemate.app.data.RegionalDownloadText
import com.drivemate.app.data.RegionalMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline maps: download stage with pause/resume (WA22), installed versus
 * available areas with what each contains and what's missing (MX08/WA21),
 * "before you go" coverage of the planned route, and storage with
 * user-chosen cleanup that explains the offline consequences (WA23).
 */
@Composable
internal fun OfflinePackagesPanel(vm: NavigationSession) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by RegionalMaps.status.collectAsState()
    val navigating = vm.mode == Mode.NAVIGATING
    var reload by remember { mutableIntStateOf(0) }
    var overview by remember { mutableStateOf<OfflinePackagesService.Overview?>(null) }
    var storage by remember { mutableStateOf<Pair<List<OfflinePackagesService.StorageRow>, String>?>(null) }
    var route by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<OfflinePackagesService.StorageRow?>(null) }
    var showAvailable by remember { mutableStateOf(false) }
    var failureBanner by remember { mutableStateOf(false) }
    var mobileAllowed by remember { mutableStateOf(RegionalMaps.mobileDownloadsAllowed(ctx)) }
    var confirmMobile by remember { mutableStateOf(false) }
    var areaRows by remember { mutableStateOf(emptyList<OfflinePackagesService.AreaStorageRow>()) }
    var selectedAreas by remember { mutableStateOf(emptySet<String>()) }
    var confirmAreas by remember { mutableStateOf(false) }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(st.failedSinceMs) {
        val since = st.failedSinceMs ?: return@LaunchedEffect
        val age = (System.currentTimeMillis() - since).coerceAtLeast(0)
        failureBanner = age < 8_000
        if (failureBanner) { delay(8_000 - age); failureBanner = false }
        delay((2L * 24 * 60 * 60 * 1000 - (System.currentTimeMillis() - since)).coerceAtLeast(0))
        withContext(Dispatchers.IO) { RegionalMaps.refresh(ctx) }
    }
    LaunchedEffect(confirm, confirmAreas) { if (confirm != null || confirmAreas) cancelFocus.requestFocus() }
    val routePoints = vm.routes.getOrNull(vm.selectedRoute)?.points

    LaunchedEffect(reload, st.installedRevision, st.stage?.active, routePoints, navigating) {
        withContext(Dispatchers.IO) {
            overview = runCatching { OfflinePackagesService.overview(ctx) }.getOrNull()
            storage = runCatching { OfflinePackagesService.storage(ctx, navigating) }.getOrNull()
            areaRows = runCatching { OfflinePackagesService.areaStorage(ctx, navigating, routePoints.orEmpty()) }.getOrDefault(emptyList())
            selectedAreas = selectedAreas.intersect(areaRows.filter { it.removable }.map { it.id }.toSet())
        }
    }
    LaunchedEffect(routePoints, st.installedRevision) {
        route = routePoints?.let { pts -> withContext(Dispatchers.IO) { OfflinePackagesService.routeCoverage(ctx, pts) } }
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun note(text: String) = Text(text, fontSize = 14.sp, color = muted)
    @Composable fun heading(text: String) =
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))

    OfflineAutoUpdateControl()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Allow mobile downloads", modifier = Modifier.weight(1f))
        Switch(checked = mobileAllowed, onCheckedChange = { allowed ->
            if (allowed) confirmMobile = true else {
                mobileAllowed = false
                RegionalMaps.setMobileDownloadsAllowed(ctx, false)
            }
        })
    }
    if (confirmMobile) AlertDialog(
        onDismissRequest = { confirmMobile = false },
        title = { Text("Allow mobile downloads?") },
        text = { Text("Map downloads can be large and use your mobile data allowance. There is no daily download cap.") },
        confirmButton = { TextButton(onClick = {
            mobileAllowed = true; confirmMobile = false
            RegionalMaps.setMobileDownloadsAllowed(ctx, true)
        }) { Text("Allow") } },
        dismissButton = { TextButton(onClick = { confirmMobile = false }) { Text("Cancel") } },
    )
    if (failureBanner) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Map download failed", modifier = Modifier.weight(1f))
        TextButton(onClick = { failureBanner = false; RegionalMaps.resume(ctx) }) { Text("Retry") }
    }

    // Download stage (WA22).
    st.stage?.let { stage ->
        heading("Area map download")
        note(RegionalDownloadText.line(stage))
        if (stage.active && stage.totalBytes > 0) {
            LinearProgressIndicator(
                progress = { (stage.doneBytes.toDouble() / stage.totalBytes).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (stage.step != RegionalDownloadStage.Step.DOWNLOADING)
                note("${(stage.doneBytes.toDouble() * 100 / stage.totalBytes).toInt().coerceIn(0, 100)}%")
        }
        stage.note?.takeIf { stage.step != RegionalDownloadStage.Step.FAILED && stage.step != RegionalDownloadStage.Step.WAITING }
            ?.let { note(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stage.active) TextButton(onClick = { RegionalMaps.pause(ctx) }) { Text("Pause") }
            if ((stage.step == RegionalDownloadStage.Step.PAUSED || stage.step == RegionalDownloadStage.Step.FAILED) &&
                RegionalMaps.canResume(ctx)) {
                TextButton(onClick = { RegionalMaps.resume(ctx) }) { Text(if (stage.step == RegionalDownloadStage.Step.FAILED) "Retry" else "Resume") }
            }
        }
    }

    // Before you go (MX08).
    route?.let { heading("Your planned route offline"); note(it) }

    // Installed versus available (MX08 / WA21).
    val o = overview
    heading("Offline area maps")
    if (o == null) note("Checking what's on this phone…") else {
        note(o.versionLine)
        o.installedLines.forEach { (line, parts) ->
            Text(line, fontSize = 14.sp)
            note(parts)
        }
        if (o.availableSummary.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o.availableSummary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAvailable = !showAvailable }) { Text(if (showAvailable) "Hide" else "Show") }
            }
            if (showAvailable) o.availableLines.forEach { note(it) }
        }
        o.notes.forEach { note(it) }
    }

    // Storage and cleanup (WA23).
    val s = storage
    heading("Storage")
    if (s == null) note("Measuring…") else {
        note(s.second)
        s.first.forEach { row ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${row.label} · ${row.size}", fontSize = 14.sp)
                    row.reason?.let { note(it) }
                }
                if (row.removable) TextButton(onClick = { confirm = row }) { Text("Remove") }
            }
        }
    }
    if (areaRows.isNotEmpty()) {
        heading("Choose downloaded areas to remove")
        note("Last-use dates for existing areas are not recorded yet.")
        areaRows.forEach { row ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = row.id in selectedAreas, enabled = row.removable, onCheckedChange = { checked ->
                    selectedAreas = if (checked) selectedAreas + row.id else selectedAreas - row.id
                })
                Column(Modifier.weight(1f)) {
                    Text("${row.label} · ${OfflineStorage.mb(row.bytes)} freed", fontSize = 14.sp)
                    if (row.affectsRoute) note("Removing this area affects your planned route.")
                    if (!row.removable) note("In use on the map or during a journey; removal is unavailable.")
                }
            }
        }
        val selected = areaRows.filter { it.id in selectedAreas }
        note("Selected removals free ${OfflineStorage.mb(selected.sumOf { it.bytes })}")
        if (selected.isNotEmpty()) TextButton(onClick = { confirmAreas = true }) { Text("Review removal") }
    }
    if (confirmAreas) {
        val selected = areaRows.filter { it.id in selectedAreas }
        AlertDialog(
            onDismissRequest = { confirmAreas = false },
            title = { Text("Remove selected offline areas?") },
            text = { Text("Frees ${OfflineStorage.mb(selected.sumOf { it.bytes })}. " +
                if (selected.any { it.affectsRoute }) "This affects your planned route. Offline map detail in these areas will be unavailable."
                else "Offline map detail in these areas will be unavailable.") },
            confirmButton = { TextButton(onClick = {
                val chosen = selectedAreas
                confirmAreas = false
                scope.launch {
                    message = withContext(Dispatchers.IO) {
                        OfflinePackagesService.cleanAreas(ctx, chosen, navigating, routePoints.orEmpty(), confirmed = true)
                    }
                    selectedAreas = emptySet(); reload++
                }
            }) { Text("Remove areas") } },
            dismissButton = { TextButton(modifier = Modifier.focusRequester(cancelFocus),
                onClick = { confirmAreas = false }) { Text("Cancel removal") } },
        )
    }
    message?.let { note(it) }

    confirm?.let { row ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove ${row.label.lowercase()}?") },
            text = { Text("${row.size}. ${row.consequence} Your favourites and saved places are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        message = withContext(Dispatchers.IO) { OfflinePackagesService.clean(ctx, row.kind, navigating) }
                        reload++
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(modifier = Modifier.focusRequester(cancelFocus), onClick = { confirm = null }) { Text("Cancel removal") } },
        )
    }
}
