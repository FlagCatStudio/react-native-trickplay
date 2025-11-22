# react-native-trickplay

**Fast HLS I-Frame extraction for React Native** - Extract video thumbnails from HLS streams for smooth trick play (scrubbing) functionality.

[![npm version](https://img.shields.io/npm/v/react-native-trickplay.svg)](https://www.npmjs.com/package/react-native-trickplay)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 🎯 Features

- ⚡ **Instant keyframe extraction** from HLS I-FRAMES-ONLY playlists
- 📱 **Native performance** on iOS (AVFoundation) and Android (Media3 ExoPlayer)
- 🎬 **Optimized for scrubbing** - Extract frames at any timestamp with minimal latency
- 🔄 **Automatic master manifest handling** - Works with master playlists
- 📐 **Flexible sizing** - Optional target dimensions with automatic aspect ratio preservation
- 🎯 **Smart track selection** - Automatically picks optimal quality variant (360p default)
- 📦 **Zero configuration** - Works out of the box with Shaka Packager generated HLS streams

## 📋 Requirements

- React Native 0.74+
- Expo SDK 51+
- iOS 13.0+
- Android API 24+ (Android 7.0+)

## 🚀 Installation

```bash
npm install react-native-trickplay
```

Or with yarn:

```bash
yarn add react-native-trickplay
```

### Configure native projects

For **bare React Native** projects, run:

```bash
npx pod-install
```

For **Expo managed** projects, rebuild your app:

```bash
npx expo prebuild --clean
npx expo run:ios
npx expo run:android
```

## 📖 Usage

### Basic Example

```typescript
import ReactNativeTrickplay from 'react-native-trickplay';
import { Image } from 'react-native';

// Extract a frame at 5 seconds with target dimensions
async function getThumbnail() {
  const result = await ReactNativeTrickplay.extractFrameAsync(
    'https://example.com/video/descriptor.m3u8',  // Master manifest or I-Frame playlist
    5.0,      // Timestamp in seconds
    90,       // Target width (optional)
    160       // Target height (optional)
  );

  // Display the extracted frame (result.uri is a local file path)
  return (
    <Image 
      source={{ uri: result.uri }}
      style={{ width: result.width, height: result.height }}
    />
  );
}
```

### Advanced Example: Scrubbing Preview

```typescript
import { useState, useRef } from 'react';
import ReactNativeTrickplay from 'react-native-trickplay';
import { View, Image, Slider, Text } from 'react-native';

function VideoScrubber({ videoUrl, duration }) {
  const [currentTime, setCurrentTime] = useState(0);
  const [thumbnail, setThumbnail] = useState<string | null>(null);
  const debounceRef = useRef<NodeJS.Timeout | null>(null);

  const handleSeek = (time: number) => {
    setCurrentTime(time);

    // Debounce extraction to avoid too many calls
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    debounceRef.current = setTimeout(async () => {
      try {
        // Extract with target dimensions 90x160 (9:16 vertical format)
        const result = await ReactNativeTrickplay.extractFrameAsync(
          videoUrl,
          time,
          90,   // Target width
          160   // Target height
        );
        setThumbnail(result.uri);
      } catch (error) {
        console.error('Failed to extract frame:', error);
      }
    }, 100);
  };

  return (
    <View>
      {thumbnail && (
        <Image
          source={{ uri: thumbnail }}
          style={{ width: 90, height: 160, borderRadius: 8 }}
        />
      )}
      <Slider
        value={currentTime}
        minimumValue={0}
        maximumValue={duration}
        onValueChange={handleSeek}
      />
      <Text>{currentTime.toFixed(1)}s / {duration}s</Text>
    </View>
  );
}
```

## 🎬 HLS I-Frame Playlists

This library is designed to work with **HLS I-FRAMES-ONLY** playlists, which contain only keyframes (I-Frames) for fast seeking.
You can pass:
- The master manifest URL - iOS/Android will auto-select the best I-Frame variant

## 📚 API Reference

### `extractFrameAsync(url, seconds, targetWidth?, targetHeight?): Promise<ExtractFrameResult>`

Extracts a video frame at the specified timestamp with optional target dimensions.

**Parameters:**
- `url` (string): URL to HLS master manifest or I-Frame playlist
- `seconds` (number): Timestamp in seconds where to extract the frame
- `targetWidth` (number, optional): Target width in pixels (preserves aspect ratio if height not provided)
- `targetHeight` (number, optional): Target height in pixels (preserves aspect ratio if width not provided)

**Returns:** Promise resolving to:
```typescript
{
  uri: string;        // Local file path (file://...) to extracted JPEG
  width: number;      // Frame width in pixels
  height: number;     // Frame height in pixels
}
```

**Dimension Behavior:**
- **No dimensions**: Uses video track resolution (e.g., 360x640 for 360p)
- **Width only**: Calculates height to preserve aspect ratio
- **Height only**: Calculates width to preserve aspect ratio
- **Both dimensions**: Uses exact dimensions (may stretch if aspect ratio differs)

**Throws:** Error if:
- URL is invalid or inaccessible
- Video stream cannot be loaded
- Frame extraction fails

## ⚡ Performance

### Why This Library Is Fast

1. **Keyframe-only extraction** - I-Frames are self-contained, no P-frame/B-frame decoding needed
2. **Native tolerance configuration** - Both iOS and Android seek to nearest keyframe
3. **Optimized threading** - All operations run on background threads
4. **Efficient encoding** - JPEG at 80% quality balances size and quality

### Expected Performance

- **First extraction**: 300-1200ms (includes stream initialization and track selection)
- **Subsequent extractions**: 100-500ms (cached segments) or 50-150ms (same segment)
- **Memory usage**: ~20-40 KB per frame at 360p, ~2-5 KB per frame at 90x160
- **Automatic optimization**: ExoPlayer caches segments, iOS pre-buffers keyframes

### Tips for Production

1. **Use target dimensions** - Extract at display size (e.g., 90x160) for 10x smaller files
2. **Debounce scrubbing** - 100-200ms debounce prevents excessive extractions
3. **Automatic cleanup** - Module uses LRU cache (max 10 frames) to prevent storage bloat
4. **Master manifest** - Let native track selector choose optimal quality automatically

## 🏗️ Architecture

### iOS (Swift + AVFoundation)

- Uses `AVAssetImageGenerator` with infinite tolerances for keyframe-only extraction
- Natively handles HLS playlists including I-FRAMES-ONLY variants
- Async/await for clean concurrency

### Android (Kotlin + Media3 ExoPlayer)

- Uses ExoPlayer for robust HLS support
- Headless Surface rendering for frame extraction
- Coroutines for async operations with proper cleanup

## 📄 License

MIT © [MalguyMQ](https://github.com/MalguyMQ)


## 🐛 Issues

Found a bug? [Open an issue](https://github.com/MalguyMQ/react-native-trickplay/issues)

## 🙏 Credits

Built with:
- [Expo Modules API](https://docs.expo.dev/modules/)
- [AVFoundation](https://developer.apple.com/av-foundation/) (iOS)
- [Media3 ExoPlayer](https://developer.android.com/media/media3) (Android)
