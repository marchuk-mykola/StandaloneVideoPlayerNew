package com.reactnativestandalonevideoplayer


import android.content.Context
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView


class MyPlayerView(context: Context): PlayerView(context) {

  var playerInstance: Int = -1
  var isBound: Boolean = false

}

//

class PlayerContainerView: SimpleViewManager<MyPlayerView>() {

  companion object {
    // Thread-safe pending views management
    private val pendingViewsLock = Any()
    private val _pendingViews: MutableList<MyPlayerView> = mutableListOf()

    fun addPendingView(view: MyPlayerView) = synchronized(pendingViewsLock) {
      if (!_pendingViews.contains(view)) {
        _pendingViews.add(view)
      }
    }

    fun removePendingView(view: MyPlayerView) = synchronized(pendingViewsLock) {
      _pendingViews.remove(view)
    }

    fun clearPendingViews() = synchronized(pendingViewsLock) {
      _pendingViews.clear()
    }

    fun bindPendingViews() = synchronized(pendingViewsLock) {
      val iterator = _pendingViews.iterator()
      while (iterator.hasNext()) {
        val view = iterator.next()
        val player = PlayerVideo.getInstance(view.playerInstance)
        if (view.playerInstance >= 0 && player != null) {
          val targetPlayer = if (view.isBound) player.player else null
          view.player = targetPlayer
          view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
          (targetPlayer as? ExoPlayer)?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
          iterator.remove()
        }
      }
    }

    fun hasPendingViews(): Boolean = synchronized(pendingViewsLock) {
      _pendingViews.isNotEmpty()
    }
  }

  init {
    // Singleton initialization
  }

  override fun getName() = "RNTPlayerVideoView"

  @ReactProp(name = "isBoundToPlayer")
  fun boundToPlayer(view: MyPlayerView, isBoundToPlayer: Boolean) {
    if (view.isBound != isBoundToPlayer) {
      view.isBound = isBoundToPlayer
      setup(view)
    }
  }

  @ReactProp(name = "playerInstance")
  fun setPlayerInstance(view: MyPlayerView, instance: Int) {
    if (view.playerInstance != instance) {
      view.playerInstance = instance
      setup(view)
    }
  }

  private fun setup(view: MyPlayerView) {
    if (view.playerInstance < 0) {
      return
    }

    // Wait for instance to be created if not yet available
    val playerVideo = PlayerVideo.getInstance(view.playerInstance)
    if (playerVideo == null) {
      addPendingView(view)
      return
    }

    // Remove from pending views if it was there
    removePendingView(view)

    val targetPlayer = if (view.isBound) playerVideo.player else null

    // Always update player binding when isBound is true
    if (view.isBound) {
      view.player = targetPlayer
      view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      (targetPlayer as? ExoPlayer)?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

      // Set up video size callback
      playerVideo.videoSizeChanged = { _, _ ->
        // Refresh resize mode when video size changes
        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      }
    } else {
      // Unbind player when not bound
      view.player = null
    }

    // Try to bind any pending views now that we have more instances
    if (hasPendingViews()) {
      bindPendingViews()
    }
  }

  override fun createViewInstance(reactContext: ThemedReactContext): MyPlayerView {
    return MyPlayerView(reactContext).apply {
      useController = false
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      player = null
      // Disable shutter view for faster display
      setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
  }

  override fun onDropViewInstance(view: MyPlayerView) {
    // Remove from pending views to prevent memory leak
    removePendingView(view)
    // Unbind player
    view.player = null
    super.onDropViewInstance(view)
  }
}
