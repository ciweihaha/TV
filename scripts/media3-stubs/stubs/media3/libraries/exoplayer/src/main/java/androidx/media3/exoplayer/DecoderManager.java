/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Owns the audio/video decoder mode selection for one ExoPlayer session. The real
 * implementation rebuilds renderers when the mode changes; the shim remembers the
 * selection (the UI keeps working) while playback keeps using the default decoders.
 */
package androidx.media3.exoplayer;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.trackselection.DecodeTrackSelector;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class DecoderManager {

  private DecoderMode audioMode;
  private DecoderMode videoMode;
  private @Nullable ExoPlayer player;
  private final List<DecodeTrackSelector> trackSelectors = new ArrayList<>();

  public DecoderManager(DecoderMode audioMode, DecoderMode videoMode) {
    this.audioMode = audioMode;
    this.videoMode = videoMode;
  }

  public DecoderMode getAudioMode() {
    return audioMode;
  }

  public DecoderMode getVideoMode() {
    return videoMode;
  }

  public void selectAudioDecoder(DecoderMode mode) {
    if (mode != null) audioMode = mode;
  }

  public void selectVideoDecoder(DecoderMode mode) {
    if (mode != null) videoMode = mode;
  }

  public void attach(ExoPlayer player) {
    this.player = player;
  }

  public void detach() {
    this.player = null;
  }

  public DecodeTrackSelector createTrackSelector(Context context) {
    return new DecodeTrackSelector(context);
  }

  /**
   * Registers a track selector created for a session (round 139 / v5.9.16: upstream
   * 9713711 registers its per-episode selectors here).
   *
   * <p>FongMi extension (shim): remembered only — the real implementation wires
   * decoder-mode changes into the registered selectors, which the shim does not
   * do (it never rebuilds renderers; see the class header).
   */
  public void registerTrackSelector(DecodeTrackSelector selector) {
    if (selector != null && !trackSelectors.contains(selector)) trackSelectors.add(selector);
  }
}
