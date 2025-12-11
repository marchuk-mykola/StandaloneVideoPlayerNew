package com.reactnativestandalonevideoplayer


import android.content.Context
import android.util.Log
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
    // Views waiting for player instances to be created
    val pendingViews: MutableList<MyPlayerView> = mutableListOf()

    fun bindPendingViews() {
      val iterator = pendingViews.iterator()
      while (iterator.hasNext()) {
        val view = iterator.next()
        if (view.playerInstance >= 0 && view.playerInstance < PlayerVideo.instances.size) {
          Log.d("PlayerView", "Binding pending view for instance ${view.playerInstance}")
          view.player = if (view.isBound) PlayerVideo.instances[view.playerInstance].player else null
          iterator.remove()
        }
      }
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
    if (view.playerInstance >= PlayerVideo.instances.size) {
      if (!pendingViews.contains(view)) {
        pendingViews.add(view)
      }
      return
    }

    val targetPlayer = if (view.isBound) PlayerVideo.instances[view.playerInstance].player else null

    // Only update if player changed (performance optimization)
    if (view.player != targetPlayer) {
      view.player = targetPlayer
      view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
      (targetPlayer as? ExoPlayer)?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
    }

    // Set up video size callback only once when bound
    if (view.isBound && targetPlayer != null) {
      PlayerVideo.instances[view.playerInstance].videoSizeChanged = { _, _ ->
        // Just refresh resize mode, don't call full setup
        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
      }
    }
  }

  //

  override fun createViewInstance(reactContext: ThemedReactContext): MyPlayerView {
    return MyPlayerView(reactContext).apply {
      useController = false
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
      player = null
      // Disable shutter view for faster display
      setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
  }

}
