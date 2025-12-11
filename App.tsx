import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  StyleSheet,
  View,
  FlatList,
  TouchableOpacity,
  ViewToken,
} from 'react-native';
import {
  useVideoPlayer,
  PlayerVideoView,
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

type ItemProps = {
  index: number;
  videoUrl: string;
  isVisible: boolean;
};

//

const VideoItem = ({ index, videoUrl, isVisible }: ItemProps) => {
  const player = useVideoPlayer(index);
  const hasLoaded = useRef(false);

  useEffect(() => {
    // Load video only once on first visibility
    if (isVisible && !hasLoaded.current) {
      console.log(`[Player ${index}] Loading video for the first time`);
      player.load(videoUrl, true, `video-${index}`, true, true, false);
      hasLoaded.current = true;
    }
    // Video continues playing in background - no stop/pause when not visible
  }, [isVisible, player, videoUrl, index]);

  return (
    <TouchableOpacity
      style={styles.itemContainer}
      activeOpacity={0.8}
      onPress={() => player.play()}
      onLongPress={() => player.pause()}
    >
      <View style={styles.player} pointerEvents={'none'}>
        <PlayerVideoView
          style={styles.player}
          isBoundToPlayer={isVisible}
          playerInstance={index}
        />
      </View>
    </TouchableOpacity>
  );
};

const VideoItemMemo = React.memo(VideoItem);

//

const items = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]; // 10 players

// Viewability configuration
const viewabilityConfig = {
  itemVisiblePercentThreshold: 30,
  minimumViewTime: 50,
};

//

export default function App() {
  const [visibleItems, setVisibleItems] = useState<Set<number>>(new Set([0, 1]));

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
    ({ item: index }: { item: number }) => (
      <VideoItemMemo
        index={index}
        videoUrl={VideoUrls[index % VideoUrls.length]}
        isVisible={visibleItems.has(index)}
      />
    ),
    [visibleItems]
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
        initialNumToRender={3}
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
    height: 250,
    marginBottom: 10,
  },
});
