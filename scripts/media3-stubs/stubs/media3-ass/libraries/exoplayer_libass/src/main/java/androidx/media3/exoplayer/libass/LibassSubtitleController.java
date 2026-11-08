/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Bridges the ExoPlayer, the libass playback session and the secondary subtitle track
 * selector. The real implementation routes subtitle selection state between the player
 * and libass; the shim reports "no secondary track" so the UI falls back to
 * single-subtitle behaviour.
 */
package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.text.SubtitleSelectionState;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.text.SecondaryTextOutput;
import androidx.media3.exoplayer.trackselection.SecondaryTextTrackSelector;

@UnstableApi
public final class LibassSubtitleController {

  private final ExoPlayer player;
  private final LibassPlaybackSession session;
  private final SecondaryTextTrackSelector.Factory trackSelectorFactory;
  private final SecondaryTextOutput secondaryTextOutput;

  public LibassSubtitleController(
      ExoPlayer player,
      LibassPlaybackSession session,
      SecondaryTextTrackSelector.Factory trackSelectorFactory,
      SecondaryTextOutput secondaryTextOutput) {
    this.player = player;
    this.session = session;
    this.trackSelectorFactory = trackSelectorFactory;
    this.secondaryTextOutput = secondaryTextOutput;
  }

  public SubtitleSelectionState getSubtitleSelectionState() {
    return SubtitleSelectionState.EMPTY;
  }

  public void setSecondaryTextTrackSelectionOverride(
      @Nullable TrackSelectionOverride selection) {}

  public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {}

  public void close() {}
}
