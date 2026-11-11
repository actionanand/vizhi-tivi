package com.actionanand.vizhitivi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.actionanand.vizhitivi.R
import com.actionanand.vizhitivi.core.Channel
import coil3.compose.AsyncImage
import androidx.compose.ui.res.painterResource

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LivePlayer(channel: Channel, close: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val stream = channel.playable ?: return
    var error by remember(channel.id) { mutableStateOf(false) }
    var buffering by remember(channel.id) { mutableStateOf(true) }
    val view = remember { PlayerView(context).apply { setShowSubtitleButton(true); setShowNextButton(false); setShowPreviousButton(false) } }
    val player = remember(channel.id) {
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(context).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(2))).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            val mime = when (stream.format) { "hls" -> MimeTypes.APPLICATION_M3U8; "dash" -> MimeTypes.APPLICATION_MPD; else -> null }
            setMediaItem(MediaItem.Builder().setUri(stream.url).setMimeType(mime).build())
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player, lifecycle) {
        view.player = player
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(e: PlaybackException) { error = true; buffering = false; view.keepScreenOn = false }
            override fun onIsPlayingChanged(isPlaying: Boolean) { view.keepScreenOn = isPlaying }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { player.pause(); view.keepScreenOn = false } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); view.keepScreenOn = false; view.player = null; player.release() }
    }
    BackHandler { close() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(close) { Text(stringResource(R.string.back)) }
            AsyncImage(model = channel.logo.ifBlank { null }, contentDescription = null, fallback = painterResource(R.drawable.app_icon), error = painterResource(R.drawable.app_icon), modifier = Modifier.size(48.dp))
            Text(channel.name + " · " + stringResource(R.string.live), style = MaterialTheme.typography.titleLarge)
        }
        if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (channel.trial) Text(stringResource(R.string.trial))
        if (error) {
            Text(stringResource(R.string.playback_error))
            Button({ error = false; player.prepare(); player.play() }) { Text(stringResource(R.string.retry)) }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
