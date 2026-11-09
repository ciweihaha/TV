/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Wraps a media source to stop playback {@code endTrimDurationMs} before the end (skip
 * endings / credits). Implemented with a {@link ForwardingTimeline} that clips the window
 * and period durations, mirroring stock {@code ClippingMediaSource.ClippingTimeline} (with
 * start 0 and end = duration - trim). The player ends the period when the position reaches
 * the clipped duration, so playback stops at the trim point and advances / ends normally.
 * Live or unknown-duration sources and multi-period timelines are left unclipped.
 */
package androidx.media3.exoplayer.source;

import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

@UnstableApi
public final class EndTrimMediaSource extends WrappingMediaSource {

  private long endTrimDurationMs;
  private Timeline childTimeline;

  public EndTrimMediaSource(MediaSource mediaSource, long endTrimDurationMs) {
    super(mediaSource);
    this.endTrimDurationMs = endTrimDurationMs;
  }

  public void setEndTrimDurationMs(long endTrimDurationMs) {
    if (this.endTrimDurationMs == endTrimDurationMs) {
      return;
    }
    this.endTrimDurationMs = endTrimDurationMs;
    if (childTimeline != null) {
      refreshSourceInfo(clipTimeline(childTimeline));
    }
  }

  public boolean isEndTrimActive() {
    return endTrimDurationMs != C.TIME_UNSET && endTrimDurationMs > 0;
  }

  /** The original (untrimmed) duration, or {@link C#TIME_UNSET} if unknown. */
  public long getOriginalDurationMs() {
    if (childTimeline == null || childTimeline.isEmpty()) {
      return C.TIME_UNSET;
    }
    Timeline.Window window = new Timeline.Window();
    childTimeline.getWindow(0, window);
    return window.getDurationMs();
  }

  @Override
  protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
    childTimeline = newTimeline;
    refreshSourceInfo(clipTimeline(newTimeline));
  }

  private Timeline clipTimeline(Timeline timeline) {
    if (!isEndTrimActive() || timeline.isEmpty() || timeline.getPeriodCount() != 1) {
      return timeline;
    }
    Timeline.Window firstWindow = new Timeline.Window();
    timeline.getWindow(0, firstWindow);
    long durationUs = firstWindow.durationUs;
    if (durationUs == C.TIME_UNSET) {
      return timeline;
    }
    long trimUs = Util.msToUs(endTrimDurationMs);
    if (trimUs <= 0 || durationUs <= trimUs) {
      return timeline;
    }
    final long trimmedDurationUs = durationUs - trimUs;
    return new ForwardingTimeline(timeline) {
      @Override
      public Timeline.Window getWindow(
          int windowIndex, Timeline.Window window, long defaultPositionProjectionUs) {
        super.getWindow(windowIndex, window, defaultPositionProjectionUs);
        if (windowIndex == 0) {
          window.durationUs = trimmedDurationUs;
          if (window.defaultPositionUs != C.TIME_UNSET
              && window.defaultPositionUs > trimmedDurationUs) {
            window.defaultPositionUs = trimmedDurationUs;
          }
        }
        return window;
      }

      @Override
      public Timeline.Period getPeriod(
          int periodIndex, Timeline.Period period, boolean setIds) {
        super.getPeriod(periodIndex, period, setIds);
        if (periodIndex == 0) {
          long periodDurationUs = trimmedDurationUs - period.positionInWindowUs;
          if (periodDurationUs < 0) {
            periodDurationUs = 0;
          }
          period.set(
              period.id,
              period.uid,
              period.windowIndex,
              periodDurationUs,
              period.positionInWindowUs,
              period.adPlaybackState,
              period.isPlaceholder);
        }
        return period;
      }
    };
  }
}
