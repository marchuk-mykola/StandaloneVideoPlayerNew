package com.reactnativestandalonevideoplayer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import java.util.concurrent.atomic.AtomicBoolean


//


class PlayerVideo(val context: Context) {

  companion object {
    // Thread-safe instance management
    private val instancesLock = Any()
    private val _instances: MutableList<PlayerVideo> = mutableListOf()

    // Thread-safe read-only access to instances list
    val instances: List<PlayerVideo>
      get() = synchronized(instancesLock) { _instances.toList() }

    val instanceCount: Int
      get() = synchronized(instancesLock) { _instances.size }

    fun addInstance(instance: PlayerVideo) = synchronized(instancesLock) {
      _instances.add(instance)
    }

    fun getInstance(index: Int): PlayerVideo? = synchronized(instancesLock) {
      _instances.getOrNull(index)
    }

    fun clearInstances() = synchronized(instancesLock) {
      _instances.clear()
    }

    fun releaseAllInstances() = synchronized(instancesLock) {
      _instances.forEach { it.release() }
      _instances.clear()
    }

    // Thread-safe DataSource factory (double-checked locking)
    @Volatile
    private var sharedDataSourceFactory: DefaultDataSource.Factory? = null
    private val factoryLock = Any()

    fun getDataSourceFactory(context: Context): DefaultDataSource.Factory {
      return sharedDataSourceFactory ?: synchronized(factoryLock) {
        sharedDataSourceFactory ?: DefaultDataSource.Factory(
          context.applicationContext,
          DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
        ).also { sharedDataSourceFactory = it }
      }
    }
  }

  private var status: PlayerVideoStatus = PlayerVideoStatus.none

  private var progressHandler: Handler? = null
  private var progressRunnable: Runnable? = null
  private var isProgressTimerRunning = false

  private val PROGRESS_UPDATE_TIME: Long = 500 // Faster updates for smoother UI

  //

