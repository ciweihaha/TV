/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 */
package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;
import java.util.Objects;

/** Snapshot of the ISO/BD disc-menu state of the current media item. */
@UnstableApi
public final class DiscMenuState {

  public static final int AVAILABILITY_UNAVAILABLE = 0;
  public static final int AVAILABILITY_AVAILABLE = 1;

  public static final int PHASE_IDLE = 0;
  public static final int PHASE_OPENING = 1;
  public static final int PHASE_ACTIVE = 2;

  public static final DiscMenuState EMPTY =
      new DiscMenuState(AVAILABILITY_UNAVAILABLE, PHASE_IDLE, false);

  public final int availability;
  public final int phase;
  public final boolean navigationPlaybackOngoing;

  public DiscMenuState() {
    this(AVAILABILITY_UNAVAILABLE, PHASE_IDLE, false);
  }

  public DiscMenuState(int availability, int phase, boolean navigationPlaybackOngoing) {
    this.availability = availability;
    this.phase = phase;
    this.navigationPlaybackOngoing = navigationPlaybackOngoing;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DiscMenuState other)) return false;
    return availability == other.availability
        && phase == other.phase
        && navigationPlaybackOngoing == other.navigationPlaybackOngoing;
  }

  @Override
  public int hashCode() {
    return Objects.hash(availability, phase, navigationPlaybackOngoing);
  }
}
