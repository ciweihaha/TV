/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * SubtitleParser factory backed by a live subtitle transcript. The shim parser emits no
 * cues (no transcript is available), so transcript subtitles stay inactive.
 */
package androidx.media3.exoplayer.text;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;

@UnstableApi
public final class SubtitleTranscriptParserFactory implements SubtitleParser.Factory {

  private final @Nullable SubtitleTranscript transcript;

  public SubtitleTranscriptParserFactory(@Nullable SubtitleTranscript transcript) {
    this.transcript = transcript;
  }

  @Override
  public boolean supportsFormat(Format format) {
    return transcript != null;
  }

  @Override
  public @Format.CueReplacementBehavior int getCueReplacementBehavior(Format format) {
    return Format.CUE_REPLACEMENT_BEHAVIOR_MERGE;
  }

  @Override
  public SubtitleParser create(Format format) {
    return new SubtitleParser() {
      @Override
      public void parse(
          byte[] data,
          int offset,
          int length,
          OutputOptions outputOptions,
          Consumer<CuesWithTiming> output) {
        // No-op: no transcript available.
      }

      @Override
      public @Format.CueReplacementBehavior int getCueReplacementBehavior() {
        return Format.CUE_REPLACEMENT_BEHAVIOR_MERGE;
      }
    };
  }
}
