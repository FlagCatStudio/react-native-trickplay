import { registerWebModule, NativeModule } from 'expo';

import { ReactNativeTrickplayModuleEvents, ExtractFrameResult } from './ReactNativeTrickplay.types';

/**
 * Web stub for ReactNativeTrickplay.
 * 
 * Note: This module requires native video decoding capabilities and is not
 * supported on web. Use this module only in native iOS/Android environments.
 */
class ReactNativeTrickplayModule extends NativeModule<ReactNativeTrickplayModuleEvents> {
  extractFrameAsync(
    _url: string,
    _seconds: number,
    _targetWidth?: number,
    _targetHeight?: number
  ): Promise<ExtractFrameResult> {
    throw new Error(
      'ReactNativeTrickplay is not supported on web. ' +
      'This module requires native video decoding (iOS AVFoundation / Android ExoPlayer). ' +
      'Please use this module only in native iOS/Android apps.'
    );
  }
}

export default registerWebModule(ReactNativeTrickplayModule, 'ReactNativeTrickplayModule');