  // Optimized load control for faster startup and lower memory usage
  private val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
      1500,   // Min buffer before playback starts - faster start
      15000,  // Max buffer - balance between memory and smoothness
      500,    // Buffer for playback - minimal delay
      1500    // Buffer for rebuffer
    )
    .setPrioritizeTimeOverSizeThresholds(true)
    .setTargetBufferBytes(C.LENGTH_UNSET) // Don't limit by size
    .build()

  // Optimized renderers for faster decoding
  private val renderersFactory = DefaultRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    .setEnableDecoderFallback(true)

  val player = ExoPlayer.Builder(context)
    .setLoadControl(loadControl)
    .setRenderersFactory(renderersFactory)
    .setHandleAudioBecomingNoisy(true) // Pause when headphones unplugged
    .build()

  // Player listener reference for cleanup
  private var playerListener: Player.Listener? = null

  // Atomic flag to prevent race conditions during release
  private val _isReleased = AtomicBoolean(false)
  val isReleased: Boolean get() = _isReleased.get()

  var autoplay: Boolean = true

  var statusChanged: ((status: PlayerVideoStatus) -> Unit)? = null

  var progressChanged: ((progress: Double, duration: Double) -> Unit)? = null

  var videoSizeChanged: ((width: Int, height: Int) -> Unit)? = null

  val currentStatus: PlayerVideoStatus
    get() = status

  val isPlaying: Boolean
    get() = status == PlayerVideoStatus.playing

  val isLoaded: Boolean
    get() = status == PlayerVideoStatus.playing || status == PlayerVideoStatus.paused || status == PlayerVideoStatus.loading

  val isLoading: Boolean
    get() = status == PlayerVideoStatus.loading

  var volume: Float
    get() = player.volume
    set(value) {
      if (!isReleased) {
        player.volume = value.coerceIn(0f, 1f)
      }
    }

  val duration: Double
    get() {
      if (player.duration == C.TIME_UNSET) {
        return 0.0
      }
      return player.duration.toDouble()
    }

  val position: Double
    get() = player.currentPosition.toDouble()

  val progress: Double
    get() {
      if (player.duration > 0) {
        return player.currentPosition.toDouble() / player.duration.toDouble()
      }
      return 0.0
    }

  //

  fun loadVideo(url: String, isHls: Boolean, loop: Boolean) {
    if (isReleased) {
      Log.w("PlayerVideo", "Cannot load video - player is released")
      return
    }

    // Use shared DataSource factory for better performance
    val dataSourceFactory = getDataSourceFactory(context)

    val mediaItem = MediaItem.fromUri(Uri.parse(url))

    // Create appropriate media source
    val mediaSource = if(isHls) {
      HlsMediaSource.Factory(dataSourceFactory)
        .setAllowChunklessPreparation(true) // Faster HLS start
        .createMediaSource(mediaItem)
    } else {
      ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(mediaItem)
    }

    // Prepare the player with the source.
    player.setMediaSource(mediaSource)
    player.prepare()

    player.playWhenReady = autoplay
    player.repeatMode = if(loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

    setStatus(PlayerVideoStatus.new)

    // Add listener only once (store reference for cleanup)
    if (playerListener == null) {
      playerListener = object: Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (isReleased) return
          when(playbackState) {
            Player.STATE_IDLE -> {} // Don't change status on idle, we manage it manually
            Player.STATE_BUFFERING -> setStatus(PlayerVideoStatus.loading)
            Player.STATE_READY -> setStatus(if(player.playWhenReady) PlayerVideoStatus.playing else PlayerVideoStatus.paused)
            Player.STATE_ENDED -> {
              setStatus(PlayerVideoStatus.finished)
              stopProgressTimer()
            }
          }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isReleased) return
          if (player.playbackState == Player.STATE_READY) {
            setStatus(if(isPlaying) PlayerVideoStatus.playing else PlayerVideoStatus.paused)
          }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
          if (isReleased) return
          videoSizeChanged?.invoke(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
          if (isReleased) return
          Log.e("PlayerVideo", "Playback error: ${error.errorCode}, ${error.message}")
          setStatus(PlayerVideoStatus.error)
        }
      }
      player.addListener(playerListener!!)
    }

    startProgressTimer()
  }

  fun play() {
    if (isReleased) return

    if (status === PlayerVideoStatus.finished) {
      seek(0.0)
    }

    player.playWhenReady = true
    startProgressTimer()
  }

  fun pause() {
    if (isReleased) return
    player.playWhenReady = false
  }

  fun stop() {
    if (isReleased) return

    player.stop()
    player.clearMediaItems()
    setStatus(PlayerVideoStatus.stopped)
    stopProgressTimer()
  }

  fun clear() {
    if (isReleased) return

    player.stop()
    player.clearMediaItems()
    setStatus(PlayerVideoStatus.none)
    stopProgressTimer()
  }

  fun seek(progress: Double) {
    if (isReleased) return
    val clampedProgress = progress.coerceIn(0.0, 1.0)
    player.seekTo((duration * clampedProgress).toLong())
    // Immediately notify progress change after seek
    progressChanged?.invoke(clampedProgress, duration)
  }

  fun seekForward(time: Double) {
    if (isReleased || time < 0) return
    val maxPosition = if (player.duration > 0) player.duration else Long.MAX_VALUE
    val newPosition = (position + time * 1000).toLong().coerceAtMost(maxPosition)
    player.seekTo(newPosition)
    // Immediately notify progress change after seek
    progressChanged?.invoke(this.progress, duration)
  }

  fun seekRewind(time: Double) {
    if (isReleased || time < 0) return
    val newPosition = (position - time * 1000).toLong().coerceAtLeast(0)
    player.seekTo(newPosition)
    // Immediately notify progress change after seek
    progressChanged?.invoke(this.progress, duration)
  }

  fun release() {
    // Atomic check-and-set to prevent double release
    if (!_isReleased.compareAndSet(false, true)) return

    stopProgressTimer()

    // Remove listener to prevent memory leaks
    playerListener?.let { player.removeListener(it) }
    playerListener = null

    // Clear callbacks to prevent memory leaks
    statusChanged = null
    progressChanged = null
    videoSizeChanged = null

    // Clear handler completely
    progressHandler?.removeCallbacksAndMessages(null)
    progressHandler = null

    player.release()
  }

  //
  // Private
  //


  private fun setStatus(newStatus: PlayerVideoStatus) {
    status = newStatus
    statusChanged?.invoke(status)

    if (status == PlayerVideoStatus.stopped || status == PlayerVideoStatus.none || status == PlayerVideoStatus.error) {
      stopProgressTimer()
    }
  }

  private fun startProgressTimer() {
    if (isProgressTimerRunning || isReleased) return // Don't start if already running or released

    isProgressTimerRunning = true

    if (progressHandler == null) {
      progressHandler = Handler(Looper.getMainLooper())
    }

    progressRunnable = object : Runnable {
      override fun run() {
        if (isProgressTimerRunning && !isReleased) {
          // Always send progress update
          progressChanged?.invoke(progress, duration)
          // Always continue the timer loop
          progressHandler?.postDelayed(this, PROGRESS_UPDATE_TIME)
        }
      }
    }

    progressHandler?.post(progressRunnable!!)
  }

  private fun stopProgressTimer() {
    isProgressTimerRunning = false
    progressRunnable?.let {
      progressHandler?.removeCallbacks(it)
    }
    progressRunnable = null
  }

}


enum class PlayerVideoStatus(val value: Int) {
  new(0),
  loading(1),
  playing(2),
  paused(3),
  error(4),
  stopped(5),
  none(6),
  finished(7)
}
