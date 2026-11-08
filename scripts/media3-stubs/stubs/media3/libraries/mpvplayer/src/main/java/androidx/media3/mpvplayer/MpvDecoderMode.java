/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * MPV-side decoder mode; converts 1:1 to/from the public
 * {@link androidx.media3.common.DecoderMode}.
 */
package androidx.media3.mpvplayer;

import androidx.media3.common.DecoderMode;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public enum MpvDecoderMode {
  AUTO,
  HARDWARE,
  SOFTWARE,
  FFMPEG;

  public static MpvDecoderMode fromCommonDecoderMode(DecoderMode mode) {
    return mode == null ? null : MpvDecoderMode.valueOf(mode.name());
  }

  public DecoderMode toCommonDecoderMode() {
    return DecoderMode.valueOf(name());
  }
}
