/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Drives the platform audio equalizer for Exo playback. Round 116: real implementation
 * — attaches Android's {@link android.media.audiofx.Equalizer} to the player's audio
 * session and maps the configured bands onto the platform bands by nearest center
 * frequency (config frequencies are Hz, platform center frequencies are milliHz;
 * levels are millibels on both sides, clamped to the platform band-level range).
 * The equalizer is recreated when the audio session changes (track switch / release
 * re-triggers apply from onAudioTrackInitialized / onAudioTrackReleased).
 *
 * Compiled inside the Media3 checkout with -source 8: Java 8 syntax only.
 */
package androidx.media3.exoplayer.audio;

import android.content.Context;
import android.media.audiofx.Equalizer;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioEffectParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

@UnstableApi
public final class AudioEqualizerController {

  private @Nullable Equalizer equalizer;
  private int audioSessionId = C.AUDIO_SESSION_ID_UNSET;

  public AudioEqualizerController(Context context) {}

  /**
   * Applies the equalizer configuration to the player's audio session. Returns false
   * when the platform equalizer cannot be created (the caller surfaces this as an
   * audio-effect failure); returns true when there is no audio session yet, so the
   * next apply (track initialized / setting changed) can attach then instead of
   * permanently marking effects failed.
   */
  public boolean apply(ExoPlayer player, AudioEffectParameters config) {
    int sessionId = player.getAudioSessionId();
    if (sessionId == C.AUDIO_SESSION_ID_UNSET) {
      return true;
    }
    short[] levels = config.getEqualizerLevelsMillibels();
    int[] frequencies = config.getEqualizerFrequencies();
    int bandConfigCount = Math.min(levels.length, frequencies.length);
    if (bandConfigCount == 0) {
      release();
      return true;
    }
    try {
      if (equalizer == null || audioSessionId != sessionId) {
        release();
        equalizer = new Equalizer(0, sessionId);
        audioSessionId = sessionId;
      }
      short bandCount = equalizer.getNumberOfBands();
      short[] range = equalizer.getBandLevelRange();
      short minLevel = range[0];
      short maxLevel = range[1];
      for (short band = 0; band < bandCount; band++) {
        int centerFreqMilliHz = equalizer.getCenterFreq(band);
        int nearest = nearestBandIndex(frequencies, bandConfigCount, centerFreqMilliHz);
        short level = clamp(levels[nearest], minLevel, maxLevel);
        equalizer.setBandLevel(band, level);
      }
      equalizer.setEnabled(true);
      return true;
    } catch (RuntimeException e) {
      release();
      return false;
    }
  }

  public void release() {
    if (equalizer != null) {
      try {
        equalizer.setEnabled(false);
      } catch (RuntimeException ignored) {
        // Already released or never enabled; release() below still runs.
      }
      equalizer.release();
      equalizer = null;
    }
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
  }

  /**
   * Index of the configured band whose center frequency is nearest to the platform
   * band's center frequency (milliHz). Config frequencies are Hz.
   */
  private static int nearestBandIndex(int[] frequencies, int count, int centerFreqMilliHz) {
    int nearest = 0;
    int nearestDistance = Math.abs(frequencies[0] * 1000 - centerFreqMilliHz);
    for (int i = 1; i < count; i++) {
      int distance = Math.abs(frequencies[i] * 1000 - centerFreqMilliHz);
      if (distance < nearestDistance) {
        nearest = i;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  private static short clamp(short value, short min, short max) {
    return value < min ? min : (value > max ? max : value);
  }
}
