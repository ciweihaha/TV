/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Wraps a media source to stop playback {@code endTrimDurationMs} before the end (skip
 * endings / credits). The shim reports the trim as configured but does not cut playback
 * short; the media plays to its full length.
 */
package androidx.media3.exoplayer.source;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class EndTrimMediaSource extends WrappingMediaSource {

  private long endTrimDurationMs;

  public EndTrimMediaSource(MediaSource mediaSource, long endTrimDurationMs) {
    super(mediaSource);
    this.endTrimDurationMs = endTrimDurationMs;
  }

  public void setEndTrimDurationMs(long endTrimDurationMs) {
    this.endTrimDurationMs = endTrimDurationMs;
  }

  public boolean isEndTrimActive() {
    return endTrimDurationMs != C.TIME_UNSET && endTrimDurationMs > 0;
  }

  /** The original (untrimmed) duration; unknown in the shim. */
  public long getOriginalDurationMs() {
    return C.TIME_UNSET;
  }
}
