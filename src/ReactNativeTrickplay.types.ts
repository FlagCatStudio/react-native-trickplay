export type ReactNativeTrickplayModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
};

export type ChangeEventPayload = {
  value: string;
};

export type ExtractFrameResult = {
  /** Local file URI to the extracted frame (e.g., file:///path/to/frame.jpg) */
  uri: string;
  /** Width of the extracted frame in pixels */
  width: number;
  /** Height of the extracted frame in pixels */
  height: number;
  /** Actual timestamp of the extracted frame (may differ from requested due to keyframe alignment) */
  actualTime: number;
};
