/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * ExoPlayer-side decoder mode; converts 1:1 to/from the public
 * {@link androidx.media3.common.DecoderMode}.
 */
package androidx.media3.exoplayer;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public enum DecoderMode {
  AUTO,
  HARDWARE,
  SOFTWARE,
  FFMPEG;

  public static DecoderMode fromCommonDecoderMode(androidx.media3.common.DecoderMode mode) {
    return mode == null ? null : DecoderMode.valueOf(mode.name());
  }

  public androidx.media3.common.DecoderMode toCommonDecoderMode() {
    return androidx.media3.common.DecoderMode.valueOf(name());
  }
}
