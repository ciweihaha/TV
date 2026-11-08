/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Track-selection helpers used by the mpv-style command interface (audio track cycling /
 * selection). Implemented on top of the stock Player / Tracks APIs.
 */
package androidx.media3.common.util;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class TrackSelectionUtil {

  private TrackSelectionUtil() {}

  /** Cycles to the next (or previous) track of the given type. */
  public static boolean cycleTrack(
      Player player, @C.TrackType int trackType, boolean forward, boolean includeDisabled) {
    List<Integer> candidates = candidateIndices(player, trackType, null);
    if (candidates.isEmpty()) return false;
    return selectCycled(player, trackType, candidates, forward);
  }

  /** Cycles among the tracks whose ids are listed in {@code trackIds}. */
  public static boolean cycleTrack(
      Player player,
      @C.TrackType int trackType,
      List<String> trackIds,
      boolean forward,
      boolean includeDisabled) {
    List<Integer> candidates = candidateIndices(player, trackType, trackIds);
    if (candidates.isEmpty()) return false;
    return selectCycled(player, trackType, candidates, forward);
  }

  /** Disables (or re-enables) all tracks of the given type. */
  public static boolean setTrackTypeDisabled(
      Player player, @C.TrackType int trackType, boolean disabled) {
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setTrackTypeDisabled(trackType, disabled)
            .build());
    return true;
  }

  /** Resets the track selection of the given type back to automatic. */
  public static boolean resetTrackSelection(Player player, @C.TrackType int trackType) {
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, false)
            .build());
    return true;
  }

  /** Selects the track of the given type whose id, index or language matches {@code trackId}. */
  public static boolean selectTrack(Player player, @C.TrackType int trackType, String trackId) {
    Tracks.Group group = firstGroup(player, trackType);
    if (group == null) return false;
    for (int i = 0; i < group.length; i++) {
      if (matches(trackId, group.getTrackFormat(i), i)) {
        applyOverride(player, group, i);
        return true;
      }
    }
    return false;
  }

  private static boolean selectCycled(
      Player player, @C.TrackType int trackType, List<Integer> candidates, boolean forward) {
    Tracks.Group group = firstGroup(player, trackType);
    if (group == null) return false;
    int current = -1;
    for (int i = 0; i < group.length; i++) {
      if (group.isTrackSelected(i)) current = i;
    }
    int pos = candidates.indexOf(current);
    int next = candidates.get(pos < 0 ? (forward ? 0 : candidates.size() - 1)
        : (pos + (forward ? 1 : -1) + candidates.size()) % candidates.size());
    applyOverride(player, group, next);
    return true;
  }

  private static List<Integer> candidateIndices(
      Player player, @C.TrackType int trackType, List<String> trackIds) {
    List<Integer> candidates = new ArrayList<>();
    Tracks.Group group = firstGroup(player, trackType);
    if (group == null) return candidates;
    for (int i = 0; i < group.length; i++) {
      if (trackIds == null || matchesAny(trackIds, group.getTrackFormat(i), i)) {
        candidates.add(i);
      }
    }
    return candidates;
  }

  private static boolean matchesAny(List<String> trackIds, Format format, int index) {
    for (String trackId : trackIds) {
      if (matches(trackId, format, index)) return true;
    }
    return false;
  }

  private static boolean matches(String trackId, Format format, int index) {
    if (trackId == null) return false;
    return trackId.equals(format.id)
        || trackId.equals(String.valueOf(index))
        || trackId.equals(format.language);
  }

  private static Tracks.Group firstGroup(Player player, @C.TrackType int trackType) {
    for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
      if (group.getType() == trackType && group.length > 0) return group;
    }
    return null;
  }

  private static void applyOverride(Player player, Tracks.Group group, int index) {
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setOverrideForType(
                new TrackSelectionOverride(group.getMediaTrackGroup(), index))
            .build());
  }
}
