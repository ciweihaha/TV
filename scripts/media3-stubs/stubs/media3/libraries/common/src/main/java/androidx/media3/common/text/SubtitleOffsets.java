/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 */
package androidx.media3.common.text;

import androidx.media3.common.util.UnstableApi;
import java.util.Objects;

/** Primary/secondary subtitle time offsets in milliseconds. */
@UnstableApi
public final class SubtitleOffsets {

  public static final SubtitleOffsets ZERO = new SubtitleOffsets(0, 0);

  public final long primaryMs;
  public final long secondaryMs;

  public SubtitleOffsets(long primaryMs, long secondaryMs) {
    this.primaryMs = primaryMs;
    this.secondaryMs = secondaryMs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SubtitleOffsets other)) return false;
    return primaryMs == other.primaryMs && secondaryMs == other.secondaryMs;
  }

  @Override
  public int hashCode() {
    return Objects.hash(primaryMs, secondaryMs);
  }

  @Override
  public String toString() {
    return "SubtitleOffsets{primaryMs=" + primaryMs + ", secondaryMs=" + secondaryMs + "}";
  }
}
