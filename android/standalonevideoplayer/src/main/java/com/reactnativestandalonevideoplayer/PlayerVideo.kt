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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultHttpDataSource


//


class PlayerVideo(val context: Context) {

  companion object {
    var instances: MutableList<PlayerVideo> = mutableListOf()

    // Shared DataSource factory for all players (performance optimization)
    private var sharedDataSourceFactory: DefaultDataSource.Factory? = null

    fun getDataSourceFactory(context: Context): DefaultDataSource.Factory {
      if (sharedDataSourceFactory == null) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
          .setConnectTimeoutMs(8000)
          .setReadTimeoutMs(8000)
          .setAllowCrossProtocolRedirects(true)
        sharedDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
      }
      return sharedDataSourceFactory!!
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
    .setWakeMode(C.WAKE_MODE_NETWORK) // Keep network active during playback
    .build()

  private var listenerAdded = false

  var autoplay: Boolean = true

  var statusChanged: ((status: PlayerVideoStatus) -> Unit)? = null

  var progressChanged: ((progress: Double, duration: Double) -> Unit)? = null

  var videoSizeChanged: ((width: Int, height: Int) -> Unit)? = null

  var currentStatus: PlayerVideoStatus
    get() = status
    set(value) {}

  var isPlaying: Boolean
    get() = status == PlayerVideoStatus.playing
    set(value) {}

  var isLoaded: Boolean
    get() = status == PlayerVideoStatus.playing || status == PlayerVideoStatus.paused || status == PlayerVideoStatus.loading
    set(value) {}

  var isLoading: Boolean
    get() = status == PlayerVideoStatus.loading
    set(value) {}

  var volume: Float
    get() = player.volume
    set(value) { player.volume = value }

  var duration: Double
    get() {
      if (player.duration == C.TIME_UNSET) {
        Log.d("PlayerVideo", "DURRRRR: TIME_UNSET")
        return 0.0
      }

      val dur = player.duration.toDouble()

      Log.d("PlayerVideo", "DURRRRR: ${dur}")
      return dur
    }
    set(value){}

  var position: Double
    get() = player.currentPosition.toDouble()
    set(value){}

  var progress: Double
    get() {
      if (player.duration > 0) {
        return player.currentPosition.toDouble() / player.duration.toDouble()
      }

      return 0.0
    }
    set(value) {}

  //

  fun loadVideo(url: String, isHls: Boolean, loop: Boolean) {
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
    player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING

    setStatus(PlayerVideoStatus.new)

    // Add listener only once
    if (!listenerAdded) {
      listenerAdded = true
      player.addListener(object: Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          Log.d("PlayerVideo", "onPlaybackStateChanged = ${playbackState}")

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
          if (player.playbackState == Player.STATE_READY) {
            setStatus(if(isPlaying) PlayerVideoStatus.playing else PlayerVideoStatus.paused)
          }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
          Log.d("PlayerView", "onVideoSizeChanged width=${videoSize.width}, height=${videoSize.height}")
          videoSizeChanged?.invoke(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
          Log.e("PlayerVideo", "Playback error: ${error.errorCode}, ${error.message}")
          Log.e("PlayerVideo", "Error cause: ${error.cause}")
          setStatus(PlayerVideoStatus.error)
        }
      })
    }

    startProgressTimer()
  }

  fun play() {
    Log.d("PlayerVideo", "play")

    if (status === PlayerVideoStatus.finished) {
      seek(0.0)
    }

    player.playWhenReady = true

    startProgressTimer()
  }

  fun pause() {
    Log.d("PlayerVideo", "pause")

    player.playWhenReady = false

    stopProgressTimer()
  }

  fun stop() {
    Log.d("PlayerVideo", "stop")

    player.stop()
    player.clearMediaItems()

    setStatus(PlayerVideoStatus.stopped)

    stopProgressTimer()
  }

  fun clear() {
    Log.d("PlayerVideo", "clear - releasing resources")

    player.stop()
    player.clearMediaItems()

    setStatus(PlayerVideoStatus.none)

    stopProgressTimer()
  }

  fun seek(progress: Double) {
    Log.d("PlayerVideo", "seek: ${progress}")

    player.seekTo((duration * progress).toLong())
  }

  fun seekForward(time: Double) {
    Log.d("PlayerVideo", "Seek forward position=${position}, by=${time*1000}")

    player.seekTo((position + time*1000).toLong())
  }

  fun seekRewind(time: Double) {
    Log.d("PlayerVideo", "Seek rewind position=${position}, by=${time*1000}")

    player.seekTo((position - time * 1000).toLong())
  }

  fun release() {
    player.release()
  }

  //
  // Private
  //


  private fun setStatus(newStatus: PlayerVideoStatus) {
    status = newStatus

    Log.d("PlayerVideo", "NEW status = ${status}")

    statusChanged?.invoke(status)

    if (status == PlayerVideoStatus.stopped || status == PlayerVideoStatus.none || status == PlayerVideoStatus.error) {
      stopProgressTimer()
    }
  }

  private fun startProgressTimer() {
    if (isProgressTimerRunning) return // Don't start if already running

    isProgressTimerRunning = true

    if (progressHandler == null) {
      progressHandler = Handler(Looper.getMainLooper())
    }

    progressRunnable = object : Runnable {
      override fun run() {
        if (isProgressTimerRunning && player.isPlaying) {
          progressChanged?.invoke(progress, duration)
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
