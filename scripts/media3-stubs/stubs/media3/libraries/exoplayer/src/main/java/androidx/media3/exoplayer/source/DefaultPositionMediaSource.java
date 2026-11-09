/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Wraps a (preloaded) media source so playback starts at {@code startPositionMs} when
 * the source becomes current. Implemented by overriding the window's default position in
 * a {@link ForwardingTimeline}; the full seekable range is kept, so seeking back to 0
 * still works. A start position at or beyond the (known) duration leaves the timeline
 * unchanged.
 */
package androidx.media3.exoplayer.source;

import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

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

  @Override
  protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
    refreshSourceInfo(positionTimeline(newTimeline));
  }

  private Timeline positionTimeline(Timeline timeline) {
    if (startPositionMs == C.TIME_UNSET || startPositionMs <= 0 || timeline.isEmpty()) {
      return timeline;
    }
    Timeline.Window window = new Timeline.Window();
    timeline.getWindow(0, window);
    long durationUs = window.durationUs;
    final long startPositionUs = Util.msToUs(startPositionMs);
    if (durationUs != C.TIME_UNSET && startPositionUs >= durationUs) {
      return timeline;
    }
    return new ForwardingTimeline(timeline) {
      @Override
      public Timeline.Window getWindow(
          int windowIndex, Timeline.Window window, long defaultPositionProjectionUs) {
        super.getWindow(windowIndex, window, defaultPositionProjectionUs);
        if (windowIndex == 0) {
          window.defaultPositionUs = startPositionUs;
        }
        return window;
      }
    };
  }
}
