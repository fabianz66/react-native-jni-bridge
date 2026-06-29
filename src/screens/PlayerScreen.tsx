import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, NativeModules, Platform } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../App';
import VideoPlayer from '../components/VideoPlayer';

type Props = NativeStackScreenProps<RootStackParamList, 'Player'>;

const { JniBridge } = NativeModules;

export default function PlayerScreen({ route }: Props) {
  const { url } = route.params;
  const [jniString, setJniString] = useState<string>('…');

  useEffect(() => {
    if (Platform.OS === 'android' && JniBridge) {
      JniBridge.getString()
        .then((s: string) => setJniString(s))
        .catch(() => setJniString('JNI call failed'));
    } else {
      setJniString('JNI only available on Android');
    }
  }, []);

  return (
    <View style={styles.container}>
      <VideoPlayer style={styles.player} url={url} />
      <View style={styles.jniBox}>
        <Text style={styles.jniLabel}>C++ / JNI</Text>
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
  jniBox: {
    padding: 16,
    backgroundColor: '#0d0d0d',
    borderTopWidth: 1,
    borderTopColor: '#222',
  },
  jniLabel: {
    color: '#555',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  jniText: {
    color: '#4ade80',
    fontSize: 14,
    fontFamily: 'monospace',
  },
});
