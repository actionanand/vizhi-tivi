package com.actionanand.vizhitivi.core

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

data class LiveState(val catalogue: Catalogue = Catalogue(null, emptyList()), val playlists: List<Playlist> = emptyList(), val preferences: Preferences = Preferences(), val loading: Boolean = true, val localBusy: Boolean = true, val networkBusy: Boolean = false, val error: Boolean = false, val networkOutcome: NetworkOutcome = NetworkOutcome.NONE, val recoveryRequired: Boolean = false, val recoveryBackupAvailable: Boolean = false, val recoverySnapshots: List<RecoverySnapshot> = emptyList())
class LiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository(application)
    private val operations = Mutex()
    private val publications = PublicationCoordinator()
    private val localPending = AtomicInteger()
    private val networkPending = AtomicInteger()
    private val networkJobs = ConcurrentHashMap<Job, NetworkCommitGate>()
    @Volatile private var privateStoreReadable = true
    private val mutable = MutableStateFlow(LiveState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch { repository.preferences.catch { mutable.update { it.copy(error = true) } }.collect { p -> mutable.update { it.copy(preferences = p) } } }
        val initial = action {
            val playlists = try { repository.playlists().let { loaded -> privateStoreReadable = loaded.complete; loaded.playlists } } catch (e: Exception) {
                if (e is CancellationException) throw e
                privateStoreReadable = false
                mutable.update { it.copy(error = true) }
                emptyList()
            }
            val snapshots = try { repository.recoverySnapshots() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(error = true) }; emptyList() }
            mutable.update { it.copy(playlists = playlists, recoveryRequired = !privateStoreReadable, recoveryBackupAvailable = snapshots.isNotEmpty(), recoverySnapshots = snapshots) }
            var catalogue = state.value.catalogue
            try { catalogue = repository.initial() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(error = true) } }
            val official = catalogue.channels.mapNotNull { it.number }.toSet()
            val reconciled = playlists.map { p -> p.copy(channels = p.channels.map { c -> if (c.number in official) c.copy(number = null) else c }) }
            mutable.update { it.copy(catalogue = catalogue, playlists = reconciled) }
        }
        viewModelScope.launch { initial.join(); refresh() }
    }
    private fun begin(network: Boolean) {
        (if (network) networkPending else localPending).incrementAndGet()
        mutable.update { it.copy(loading = true, localBusy = localPending.get() > 0, networkBusy = networkPending.get() > 0, error = false, networkOutcome = NetworkOutcome.NONE) }
    }
    private fun end(network: Boolean) {
        (if (network) networkPending else localPending).decrementAndGet()
        mutable.update { it.copy(loading = localPending.get() > 0 || networkPending.get() > 0, localBusy = localPending.get() > 0, networkBusy = networkPending.get() > 0, error = it.error || !privateStoreReadable) }
    }
    private fun failure(e: Exception, network: Boolean = false) { if (e is CancellationException) throw e; mutable.update { it.copy(error = true, networkOutcome = if (network) networkOutcome(e) else NetworkOutcome.NONE) } }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        begin(false)
        try { operations.withLock { withContext(Dispatchers.IO) { block() } } } catch (e: Exception) { failure(e) } finally { end(false) }
    }
    private fun networkAction(block: suspend (NetworkCommitGate) -> Unit): Job {
        val gate = NetworkCommitGate()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            begin(true)
            try { withContext(Dispatchers.IO) { block(gate) } } catch (e: Exception) { failure(e, network = true) } finally { end(true) }
        }
        networkJobs[job] = gate
        job.invokeOnCompletion { networkJobs.remove(job) }
        job.start()
        return job
    }
    fun cancelNetwork() {
        networkJobs.forEach { (job, gate) -> viewModelScope.launch(Dispatchers.IO) {
            if (job.isActive && gate.cancel() && job.isActive) {
                job.cancel()
                mutable.update { it.copy(error = false, networkOutcome = NetworkOutcome.CANCELLED) }
            }
        } }
    }
    fun refresh() = networkAction { gate -> publications.run {
        val previous = state.value.catalogue
        val next = repository.fetchPublication(state.value.preferences.endpoint, previous)
        val prepared = next?.let { repository.preparePublication(it) }
        operations.withLock { gate.commit {
            commitPublication(next?.catalogue ?: previous, previous,
                persist = { mark -> if (prepared != null) finishPublicationCandidate(prepared, mark) }, apply = ::reconcile)
        } }
    } }
    private fun reconcile(next: Catalogue) {
        val official = next.channels.mapNotNull { it.number }.toSet()
        val playlists = state.value.playlists.map { p -> p.copy(channels = p.channels.map { c -> if (c.number in official) c.copy(number = null) else c }) }
        // Public refresh never uploads or rewrites private playlist records.
        mutable.update { it.copy(catalogue = next, playlists = playlists, error = !privateStoreReadable) }
    }
    private fun localAction(block: suspend () -> Unit) = viewModelScope.launch {
        try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { failure(e) }
    }
    fun setting(key: String, value: String) = localAction { repository.preference(key, value) }
    fun favourite(id: String) = localAction { repository.favourite(id) }
    fun reportError() { mutable.update { it.copy(error = true, networkOutcome = NetworkOutcome.NONE) } }
    fun watched(id: String) = localAction { repository.watched(id) }
    fun importFile(uri: Uri) = action { importText(repository.readFile(uri), "document:${uri}", uri.lastPathSegment ?: "M3U") }
    fun importUrl(url: String) = networkAction { gate ->
        val text = repository.fetch(url)
        operations.withLock { gate.commit { importText(text, "url:$url", "M3U • ${java.net.URI(url).host}") } }
    }
    fun exportFile(uri: Uri) = action { repository.exportPlaylists(uri, state.value.playlists) }
    fun exportOriginal(uri: Uri) = action { repository.exportOriginal(uri, privateStoreReadable) }
    fun recoverPlaylists() = action {
        check(!privateStoreReadable)
        repository.recoverPlaylists(state.value.playlists)
        privateStoreReadable = true
        val snapshots = repository.recoverySnapshots()
        mutable.update { it.copy(recoveryRequired = false, recoveryBackupAvailable = snapshots.isNotEmpty(), recoverySnapshots = snapshots, error = false) }
    }
    fun restoreSnapshot(id: String) = action {
        val restored = try {
            repository.restoreSnapshot(id, state.value.catalogue.channels.mapNotNull { it.number }.toSet(), state.value.playlists)
        } catch (failure: Exception) {
            // The atomic rename may have completed even when its final sync or
            // verification failed. Block stale writes until explicit recovery.
            privateStoreReadable = false
            mutable.update { it.copy(recoveryRequired = true) }
            throw failure
        }
        privateStoreReadable = true
        val snapshots = repository.recoverySnapshots()
        mutable.update { it.copy(playlists = restored, recoveryRequired = false, recoveryBackupAvailable = snapshots.isNotEmpty(), recoverySnapshots = snapshots, error = false) }
    }
    fun deleteSnapshot(id: String) = action {
        repository.deleteSnapshot(id)
        val snapshots = repository.recoverySnapshots()
        mutable.update { it.copy(recoveryBackupAvailable = snapshots.isNotEmpty(), recoverySnapshots = snapshots) }
    }
    fun exportSnapshot(uri: Uri, id: String) = action { repository.exportSnapshot(uri, id) }
    private suspend fun importText(text: String, source: String, name: String) {
        check(privateStoreReadable) { "Private playlist store needs recovery" }
        val id = stableId(source)
        val current = state.value.playlists
        val prior = current.find { it.id == id }?.channels.orEmpty().associateBy { it.id }
        val channels = withContext(Dispatchers.Default) { (if (isVztv(text)) parseVztv(text, id) else parseM3u(text, id)).map { c -> prior[c.id]?.languageOverride?.let { c.copy(languages = listOf(it), languageOverride = it) } ?: c } }
        val old = prior.values.mapNotNull { c -> c.number?.let { c.id to it } }.toMap()
        val reserved = (state.value.catalogue.channels + current.filter { it.id != id }.flatMap { it.channels }).mapNotNull { it.number }.toSet()
        // Default imports remain unnumbered. The opt-in local policy never reserves official assignments.
        val numbered = if (state.value.preferences.localTamilNumbers) allocate(channels, old.filterValues { it !in reserved }, reserved) else channels
        val next = current.filter { it.id != id } + Playlist(id, name, numbered)
        repository.savePlaylists(next); mutable.update { it.copy(playlists = next) }
    }
    fun removePlaylist(id: String) = action {
        check(privateStoreReadable) { "Private playlist store needs recovery" }
        val next = state.value.playlists.filter { it.id != id }; repository.savePlaylists(next); mutable.update { it.copy(playlists = next) }
    }
    fun correctLanguage(channel: Channel, language: String) = action {
        check(privateStoreReadable) { "Private playlist store needs recovery" }
        val next = state.value.playlists.map { p -> p.copy(channels = p.channels.map { c -> if (c.id == channel.id) c.copy(languages = listOf(language), number = if (("ta" in c.languages) == (language == "ta")) c.number else null, languageOverride = language) else c }) }
        repository.savePlaylists(next); mutable.update { it.copy(playlists = next) }
    }
    fun resetLanguage(channel: Channel) = action {
        check(privateStoreReadable)
        val next = state.value.playlists.map { p -> p.copy(channels = p.channels.map { c ->
            if (c.id == channel.id) resetLanguageOverride(c) else c
        }) }
        repository.savePlaylists(next); mutable.update { it.copy(playlists = next) }
    }
}
