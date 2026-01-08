import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  StyleSheet,
  View,
  FlatList,
  TouchableOpacity,
  ViewToken,
  Text,
} from 'react-native';
import Slider from '@react-native-community/slider';
import {
  useVideoPlayer,
  PlayerVideoView,
  usePlayerVideoProgress,
  usePlayerVideoStatus,
  usePlayerVideoSize,
  PlayerVideoManager,
  PlayerStatus,
} from './src/StandaloneVideoPlayer';

//

// Video URLs
const VideoUrls = [
  'https://w.ising.pl/mediadev/_definst_/5877/65035b067a99b3bb406c635d204ec9ff/playlist.m3u8?access_token=01d9a9517c6c0e6deabcde28a86b8abc27d2a0b8',
  'https://w.ising.pl/media/play/uv/1719612/331b547860b06d46648074c7b13dedeb/playlist.m3u8?client_id=9ce6c5de0e32e347d998e37fd9a13bca',
  'https://w.ising.pl/media/play/uv/2327076/dae5efbb10232873ad535f3b10d6da9f/playlist.m3u8?client_id=9ce6c5de0e32e347d998e37fd9a13bca',
  'https://w.ising.pl/mediadev/_definst_/5185/f0ea304ff2d824b528cd9efbcec0f428/playlist.m3u8?access_token=01d9a9517c6c0e6deabcde28a86b8abc27d2a0b8',
];

//

// Sync configuration: players 1 and 2 will be synchronized
const SYNCED_PLAYERS = [1, 2];
const SYNC_VIDEO_URL = VideoUrls[1]; // Both synced players use the same video

type ItemProps = {
  index: number;
  videoUrl: string;
  isVisible: boolean;
  isSynced: boolean;
  onSyncAction?: (action: 'play' | 'pause' | 'seek' | 'seekForward' | 'seekRewind', value?: number) => void;
};

//

const formatTime = (seconds: number) => {
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
};

const VideoItem = ({ index, videoUrl, isVisible, isSynced, onSyncAction }: ItemProps) => {
  const player = useVideoPlayer(index);
  const hasLoaded = useRef(false);
  const [isMuted, setIsMuted] = useState(false);
  const [isSeeking, setIsSeeking] = useState(false);
  const { progress, duration } = usePlayerVideoProgress(index);
  const { status } = usePlayerVideoStatus(index);
  const videoSize = usePlayerVideoSize(index);

  // Calculate aspect ratio from video dimensions, default to 16:9
  const aspectRatio = videoSize.width > 0 && videoSize.height > 0
    ? videoSize.width / videoSize.height
    : 16 / 9;

  // Derive isPaused from actual player status
  const isPaused = status === PlayerStatus.paused || status === PlayerStatus.stopped || status === PlayerStatus.finished;

  useEffect(() => {
    // Load video only once on first visibility
    if (isVisible && !hasLoaded.current) {
      console.log(`[Player ${index}] Loading video for the first time`);
      player.load(videoUrl, true, `video-${index}`, true, true, false);
      hasLoaded.current = true;
    }
    // Video continues playing in background - no stop/pause when not visible
  }, [isVisible, player, videoUrl, index]);

  const togglePlayPause = () => {
    if (isPaused) {
      player.play();
      if (isSynced && onSyncAction) onSyncAction('play');
    } else {
      player.pause();
      if (isSynced && onSyncAction) onSyncAction('pause');
    }
  };

  const toggleMute = () => {
    const newMuted = !isMuted;
    setIsMuted(newMuted);
    // @ts-ignore
    PlayerVideoManager.setVolume(index, newMuted ? 0 : 1);
  };

  const handleSeek = (value: number) => {
    setIsSeeking(false);
    player.seek(value);
    if (isSynced && onSyncAction) onSyncAction('seek', value);
  };

  const handleSeekRewind = () => {
    player.seekRewind(10);
    if (isSynced && onSyncAction) onSyncAction('seekRewind', 10);
  };

  const handleSeekForward = () => {
    player.seekForward(10);
    if (isSynced && onSyncAction) onSyncAction('seekForward', 10);
  };

  const currentTime = duration > 0 ? progress * duration : 0;

  return (
    <View style={[styles.itemContainer, { aspectRatio }]}>
      {isSynced && (
        <View style={styles.syncBadge}>
          <Text style={styles.syncBadgeText}>SYNCED</Text>
        </View>
      )}
      <View style={styles.player} pointerEvents={'none'}>
        <PlayerVideoView
          style={styles.player}
          isBoundToPlayer={isVisible}
          playerInstance={index}
        />
      </View>
      <View style={styles.controlsContainer}>
        <View style={styles.progressContainer}>
          <Text style={styles.timeText}>{formatTime(currentTime)}</Text>
          <Slider
            style={styles.slider}
            minimumValue={0}
            maximumValue={1}
            value={isSeeking ? undefined : progress}
            onSlidingStart={() => setIsSeeking(true)}
            onSlidingComplete={handleSeek}
            minimumTrackTintColor={isSynced ? '#00FF00' : '#FFFFFF'}
            maximumTrackTintColor="rgba(255, 255, 255, 0.3)"
            thumbTintColor={isSynced ? '#00FF00' : '#FFFFFF'}
          />
          <Text style={styles.timeText}>{formatTime(duration)}</Text>
        </View>
        <View style={styles.controls}>
          <TouchableOpacity style={styles.controlButton} onPress={handleSeekRewind}>
            <Text style={styles.controlButtonText}>-10s</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.controlButton} onPress={togglePlayPause}>
            <Text style={styles.controlButtonText}>{isPaused ? '▶' : '⏸'}</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.controlButton} onPress={handleSeekForward}>
            <Text style={styles.controlButtonText}>+10s</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.controlButton} onPress={toggleMute}>
            <Text style={styles.controlButtonText}>{isMuted ? '🔇' : '🔊'}</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
};

