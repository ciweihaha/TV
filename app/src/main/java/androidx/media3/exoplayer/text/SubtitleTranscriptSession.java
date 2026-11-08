/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Owns the live subtitle transcript of the current media item. The real implementation
 * transcribes / fetches subtitle text and serves it to the subtitle-content dialog and
 * the transcript parser; the shim reports "no transcript", so those features stay
 * inactive while normal subtitle tracks keep working.
 */
package androidx.media3.exoplayer.text;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.text.SubtitleContent;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.mpvplayer.MpvSubtitleContentLoader;

@UnstableApi
public final class SubtitleTranscriptSession {

  private @Nullable MpvSubtitleContentLoader loader;
  private @Nullable ExoPlayer player;
  private @Nullable MediaItem preloadMediaItem;
  private boolean contentEnabled = true;

  public SubtitleTranscriptSession() {}

  public void setLoader(MpvSubtitleContentLoader loader) {
    this.loader = loader;
  }

  public void setPlayer(ExoPlayer player) {
    this.player = player;
  }

  public void setPreloadMediaItem(@Nullable MediaItem mediaItem) {
    this.preloadMediaItem = mediaItem;
  }

  /** Returns the transcript for a media item, or null when there is none. */
  public @Nullable SubtitleTranscript forMediaItem(MediaItem mediaItem) {
    return null;
  }

  /** Returns the subtitle content for a track, or UNSUPPORTED when unavailable. */
  public SubtitleContent getContent(Format format, long offsetMs) {
    return SubtitleContent.UNSUPPORTED;
  }

  public void setContentEnabled(boolean enabled) {
    this.contentEnabled = enabled;
  }

  public boolean canRetryContent(Format format) {
    return false;
  }

  public void retryContent(Format format) {}

  public void close() {}
}
