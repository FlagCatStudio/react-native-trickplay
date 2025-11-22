import { NativeModule, requireNativeModule } from 'expo';

import { ReactNativeTrickplayModuleEvents, ExtractFrameResult } from './ReactNativeTrickplay.types';

declare class ReactNativeTrickplayModule extends NativeModule<ReactNativeTrickplayModuleEvents> {
  /**
   * Extract a frame from an HLS I-FRAMES-ONLY playlist at a specific timestamp
   * @param url URL to the HLS I-Frames playlist (.m3u8) or master manifest
   * @param seconds Timestamp in seconds where to extract the frame
   * @param targetWidth Optional target width (preserves aspect ratio if only one dimension provided)
   * @param targetHeight Optional target height (preserves aspect ratio if only one dimension provided)
   * @returns Promise resolving to local file URI and frame metadata
   */
  extractFrameAsync(
    url: string,
    seconds: number,
    targetWidth?: number,
    targetHeight?: number
  ): Promise<ExtractFrameResult>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ReactNativeTrickplayModule>('ReactNativeTrickplay');
