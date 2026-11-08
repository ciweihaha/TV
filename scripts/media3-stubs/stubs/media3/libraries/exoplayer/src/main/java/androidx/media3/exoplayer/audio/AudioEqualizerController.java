/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Drives the platform audio equalizer for Exo playback. The real implementation applies
 * the configured equalizer bands through Android's Equalizer audio effect; the shim
 * reports success without touching the audio path.
 */
package androidx.media3.exoplayer.audio;

import android.content.Context;
import androidx.media3.common.audio.AudioEffectParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

@UnstableApi
public final class AudioEqualizerController {

  public AudioEqualizerController(Context context) {}

  /** Applies the equalizer configuration; always succeeds (no-op in the shim). */
  public boolean apply(ExoPlayer player, AudioEffectParameters config) {
    return true;
  }

  public void release() {}
}
