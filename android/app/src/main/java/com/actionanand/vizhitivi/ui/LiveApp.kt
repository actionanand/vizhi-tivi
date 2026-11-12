package com.actionanand.vizhitivi.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.actionanand.vizhitivi.R
import com.actionanand.vizhitivi.core.*
import coil3.compose.AsyncImage

@Composable
fun LiveApp(vm: LiveViewModel, state: LiveState, isTv: Boolean) {
    var page by rememberSaveable { mutableStateOf("home") }
    var search by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var urlDialog by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<Playlist?>(null) }
    var recoverDialog by remember { mutableStateOf(false) }
    var snapshotDialog by remember { mutableStateOf(false) }
    var deleteSnapshot by remember { mutableStateOf<String?>(null) }
    var exportSnapshot by rememberSaveable { mutableStateOf<String?>(null) }
    val browseState = rememberSaveableStateHolder()
    val all = state.catalogue.channels + state.playlists.flatMap { it.channels }
    val active = all.find { it.id == selected }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFile(it) } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportFile(it) } }
    val originalExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportOriginal(it) } }
    val snapshotExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { target -> exportSnapshot?.let { vm.exportSnapshot(target, it) } }; exportSnapshot = null }
    val dark = state.preferences.theme == "dark" || (state.preferences.theme == "system" && isSystemInDarkTheme())
    val colours = if (!dark) lightColorScheme() else darkColorScheme(primary = Color(0xFF70E5CA), background = Color(0xFF101921), surface = Color(0xFF192631))
    MaterialTheme(colorScheme = colours) {
        Surface(Modifier.fillMaxSize()) {
            if (active != null && active.playable != null) LivePlayer(active) { selected = null }
            else {
              browseState.SaveableStateProvider("browse") {
                BackHandler(page != "home") { page = "home" }
                Column(Modifier.fillMaxSize().padding(if (isTv) 40.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.app_icon), contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Vizhi TiVi", style = if (isTv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge)
                    }
                    Text(stringResource(R.string.tagline))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("home" to R.string.home, "channels" to R.string.channels, "favourites" to R.string.favourites, "imported" to R.string.imported, "settings" to R.string.settings).forEach { (key, res) ->
                            Action(stringResource(res), isTv, page == key) { page = key }
                        }
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.networkBusy) Action(stringResource(R.string.cancel_network), isTv) { vm.cancelNetwork() }
                    if (state.networkOutcome == NetworkOutcome.CANCELLED) Text(stringResource(R.string.network_cancelled))
                    if (state.error) Text(stringResource(if (state.networkOutcome == NetworkOutcome.TIMED_OUT) R.string.network_timeout else R.string.operation_error), color = MaterialTheme.colorScheme.error)
                    if (page == "settings") {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Text(stringResource(R.string.ui_language)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("en" to "English", "ta" to "தமிழ்", "sa" to "संस्कृतम्").forEach { (code, label) -> Action(label, isTv, state.preferences.locale == code) { vm.setting("locale", code) } }
                            } }
                            item { Text(stringResource(R.string.theme)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("system" to R.string.system_theme, "dark" to R.string.dark_theme, "light" to R.string.light_theme).forEach { (key, res) -> Action(stringResource(res), isTv) { vm.setting("theme", key) } }
                            } }
                            item {
                                var endpoint by remember(state.preferences.endpoint) { mutableStateOf(state.preferences.endpoint) }
                                OutlinedTextField(endpoint, { endpoint = it }, label = { Text(stringResource(R.string.endpoint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Action(stringResource(R.string.save), isTv) { vm.setting("endpoint", endpoint) }
                                Action(stringResource(R.string.refresh), isTv, enabled = state.preferences.endpoint.isNotEmpty() && !state.networkBusy) { vm.refresh() }
                            }
                            item { Text(stringResource(R.string.private_notice)); Action(stringResource(R.string.local_numbers) + ": " + stringResource(if (state.preferences.localTamilNumbers) R.string.on else R.string.off), isTv) { vm.setting("localTamil", (!state.preferences.localTamilNumbers).toString()) } }
                        }
                    } else {
                        if (page == "imported") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Action(stringResource(R.string.import_url), isTv, enabled = !state.networkBusy) { urlDialog = true }
                                Action(stringResource(R.string.import_file), isTv, enabled = !state.localBusy) { runCatching { picker.launch(arrayOf("audio/*", "application/*", "text/*")) }.onFailure { vm.reportError() } }
                                Action(stringResource(R.string.export_playlists), isTv, enabled = !state.localBusy && state.playlists.isNotEmpty()) { runCatching { exporter.launch("vizhi-tivi-playlists.json") }.onFailure { vm.reportError() } }
                                if (state.recoveryRequired || state.recoveryBackupAvailable) Action(stringResource(R.string.export_original), isTv, enabled = !state.localBusy) { runCatching { originalExporter.launch("vizhi-tivi-original.json") }.onFailure { vm.reportError() } }
                                if (state.recoveryRequired) Action(stringResource(R.string.recover_playlists), isTv, enabled = !state.localBusy) { recoverDialog = true }
                                if (state.recoverySnapshots.isNotEmpty()) Action(stringResource(R.string.recovery_snapshots), isTv) { snapshotDialog = true }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(state.playlists, key = { it.id }) { p -> Action("${p.name} (${p.channels.size}) · " + stringResource(R.string.remove), isTv) { remove = p } } }
                            Text(stringResource(R.string.private_notice))
                        }
                        if (state.catalogue.developmentFixture && page != "imported") Text(stringResource(R.string.fixture_notice))
                        OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val base = when (page) { "imported" -> all.filter { it.playlistId != null }; "favourites" -> all.filter { it.id in state.preferences.favourites }; else -> state.catalogue.channels }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { Action(stringResource(R.string.all), isTv) { language = ""; category = ""; country = "" } }
                            items(base.flatMap { it.languages }.distinct().sorted()) { l -> Action(l, isTv, language == l) { language = if (language == l) "" else l } }
                            items(base.map { it.category }.distinct().sorted()) { c -> Action(c, isTv, category == c) { category = if (category == c) "" else c } }
                            items(base.map { it.country }.filter { it.isNotEmpty() }.distinct().sorted()) { c -> Action(c, isTv, country == c) { country = if (country == c) "" else c } }
                        }
                        val shown = base.filter { it.name.contains(search, true) && (language.isEmpty() || language in it.languages) && (category.isEmpty() || category == it.category) && (country.isEmpty() || country == it.country) }.sortedWith(compareBy<Channel> { it.number ?: Int.MAX_VALUE }.thenBy { it.name })
                        val play: (Channel) -> Unit = { c -> restoreId = c.id; selected = c.id; vm.watched(c.id) }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 10.dp)) {
                            if (page == "home" && state.preferences.recent.isNotEmpty()) {
                                item { Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleLarge) }
                                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) { items(state.preferences.recent.mapNotNull { id -> all.find { it.id == id } }, key = { it.id }) { c -> ChannelCard(c, vm, state, isTv, restoreId == c.id && shown.none { it.id == c.id }, play) } } }
                            }
                            if (shown.isEmpty()) item { Text(stringResource(if (page in listOf("home", "channels") && state.catalogue.contentSha256 != null && state.catalogue.channels.isEmpty()) R.string.official_empty else R.string.empty)) }
                            if (isTv) items(shown.groupBy { it.category }.toList(), key = { it.first }) { (title, channels) ->
                                Text(title, style = MaterialTheme.typography.headlineSmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) { items(channels, key = { it.id }) { c -> ChannelCard(c, vm, state, true, restoreId == c.id, play) } }
                            } else items(shown, key = { it.id }) { c -> ChannelCard(c, vm, state, false, restoreId == c.id, play) }
                        }
                    }
                }
              }
            }
            if (urlDialog) {
                var url by remember { mutableStateOf("") }
                AlertDialog(onDismissRequest = { urlDialog = false }, title = { Text(stringResource(R.string.import_url)) }, text = {
                    OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.url)) }, singleLine = true)
                }, confirmButton = { Action(stringResource(R.string.save), isTv, enabled = safeUrl(url)) { urlDialog = false; vm.importUrl(url) } }, dismissButton = { Action(stringResource(R.string.cancel), isTv) { urlDialog = false } })
            }
            remove?.let { p -> AlertDialog(onDismissRequest = { remove = null }, title = { Text(stringResource(R.string.remove_confirm)) }, text = { Text(p.name) }, confirmButton = { Action(stringResource(R.string.remove), isTv) { remove = null; vm.removePlaylist(p.id) } }, dismissButton = { Action(stringResource(R.string.cancel), isTv) { remove = null } }) }
            if (recoverDialog) AlertDialog(onDismissRequest = { recoverDialog = false }, title = { Text(stringResource(R.string.recover_playlists)) }, text = { Text(stringResource(R.string.recover_confirm)) }, confirmButton = { Action(stringResource(R.string.recover_playlists), isTv) { recoverDialog = false; vm.recoverPlaylists() } }, dismissButton = { Action(stringResource(R.string.cancel), isTv) { recoverDialog = false } })
            if (snapshotDialog) AlertDialog(onDismissRequest = { snapshotDialog = false }, title = { Text(stringResource(R.string.recovery_snapshots)) }, text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recoverySnapshots, key = { it.id }) { snapshot ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(snapshot.createdAt))} · ${snapshot.size} B · v${snapshot.formatVersion ?: "—"}")
                            Text(stringResource(when { snapshot.integrity == "invalid" -> R.string.snapshot_invalid; snapshot.integrity == "unverified" -> R.string.snapshot_unverified; !snapshot.complete -> R.string.snapshot_partial; else -> R.string.snapshot_valid }))
                            Text(stringResource(R.string.snapshot_count) + ": " + snapshot.recoverableCount)
                            Text(stringResource(if (snapshot.status == "restored") R.string.snapshot_recovered else R.string.snapshot_saved))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Action(stringResource(R.string.snapshot_export), isTv) { exportSnapshot = snapshot.id; runCatching { snapshotExporter.launch("vizhi-tivi-recovery.json") }.onFailure { vm.reportError() } }
                                Action(stringResource(R.string.snapshot_restore), isTv, enabled = snapshot.integrity != "invalid" && snapshot.recoverableCount > 0 && !state.localBusy) { snapshotDialog = false; vm.restoreSnapshot(snapshot.id) }
                                Action(stringResource(R.string.snapshot_delete), isTv) { deleteSnapshot = snapshot.id }
                            }
                        }
                    }
                }
            }, confirmButton = { Action(stringResource(R.string.cancel), isTv) { snapshotDialog = false } })
            deleteSnapshot?.let { id -> AlertDialog(onDismissRequest = { deleteSnapshot = null }, title = { Text(stringResource(R.string.snapshot_delete_confirm)) }, confirmButton = { Action(stringResource(R.string.snapshot_delete), isTv) { deleteSnapshot = null; vm.deleteSnapshot(id) } }, dismissButton = { Action(stringResource(R.string.cancel), isTv) { deleteSnapshot = null } }) }
        }
    }
}

