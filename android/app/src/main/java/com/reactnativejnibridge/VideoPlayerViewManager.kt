package com.reactnativejnibridge

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class VideoPlayerViewManager : SimpleViewManager<PlayerView>() {

    override fun getName() = "VideoPlayer"

    override fun createViewInstance(context: ThemedReactContext): PlayerView {
        val player = ExoPlayer.Builder(context).build()
        val view = PlayerView(context)
        view.player = player
        view.useController = true
        return view
    }

    @ReactProp(name = "url")
    fun setUrl(view: PlayerView, url: String?) {
        url ?: return
        val player = view.player as? ExoPlayer ?: return
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.playWhenReady = true
    }

    override fun onDropViewInstance(view: PlayerView) {
        view.player?.release()
        view.player = null
        super.onDropViewInstance(view)
    }
}
