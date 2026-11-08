/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Wraps a (preloaded) media source so playback starts at {@code startPositionMs} when
 * the source is added to the player. The shim plays from the start instead; the
 * preloaded item still plays, just without the saved resume position.
 */
package androidx.media3.exoplayer.source;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class DefaultPositionMediaSource extends WrappingMediaSource {

  private final long startPositionMs;

  public DefaultPositionMediaSource(MediaSource mediaSource, long startPositionMs) {
    super(mediaSource);
    this.startPositionMs = startPositionMs;
  }

  public long getStartPositionMs() {
    return startPositionMs;
  }
}
