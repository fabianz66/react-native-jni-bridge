import React, { useState } from 'react';
import {
  View,
  TextInput,
  TouchableOpacity,
  Text,
  StyleSheet,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

const DEFAULT_URL = 'http://10.0.0.2/master.m3u8';

export default function HomeScreen({ navigation }: Props) {
  const [url, setUrl] = useState(DEFAULT_URL);

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Stream URL</Text>
      <TextInput
        style={styles.input}
        value={url}
        onChangeText={setUrl}
        placeholder="http://..."
        placeholderTextColor="#555"
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
        selectTextOnFocus
      />
      <TouchableOpacity
        style={[styles.button, !url && styles.buttonDisabled]}
        onPress={() => url && navigation.navigate('Player', { url })}
        activeOpacity={0.8}>
        <Text style={styles.buttonText}>▶  Play</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
    backgroundColor: '#000',
  },
  label: {
    color: '#888',
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 8,
  },
  input: {
    backgroundColor: '#1a1a1a',
    color: '#fff',
    borderRadius: 8,
    padding: 14,
    fontSize: 15,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#333',
  },
  button: {
    backgroundColor: '#e50914',
    borderRadius: 8,
    padding: 16,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.4,
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '700',
  },
});
