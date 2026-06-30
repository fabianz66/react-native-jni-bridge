import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  NativeModules,
  Platform,
  DeviceEventEmitter,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../App';
import VideoPlayer from '../components/VideoPlayer';

type Props = NativeStackScreenProps<RootStackParamList, 'Player'>;

type PlayerStateEvent = { state: string; error?: string };

const STATE_COLORS: Record<string, string> = {
  idle:      '#6b7280',
  buffering: '#f59e0b',
  playing:   '#22c55e',
  paused:    '#60a5fa',
  ended:     '#a78bfa',
  error:     '#f87171',
};

function stateColor(state: string): string {
  return STATE_COLORS[state] ?? '#6b7280';
}

const { JniBridge } = NativeModules;

export default function PlayerScreen({ route }: Props) {
  const { url } = route.params;
  const [jniString, setJniString] = useState<string>('…');
  const [playerState, setPlayerState] = useState<string>('idle');
  const [playerError, setPlayerError] = useState<string | null>(null);

  // Fetch the C++ string once on mount via JNI.
  useEffect(() => {
    if (Platform.OS === 'android' && JniBridge) {
      JniBridge.getString()
        .then((s: string) => setJniString(s))
        .catch(() => setJniString('JNI call failed'));
    } else {
      setJniString('JNI only available on Android');
    }
  }, []);

  // Subscribe to ExoPlayer state change events emitted by VideoPlayerViewManager.
  // The listener is removed on unmount to avoid stale callbacks after navigation.
  useEffect(() => {
    const sub = DeviceEventEmitter.addListener(
      'onPlayerStateChanged',
      (e: PlayerStateEvent) => {
        setPlayerState(e.state);
        setPlayerError(e.error ?? null);
      },
    );
    return () => sub.remove();
  }, []);

  const color = stateColor(playerState);

  return (
    <View style={styles.container}>
      <VideoPlayer style={styles.player} url={url} />

      <View style={styles.infoBox}>
        {/* Player state row */}
        <View style={styles.row}>
          <Text style={styles.sectionLabel}>PLAYER STATE</Text>
          <View style={styles.badge}>
            <View style={[styles.dot, { backgroundColor: color }]} />
            <Text style={[styles.stateText, { color }]}>{playerState}</Text>
          </View>
        </View>
        {playerError != null && (
          <Text style={styles.errorText} numberOfLines={2}>
            {playerError}
          </Text>
        )}

        <View style={styles.divider} />

        {/* JNI string */}
        <Text style={styles.sectionLabel}>C++ / JNI</Text>
        <Text style={styles.jniText}>{jniString}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  player: {
    flex: 1,
  },
  infoBox: {
    padding: 16,
    backgroundColor: '#0d0d0d',
    borderTopWidth: 1,
    borderTopColor: '#222',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sectionLabel: {
    color: '#555',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  stateText: {
    fontSize: 13,
    fontWeight: '600',
    fontFamily: 'monospace',
  },
  errorText: {
    marginTop: 4,
    color: '#f87171',
    fontSize: 12,
    fontFamily: 'monospace',
  },
  divider: {
    height: 1,
    backgroundColor: '#1e1e1e',
    marginVertical: 12,
  },
  jniText: {
    marginTop: 4,
    color: '#4ade80',
    fontSize: 14,
    fontFamily: 'monospace',
  },
});
