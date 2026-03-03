import { NativeModule } from 'expo';
import { ReactNativeTrickplayModuleEvents, ExtractFrameResult } from './ReactNativeTrickplay.types';
/**
 * Web stub for ReactNativeTrickplay.
 *
 * Note: This module requires native video decoding capabilities and is not
 * supported on web. Use this module only in native iOS/Android environments.
 */
declare class ReactNativeTrickplayModule extends NativeModule<ReactNativeTrickplayModuleEvents> {
    extractFrameAsync(_url: string, _seconds: number, _targetWidth?: number, _targetHeight?: number): Promise<ExtractFrameResult>;
}
declare const _default: typeof ReactNativeTrickplayModule;
export default _default;
//# sourceMappingURL=ReactNativeTrickplayModule.web.d.ts.map