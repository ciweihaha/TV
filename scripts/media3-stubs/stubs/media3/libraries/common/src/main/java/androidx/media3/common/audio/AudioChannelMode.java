/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Channel mode selection for the audio effect chain. Modes are plain int constants so they
 * can be persisted in preferences.
 */
package androidx.media3.common.audio;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class AudioChannelMode {

  public static final int AUTO = 0;
  public static final int STEREO = 1;
  public static final int MONO = 2;
  public static final int REVERSE = 3;

  private AudioChannelMode() {}

  /** Resolves AUTO to a concrete mode for the given channel count. */
  public static int resolve(int mode, int channelCount) {
    if (mode != AUTO) return clamp(mode);
    return channelCount <= 1 ? MONO : STEREO;
  }

  /** Whether channel mixing / balance applies for this mode and channel count. */
  public static boolean isAvailable(int mode, int channelCount) {
    return channelCount >= 2 && mode != MONO;
  }

  /** Whether channel mixing applies for this channel count. */
  public static boolean isAvailable(int channelCount) {
    return channelCount >= 2;
  }

  /** Whether the mode supports left/right balance. */
  public static boolean canBalance(int mode) {
    return mode == STEREO || mode == REVERSE;
  }

  public static boolean isAuto(int mode) {
    return mode == AUTO;
  }

  public static int clamp(int mode) {
    return Math.max(AUTO, Math.min(REVERSE, mode));
  }
}
