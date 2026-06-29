import { requireNativeComponent, ViewStyle } from 'react-native';

interface VideoPlayerProps {
  url: string;
  style?: ViewStyle;
}

export default requireNativeComponent<VideoPlayerProps>('VideoPlayer');
