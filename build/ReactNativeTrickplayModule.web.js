import { registerWebModule, NativeModule } from 'expo';
/**
 * Web stub for ReactNativeTrickplay.
 *
 * Note: This module requires native video decoding capabilities and is not
 * supported on web. Use this module only in native iOS/Android environments.
 */
class ReactNativeTrickplayModule extends NativeModule {
    extractFrameAsync(_url, _seconds, _targetWidth, _targetHeight) {
        throw new Error('ReactNativeTrickplay is not supported on web. ' +
            'This module requires native video decoding (iOS AVFoundation / Android ExoPlayer). ' +
            'Please use this module only in native iOS/Android apps.');
    }
}
export default registerWebModule(ReactNativeTrickplayModule, 'ReactNativeTrickplayModule');
//# sourceMappingURL=ReactNativeTrickplayModule.web.js.map