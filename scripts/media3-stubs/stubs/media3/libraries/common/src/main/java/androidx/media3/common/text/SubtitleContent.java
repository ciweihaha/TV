/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * The parsed content of a subtitle track, as shown by the subtitle-content dialog (list of
 * subtitle lines with timings). The real implementation downloads and parses external
 * subtitle files; the shim only carries the already-parsed state.
 */
package androidx.media3.common.text;

import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public final class SubtitleContent {

  public enum State {
    LOADING,
    COMPLETE,
    ERROR,
    UNSUPPORTED,
    PARTIAL,
    TRUNCATED
  }

  /** One subtitle line. */
  public static final class Entry {
    public final long startTimeUs;
    public final long endTimeUs;
    public final String text;
    public final boolean image;

    public Entry(long startTimeUs, long endTimeUs, String text, boolean image) {
      this.startTimeUs = startTimeUs;
      this.endTimeUs = endTimeUs;
      this.text = text == null ? "" : text;
      this.image = image;
    }
  }

  public static final SubtitleContent UNSUPPORTED =
      new SubtitleContent(State.UNSUPPORTED, List.of());
  public static final SubtitleContent LOADING = new SubtitleContent(State.LOADING, List.of());

  public final State state;
  public final List<Entry> entries;

  public SubtitleContent(State state, List<Entry> entries) {
    this.state = state;
    this.entries =
        entries == null
            ? List.of()
            : Collections.unmodifiableList(new ArrayList<>(entries));
  }
}