@Composable
private fun ChannelCard(c: Channel, vm: LiveViewModel, state: LiveState, tv: Boolean, restore: Boolean, play: (Channel) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(restore) { if (restore) { withFrameNanos { }; runCatching { focus.requestFocus() } } }
    Card(Modifier.then(if (tv) Modifier.width(360.dp) else Modifier.fillMaxWidth())) {
        Column(Modifier.padding(if (tv) 24.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(model = c.logo.takeIf(::safeUrl), contentDescription = null, placeholder = painterResource(R.drawable.app_icon), error = painterResource(R.drawable.app_icon), fallback = painterResource(R.drawable.app_icon), modifier = Modifier.size(64.dp))
            Text(c.number?.toString()?.padStart(3, '0') ?: "—", color = MaterialTheme.colorScheme.primary)
            Text(c.name, style = MaterialTheme.typography.titleLarge)
            Text("${c.languages.joinToString()} · ${c.category}")
            if (c.playlistId != null) Text(stringResource(R.string.imported))
            if (c.playable == null) Text(stringResource(R.string.unavailable))
            if (c.trial) Text(stringResource(R.string.trial), color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(stringResource(if (c.id in state.preferences.favourites) R.string.unfavourite else R.string.favourite), tv, modifier = Modifier.focusRequester(focus)) { vm.favourite(c.id) }
                if (c.playable != null) Action(stringResource(R.string.play), tv) { play(c) }
            }
            if (c.playlistId != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ta", "en", "sa", "und").forEach { l -> Action(l, tv, c.languages == listOf(l)) { vm.correctLanguage(c, l) } }
            }
            if (c.playlistId != null && c.languageOverride != null && c.sourceLanguages != null) Action(stringResource(R.string.reset_language), tv) { vm.resetLanguage(c) }
        }
    }
}

@Composable
internal fun Action(label: String, tv: Boolean, selected: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (tv) androidx.tv.material3.Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) { androidx.tv.material3.Text(if (selected) "• $label" else label) }
    else FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) { Text(if (selected) "• $label" else label) }
}
