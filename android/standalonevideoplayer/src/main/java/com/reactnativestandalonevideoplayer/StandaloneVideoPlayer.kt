package com.reactnativestandalonevideoplayer


import android.os.Handler
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class StandaloneVideoPlayer(val context: ReactApplicationContext): ReactContextBaseJavaModule(context), LifecycleEventListener {

  override fun getName(): String {
    return "StandaloneVideoPlayer"
  }

  //
  // Every ExoPlayer requires to operate on the same thread ("Player is accessed on the wrong thread")
  // We use main thread here (Handler(context.mainLooper).post)
  //

  //

  companion object {
    private const val TAG = "StandaloneVideoPlayer"
  }

  init {
    context.addLifecycleEventListener(this)

    // Create first instance synchronously to avoid race condition
    val instance = PlayerVideo(context)
    PlayerVideo.addInstance(instance)
  }

  //
  // LifecycleEventListener
  //
  override fun onHostResume() {
    // App resumed from background
  }

  override fun onHostPause() {
    // Don't stop players - allow background playback
    // Players will continue playing and maintain position
  }

  override fun onHostDestroy() {
    Handler(context.mainLooper).post {
      try {
        PlayerVideo.releaseAllInstances()
        PlayerContainerView.clearPendingViews()
      } catch (e: Exception) {
        Log.e(TAG, "Error in onHostDestroy: ${e.message}")
      }
    }
  }

  //
  //
  //

  @ReactMethod
  fun newInstance() {
    Handler(context.mainLooper).post {
      try {
        val instance = PlayerVideo(context)
        PlayerVideo.addInstance(instance)
      } catch (e: Exception) {
        Log.e(TAG, "Error creating new instance: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun load(instance: Int, url: String, isHls: Boolean, loop: Boolean, isSilent: Boolean) {
    if (instance < 0) {
      return
    }

    Handler(context.mainLooper).post {
      try {
        // Create new instances if needed
        while (PlayerVideo.instanceCount <= instance) {
          val newInstance = PlayerVideo(context)
          PlayerVideo.addInstance(newInstance)
        }

        // Always try to bind pending views after ensuring instances exist
        PlayerContainerView.bindPendingViews()

        val player = PlayerVideo.getInstance(instance) ?: return@post

        player.statusChanged = { status ->
          try {
            val map = Arguments.createMap()
            map.putInt("status", status.value)
            map.putInt("instance", instance)

            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
              .emit("PlayerStatusChanged", map)
          } catch (e: Exception) {
            Log.e(TAG, "Error emitting status: ${e.message}")
          }
        }

        player.progressChanged = { progress, duration ->
          try {
            val map = Arguments.createMap()
            map.putDouble("progress", progress)
            map.putDouble("duration", duration / 1000)
            map.putInt("instance", instance)

            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
              .emit("PlayerProgressChanged", map)
          } catch (e: Exception) {
            Log.e(TAG, "Error emitting progress: ${e.message}")
          }
        }

        player.loadVideo(url, isHls, loop)

        if (isSilent) {
          player.volume = 0f
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error in load: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun stop(instance: Int) {
    if (instance < 0) return

    Handler(context.mainLooper).post {
      try {
        PlayerVideo.getInstance(instance)?.let { player ->
          player.stop()
          player.statusChanged = null
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error in stop: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun play(instance: Int) {
    if (instance < 0) return

    Handler(context.mainLooper).post {
      try {
        PlayerVideo.getInstance(instance)?.play()
      } catch (e: Exception) {
        Log.e(TAG, "Error in play: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun pause(instance: Int) {
    if (instance < 0) return

    Handler(context.mainLooper).post {
      try {
        PlayerVideo.getInstance(instance)?.pause()
      } catch (e: Exception) {
        Log.e(TAG, "Error in pause: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun seek(instance: Int, position: Double) {
    if (instance < 0) return

    Handler(context.mainLooper).post {
      try {
        PlayerVideo.getInstance(instance)?.seek(position)
      } catch (e: Exception) {
        Log.e(TAG, "Error in seek: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun seekForward(instance: Int, time: Double) {
    if (instance < 0) return

    Handler(context.mainLooper).post {
      try {
        PlayerVideo.getInstance(instance)?.seekForward(time)
      } catch (e: Exception) {
        Log.e(TAG, "Error in seekForward: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun seekRewind(instance: Int, time: Double) {
    if (instance < 0) return

    Handler(context.mainLooper).post {
      try {
        PlayerVideo.getInstance(instance)?.seekRewind(time)
      } catch (e: Exception) {
        Log.e(TAG, "Error in seekRewind: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun setVolume(instance: Int, volume: Float) {
    if (instance < 0) return

    Handler(context.mainLooper).post {
      try {
        PlayerVideo.getInstance(instance)?.let { it.volume = volume }
      } catch (e: Exception) {
        Log.e(TAG, "Error in setVolume: ${e.message}")
      }
    }
  }

  @ReactMethod
  fun getDuration(instance: Int, promise: Promise) {
    if (instance < 0) {
      promise.resolve(0)
      return
    }

    Handler(context.mainLooper).post {
      try {
        val player = PlayerVideo.getInstance(instance)
        val duration = player?.duration?.div(1000) ?: 0.0
        promise.resolve(duration)
      } catch (e: Exception) {
        Log.e(TAG, "Error in getDuration: ${e.message}")
        promise.resolve(0)
      }
    }
  }

  @ReactMethod
  fun getProgress(instance: Int, promise: Promise) {
    if (instance < 0) {
      promise.resolve(0)
      return
    }

    Handler(context.mainLooper).post {
      try {
        val player = PlayerVideo.getInstance(instance)
        val progress = player?.progress ?: 0.0
        promise.resolve(progress)
      } catch (e: Exception) {
        Log.e(TAG, "Error in getProgress: ${e.message}")
        promise.resolve(0)
      }
    }
  }

  @ReactMethod
  fun addListener(eventName: String) {
    // Required for RN NativeEventEmitter
  }

  @ReactMethod
  fun removeListeners(count: Int) {
    // Required for RN NativeEventEmitter
  }
}