const VideoItemMemo = React.memo(VideoItem);

//

const items = [0, 1, 2, 3]; // 4 players

// Viewability configuration
const viewabilityConfig = {
  itemVisiblePercentThreshold: 30,
  minimumViewTime: 50,
};

//

export default function App() {
  const [visibleItems, setVisibleItems] = useState<Set<number>>(new Set([0, 1]));

  // Players for synced control
  const player1 = useVideoPlayer(SYNCED_PLAYERS[0]);
  const player2 = useVideoPlayer(SYNCED_PLAYERS[1]);

  const handleSyncAction = useCallback((sourceIndex: number, action: 'play' | 'pause' | 'seek' | 'seekForward' | 'seekRewind', value?: number) => {
    // Apply action to all synced players except the source
    SYNCED_PLAYERS.forEach((playerIndex) => {
      if (playerIndex !== sourceIndex) {
        const targetPlayer = playerIndex === SYNCED_PLAYERS[0] ? player1 : player2;
        switch (action) {
          case 'play':
            targetPlayer.play();
            break;
          case 'pause':
            targetPlayer.pause();
            break;
          case 'seek':
            if (value !== undefined) targetPlayer.seek(value);
            break;
          case 'seekForward':
            if (value !== undefined) targetPlayer.seekForward(value);
            break;
          case 'seekRewind':
            if (value !== undefined) targetPlayer.seekRewind(value);
            break;
        }
      }
    });
  }, [player1, player2]);

  const onViewableItemsChanged = useCallback(
    ({ viewableItems }: { viewableItems: ViewToken[] }) => {
      const visible = new Set(
        viewableItems
          .filter((item) => item.isViewable)
          .map((item) => item.index as number)
      );
      setVisibleItems(visible);
    },
    []
  );

  const viewabilityConfigCallbackPairs = useRef([
    { viewabilityConfig, onViewableItemsChanged },
  ]);

  const renderItem = useCallback(
    ({ item: index }: { item: number }) => {
      const isSynced = SYNCED_PLAYERS.includes(index);
      const videoUrl = isSynced ? SYNC_VIDEO_URL : VideoUrls[index % VideoUrls.length];

      return (
        <VideoItemMemo
          index={index}
          videoUrl={videoUrl}
          isVisible={visibleItems.has(index)}
          isSynced={isSynced}
          onSyncAction={(action, value) => handleSyncAction(index, action, value)}
        />
      );
    },
    [visibleItems, handleSyncAction]
  );

  return (
    <View style={styles.container}>
      <FlatList
        style={styles.list}
        data={items}
        keyExtractor={(item) => item.toString()}
        renderItem={renderItem}
        viewabilityConfigCallbackPairs={viewabilityConfigCallbackPairs.current}
        removeClippedSubviews={false}
        maxToRenderPerBatch={5}
        windowSize={11}
        initialNumToRender={4}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'flex-start',
    justifyContent: 'flex-start',
  },
  list: {
    width: '100%',
    height: '100%',
    backgroundColor: 'black',
  },
  player: {
    width: '100%',
    height: '100%',
    backgroundColor: 'black',
  },
  itemContainer: {
    width: '100%',
    marginBottom: 10,
  },
  controlsContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  progressContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  slider: {
    flex: 1,
    height: 40,
    marginHorizontal: 8,
  },
  timeText: {
    color: 'white',
    fontSize: 12,
    minWidth: 40,
    textAlign: 'center',
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 16,
  },
  controlButton: {
    width: 50,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  controlButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: 'bold',
  },
  syncBadge: {
    position: 'absolute',
    top: 10,
    left: 10,
    backgroundColor: '#00FF00',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    zIndex: 10,
  },
  syncBadgeText: {
    color: 'black',
    fontSize: 10,
    fontWeight: 'bold',
  },
});
