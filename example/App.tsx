import { SafeAreaView, ScrollView, Text, View, Image, ActivityIndicator } from 'react-native';
import { useState, useCallback, useRef } from 'react';
import Slider from '@react-native-community/slider';
import ReactNativeTrickplay from 'react-native-trickplay';

export default function App() {
  const [extractedFrameUri, setExtractedFrameUri] = useState<string | null>(null);
  const [isExtracting, setIsExtracting] = useState(false);
  const [extractionError, setExtractionError] = useState<string | null>(null);
  const [sliderValue, setSliderValue] = useState(0);
  const [videoDuration] = useState(60);
  const [lastExtractionTime, setLastExtractionTime] = useState<number | null>(null);
  const extractionTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  
  const handleSliderChange = useCallback(async (value: number) => {
    setSliderValue(value);
    
    if (extractionTimeoutRef.current) {
      clearTimeout(extractionTimeoutRef.current);
    }
    
    extractionTimeoutRef.current = setTimeout(async () => {
      const startTime = Date.now();
      setIsExtracting(true);
      setExtractionError(null);
      
      try {
        // Using master descriptor with target dimensions 90x160 (vertical 9:16 format)
        // ExoPlayer will pick the 360p track and resize to preserve aspect ratio
        const result = await ReactNativeTrickplay.extractFrameAsync(
          'https://www.shortcat.fr/v/aaafa04e-3050-472d-ab9e-7bb90a05ad6d/s/descriptor.m3u8',
          value,
          90,   // targetWidth
          160   // targetHeight
        );
        
        const duration = Date.now() - startTime;
        setLastExtractionTime(duration);
        // URI is unique thanks to timestamp in filename (LRU cache)
        setExtractedFrameUri(result.uri);
        console.log(`📐 Frame dimensions: ${result.width}x${result.height}`);
      } catch (error) {
        setExtractionError(error instanceof Error ? error.message : 'Unknown error');
      } finally {
        setIsExtracting(false);
      }
    }, 100);
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>HLS Frame Extraction</Text>
        <Group name="Frame Extraction (HLS I-Frames)">
          <Text style={{ marginBottom: 10, fontSize: 12, color: '#666' }}>
            Drag the slider to scrub through the video
          </Text>
          
          <View style={{ marginTop: 10 }}>
            <Text style={{ fontSize: 14, fontWeight: 'bold', marginBottom: 5 }}>
              Time: {sliderValue.toFixed(1)}s / {videoDuration}s
            </Text>
            {lastExtractionTime !== null && (
              <Text style={{ fontSize: 12, color: '#4CAF50', marginBottom: 10 }}>
                ⚡ Extracted in {lastExtractionTime}ms
              </Text>
            )}
            <Slider
              style={{ width: '100%', height: 40 }}
              minimumValue={0}
              maximumValue={videoDuration}
              value={sliderValue}
              onValueChange={handleSliderChange}
              minimumTrackTintColor="#2196F3"
              maximumTrackTintColor="#ddd"
              thumbTintColor="#2196F3"
            />
          </View>
          {isExtracting && (
            <View style={{ marginTop: 10, alignItems: 'center' }}>
              <ActivityIndicator />
              <Text style={{ marginTop: 5, fontSize: 12, color: '#666' }}>
                Extracting frame...
              </Text>
            </View>
          )}
          {extractionError && (
            <View style={{ marginTop: 10, padding: 10, backgroundColor: '#ffebee', borderRadius: 5 }}>
              <Text style={{ color: '#c62828', fontSize: 12 }}>Error: {extractionError}</Text>
            </View>
          )}
          {extractedFrameUri && (
            <View style={{ marginTop: 10 }}>
              <Text style={{ fontSize: 12, color: '#666', marginBottom: 5 }}>
                Extracted frame:
              </Text>
              <Image
                source={{ uri: extractedFrameUri }}
                style={{ width: '100%', height: 200, borderRadius: 8, backgroundColor: '#000' }}
                resizeMode="contain"
              />
            </View>
          )}
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

const styles = {
  header: {
    fontSize: 30,
    margin: 20,
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
  },
  group: {
    margin: 20,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: '#eee',
  },
};
