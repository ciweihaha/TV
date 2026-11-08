/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Track selector for the secondary (dual) subtitle track. The real implementation
 * extends the decoder track selector with a second text-track selection; the shim
 * delegates to the wrapped selector factory, so only the primary subtitle track is
 * selectable.
 */
package androidx.media3.exoplayer.trackselection;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class SecondaryTextTrackSelector {

  private SecondaryTextTrackSelector() {}

  public static final class Factory implements TrackSelector.Factory {

    private final TrackSelector.Factory delegate;

    public Factory(TrackSelector.Factory delegate) {
      this.delegate = delegate;
    }

    @Override
    public TrackSelector createTrackSelector(Context context) {
      return delegate.createTrackSelector(context);
    }
  }
}
