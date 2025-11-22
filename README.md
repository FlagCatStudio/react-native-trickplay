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

## 📄 License

MIT © [MalguyMQ](https://github.com/MalguyMQ)


## 🐛 Issues

Found a bug? [Open an issue](https://github.com/MalguyMQ/react-native-trickplay/issues)

## 🙏 Credits

Built with:
- [Expo Modules API](https://docs.expo.dev/modules/)
- [AVFoundation](https://developer.apple.com/av-foundation/) (iOS)
- [Media3 ExoPlayer](https://developer.android.com/media/media3) (Android)
