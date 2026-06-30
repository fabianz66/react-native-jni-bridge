package com.reactnativejnibridge

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
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
 *    here: decoder (ExoPlayer) → surface + controls (PlayerView). We also attach
 *    a [Player.Listener] that fires [emitState] whenever ExoPlayer transitions
 *    between playback states. The player is stored inside PlayerView.player so
 *    we can retrieve it later without a separate map.
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
 * to [setUrl] automatically because the name in [@ReactProp] matches the prop
 * key used in JSX.
 *
 * STATE EVENTS
 * Every time ExoPlayer transitions to a new playback state, this manager emits
 * a 'onPlayerStateChanged' event to JS via DeviceEventEmitter. The payload is:
 *   { state: string, error?: string }
 *
 * Possible state values:
 *   "idle"      — player is created but prepare() has not been called yet
 *   "buffering" — player is loading data and cannot play yet
 *   "playing"   — player is actively rendering frames / audio
 *   "paused"    — player is ready but playWhenReady is false
 *   "ended"     — the media item has finished playing
 *   "error"     — a fatal playback error occurred (error field contains the message)
 *
 * On the JS side, subscribe with:
 *   DeviceEventEmitter.addListener('onPlayerStateChanged', e => { ... })
 *
 * THREADING
 * [createViewInstance], [setUrl], and [onDropViewInstance] all run on the
 * Android UI thread. ExoPlayer dispatches [Player.Listener] callbacks on the
 * main thread as well, so [emitState] is always called from the main thread.
 * [DeviceEventManagerModule.RCTDeviceEventEmitter.emit] is thread-safe and
 * schedules the JS callback on the React JS thread automatically.
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
     *
     * Attaches a [Player.Listener] that converts ExoPlayer's internal state
     * machine into human-readable state strings and pushes them to JS.
     * The listener captures [context] and [player] from the local scope —
     * no separate map is needed. When [onDropViewInstance] calls player.release(),
     * ExoPlayer clears all listeners automatically.
     */
    override fun createViewInstance(context: ThemedReactContext): PlayerView {
        val player = ExoPlayer.Builder(context).build()

        player.addListener(object : Player.Listener {
            /**
             * Fires when Player.STATE_* changes (IDLE → BUFFERING → READY → ENDED).
             * We re-derive the full state string because playWhenReady (playing vs
             * paused) is a separate flag that this callback does not reflect on its own.
             */
            override fun onPlaybackStateChanged(playbackState: Int) {
                emitState(context, resolveState(player))
            }

            /**
             * Fires when the player transitions between actually-playing and not-playing
             * within STATE_READY. This distinguishes "playing" from "paused" — two
             * sub-states that onPlaybackStateChanged alone cannot differentiate.
             */
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                emitState(context, resolveState(player))
            }

            /**
             * Fires on unrecoverable playback errors (network failure, unsupported
             * codec, malformed stream, etc.). The error message is forwarded to JS
             * so the UI can display a human-readable description.
             */
            override fun onPlayerError(error: PlaybackException) {
                emitState(context, "error", error.message)
            }
        })

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

    /**
     * Maps the current ExoPlayer state + playWhenReady flag into a single
     * human-readable string for consumption on the JS side.
     *
     * Order of the when branches matters: buffering takes priority over
     * paused, and error is handled separately in onPlayerError.
     */
    private fun resolveState(player: ExoPlayer): String = when {
        player.playbackState == Player.STATE_IDLE      -> "idle"
        player.playbackState == Player.STATE_BUFFERING -> "buffering"
        player.playbackState == Player.STATE_ENDED     -> "ended"
        player.isPlaying                               -> "playing"
        else                                           -> "paused"
    }

    /**
     * Emits a 'onPlayerStateChanged' event to every JS listener registered via
     * DeviceEventEmitter.addListener('onPlayerStateChanged', ...).
     *
     * The payload WritableMap is serialised through JSI into a plain JS object:
     *   { state: string, error?: string }
     */
    private fun emitState(context: ThemedReactContext, state: String, error: String? = null) {
        val payload = Arguments.createMap().apply {
            putString("state", state)
            error?.let { putString("error", it) }
        }
        context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onPlayerStateChanged", payload)
    }
}
