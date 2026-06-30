package com.reactnativejnibridge

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

/**
 * VideoPlayerViewManager is a React Native ViewManager that wraps ExoPlayer's
 * [PlayerView] so it can be rendered as a native component from JavaScript.
 *
 * WHY IT EXISTS
 * React Native's JS renderer can only paint views that it knows about through
 * the shadow tree. To embed a fully native Android view — one that needs hardware
 * video decoding, a SurfaceView, and codec lifecycle management — you cannot use
 * a regular RN component. You must implement a [SimpleViewManager] that tells
 * React Native: "when JS renders <VideoPlayer />, create this exact Android view
 * and apply these props to it."
 *
 * Without this class, requireNativeComponent('VideoPlayer') on the JS side would
 * throw an "Unrecognised native component" error at render time.
 *
 * WHAT SimpleViewManager<PlayerView> MEANS
 * The generic parameter [PlayerView] is the concrete Android View subclass that
 * this manager creates and manages. [SimpleViewManager] is the base class for
 * managers whose root view is a single, non-scrollable, leaf-level view. It
 * handles view recycling and prop diffing automatically — we only need to
 * override the three lifecycle hooks below.
 *
 * VIEW LIFECYCLE
 *
 * 1. [createViewInstance] — called by Fabric on the UI thread the first time
 *    this component appears in the shadow tree. We build the full ExoPlayer stack
 *    here: decoder (ExoPlayer) → surface + controls (PlayerView). The player is
 *    stored inside PlayerView.player so we can retrieve it later without a
 *    separate map.
 *
 * 2. [@ReactProp setUrl] — called by Fabric on the UI thread whenever the `url`
 *    prop changes in JS. We load the media item into ExoPlayer and start playback.
 *    [MediaItem.fromUri] auto-detects the stream type from the URI; the
 *    media3-exoplayer-hls dependency adds HLS (.m3u8) support transparently.
 *    [player.prepare()] allocates codec resources and begins buffering.
 *    [player.playWhenReady = true] starts playback as soon as buffering allows.
 *
 * 3. [onDropViewInstance] — called by Fabric when the component unmounts (e.g.
 *    user navigates back from PlayerScreen). We must release the player here to
 *    free the hardware video decoder, audio session, and SurfaceView resources.
 *    Failing to call release() leaks the codec and can prevent any other video
 *    from playing until the process restarts.
 *
 * HOW JS USES THIS
 * On the JS side (src/components/VideoPlayer.tsx):
 *   const VideoPlayer = requireNativeComponent('VideoPlayer');
 *   <VideoPlayer url="http://..." style={{ flex: 1 }} />
 *
 * React Native matches the string 'VideoPlayer' to [getName] and delegates all
 * rendering and prop updates to this class. The `url` string prop is forwarded
 * to [setUrl] automatically because [getName] in [@ReactProp] matches the prop
 * key used in JSX.
 *
 * THREADING
 * [createViewInstance], [setUrl], and [onDropViewInstance] all run on the
 * Android UI thread. ExoPlayer itself dispatches playback work to its own
 * internal thread pool, so long decode operations never block the UI thread.
 *
 * NEW ARCHITECTURE NOTE
 * In New Architecture (Fabric) mode the view manager is wrapped by an interop
 * adapter that makes it compatible with the Fabric renderer without requiring
 * a codegen-generated ViewConfig. Layout is measured by Fabric's C++ Yoga engine
 * and the resulting frame is applied to the [PlayerView] on the UI thread.
 */
class VideoPlayerViewManager : SimpleViewManager<PlayerView>() {

    // The string returned here must match the argument passed to
    // requireNativeComponent() on the JS side.
    override fun getName() = "VideoPlayer"

    /**
     * Creates the native view hierarchy for one instance of <VideoPlayer />.
     * Called once per component mount on the UI thread.
     */
    override fun createViewInstance(context: ThemedReactContext): PlayerView {
        val player = ExoPlayer.Builder(context).build()
        val view = PlayerView(context)
        view.player = player
        // Show the built-in ExoPlayer transport controls (play/pause, seek bar, etc.)
        view.useController = true
        return view
    }

    /**
     * Receives the `url` prop from JS and starts playback.
     * Called on the UI thread whenever the prop value changes.
     *
     * Casting view.player to ExoPlayer (rather than the Player interface) is safe
     * here because we are the only code that assigns view.player, and we always
     * assign an ExoPlayer instance in createViewInstance.
     */
    @ReactProp(name = "url")
    fun setUrl(view: PlayerView, url: String?) {
        url ?: return
        val player = view.player as? ExoPlayer ?: return
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Called when the <VideoPlayer /> component unmounts.
     * Releases all hardware resources held by the player.
     * Setting view.player = null prevents PlayerView from drawing stale frames
     * after the player is gone.
     */
    override fun onDropViewInstance(view: PlayerView) {
        view.player?.release()
        view.player = null
        super.onDropViewInstance(view)
    }
}
