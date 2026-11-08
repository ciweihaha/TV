/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Which subtitle tracks are selected as primary / secondary (dual subtitles). The real
 * implementation is driven by the libass subtitle controller; the shim reports "no
 * secondary track" so the UI falls back to single-subtitle behaviour.
 */
package androidx.media3.common.text;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@UnstableApi
public final class SubtitleSelectionState {

  public static final SubtitleSelectionState EMPTY = new SubtitleSelectionState();

  public final @Nullable TrackSelectionOverride primarySelection;
  public final @Nullable TrackSelectionOverride activeSecondarySelection;
  public final List<TrackSelectionOverride> secondaryCandidates;
  public final @Nullable TrackSelectionOverride requestedSecondarySelection;
  public final boolean secondaryPromotedToPrimary;

  public SubtitleSelectionState() {
    this(null, null, List.of(), null, false);
  }

  public SubtitleSelectionState(
      @Nullable TrackSelectionOverride primarySelection,
      @Nullable TrackSelectionOverride activeSecondarySelection,
      List<TrackSelectionOverride> secondaryCandidates,
      @Nullable TrackSelectionOverride requestedSecondarySelection,
      boolean secondaryPromotedToPrimary) {
    this.primarySelection = primarySelection;
    this.activeSecondarySelection = activeSecondarySelection;
    this.secondaryCandidates =
        secondaryCandidates == null
            ? List.of()
            : Collections.unmodifiableList(new ArrayList<>(secondaryCandidates));
    this.requestedSecondarySelection = requestedSecondarySelection;
    this.secondaryPromotedToPrimary = secondaryPromotedToPrimary;
  }

  public boolean isPrimary(TrackSelectionOverride selection) {
    return Objects.equals(selection, primarySelection);
  }

  public boolean isActiveSecondary(TrackSelectionOverride selection) {
    return Objects.equals(selection, activeSecondarySelection);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SubtitleSelectionState other)) return false;
    return Objects.equals(primarySelection, other.primarySelection)
        && Objects.equals(activeSecondarySelection, other.activeSecondarySelection)
        && Objects.equals(secondaryCandidates, other.secondaryCandidates)
        && Objects.equals(requestedSecondarySelection, other.requestedSecondarySelection)
        && secondaryPromotedToPrimary == other.secondaryPromotedToPrimary;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        primarySelection,
        activeSecondarySelection,
        secondaryCandidates,
        requestedSecondarySelection,
        secondaryPromotedToPrimary);
  }
}
