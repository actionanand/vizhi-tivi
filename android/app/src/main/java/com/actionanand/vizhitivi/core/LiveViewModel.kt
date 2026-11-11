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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LiveState(val catalogue: Catalogue = Catalogue(null, emptyList()), val playlists: List<Playlist> = emptyList(), val preferences: Preferences = Preferences(), val loading: Boolean = true, val error: Boolean = false)
class LiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository(application)
    private val operations = Mutex()
    private val mutable = MutableStateFlow(LiveState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch { repository.preferences.catch { mutable.update { it.copy(error = true) } }.collect { p -> mutable.update { it.copy(preferences = p) } } }
        action {
            val playlists = repository.playlists()
            mutable.update { it.copy(playlists = playlists) }
            var catalogue = state.value.catalogue
            try { catalogue = repository.initial() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(error = true) } }
            val official = catalogue.channels.mapNotNull { it.number }.toSet()
            val reconciled = playlists.map { p -> p.copy(channels = p.channels.map { c -> if (c.number in official) c.copy(number = null) else c }) }
            mutable.update { it.copy(catalogue = catalogue, playlists = reconciled) }
            val endpoint = repository.preferences.first().endpoint
            val next = repository.refresh(endpoint, catalogue)
            reconcile(next)
        }
    }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch { operations.withLock {
        mutable.update { it.copy(loading = true, error = false) }
        try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(error = true) } }
        finally { mutable.update { it.copy(loading = false) } }
    } }
    fun refresh() = action {
        val next = repository.refresh(state.value.preferences.endpoint, state.value.catalogue)
        reconcile(next)
    }
    private fun reconcile(next: Catalogue) {
        val official = next.channels.mapNotNull { it.number }.toSet()
        val playlists = state.value.playlists.map { p -> p.copy(channels = p.channels.map { c -> if (c.number in official) c.copy(number = null) else c }) }
        // Public refresh never uploads or rewrites private playlist records.
        mutable.update { it.copy(catalogue = next, playlists = playlists, error = false) }
    }
    fun setting(key: String, value: String) = action { repository.preference(key, value) }
    fun favourite(id: String) = action { repository.favourite(id) }
    fun reportError() { mutable.update { it.copy(error = true) } }
    fun watched(id: String) = action { repository.watched(id) }
    fun importFile(uri: Uri) = action { importText(repository.readFile(uri), "document:${uri}", uri.lastPathSegment ?: "M3U") }
    fun importUrl(url: String) = action { importText(repository.fetch(url), "url:$url", "M3U • ${java.net.URI(url).host}") }
    private suspend fun importText(text: String, source: String, name: String) {
        val id = stableId(source)
        val channels = parseM3u(text, id)
        val current = state.value.playlists
        val old = current.find { it.id == id }?.channels.orEmpty().mapNotNull { c -> c.number?.let { c.id to it } }.toMap()
        val reserved = (state.value.catalogue.channels + current.filter { it.id != id }.flatMap { it.channels }).mapNotNull { it.number }.toSet()
        // Default imports remain unnumbered. The opt-in local policy never reserves official assignments.
        val numbered = if (state.value.preferences.localTamilNumbers) allocate(channels, old.filterValues { it !in reserved }, reserved) else channels
        val next = current.filter { it.id != id } + Playlist(id, name, numbered)
        repository.savePlaylists(next); mutable.update { it.copy(playlists = next) }
    }
    fun removePlaylist(id: String) = action {
        val next = state.value.playlists.filter { it.id != id }; repository.savePlaylists(next); mutable.update { it.copy(playlists = next) }
    }
    fun correctLanguage(channel: Channel, language: String) = action {
        val next = state.value.playlists.map { p -> p.copy(channels = p.channels.map { c -> if (c.id == channel.id) c.copy(languages = listOf(language), number = null) else c }) }
        repository.savePlaylists(next); mutable.update { it.copy(playlists = next) }
    }
}
