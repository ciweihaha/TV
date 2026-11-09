/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Owns the live subtitle transcript of the current media item. Round 115: the
 * subtitle-content dialog path is a real implementation — {@link #getContent} resolves
 * the subtitle file for the selected track from the current media item's subtitle
 * configurations, loads and parses it on a background thread through the loader
 * (app-side {@code SubtitleFileContent} -> {@link DefaultSubtitleContentLoader}), and
 * caches the result per track so the dialog's 500 ms poll sees a stable instance.
 * Loading returns {@link SubtitleContent#LOADING} until the background load finishes;
 * embedded tracks (no external file) and unparsable formats report UNSUPPORTED;
 * download/parse failures report ERROR and can be retried.
 *
 * Still a shim: {@link #forMediaItem} returns null, so the transcript-as-playable-track
 * path (SubtitleTranscriptParserFactory / LibassPlaybackSession transcript argument)
 * stays inactive — the real transcript design lives in FongMi's private modules and
 * cannot be reconstructed from the public checkout.
 *
 * Round 121: {@code offsetMs} (the subtitle delay) is applied to the dialog entries —
 * {@link #getContent} shifts every entry by the offset so the current-line highlight
 * matches the delayed on-screen subtitles, and the cache remembers which offset each
 * entry set was built with. The MPV engine's getSubtitleContentForSelection (round 114)
 * still returns file times; shifting both engines consistently needs a real-device check
 * of the highlight logic.
 */
package androidx.media3.exoplayer.text;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.text.SubtitleContent;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.mpvplayer.MpvSubtitleContentLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@UnstableApi
public final class SubtitleTranscriptSession {

  private static final String TAG = SubtitleTranscriptSession.class.getSimpleName();

  private @Nullable MpvSubtitleContentLoader loader;
  private @Nullable ExoPlayer player;
  private @Nullable MediaItem preloadMediaItem;
  private boolean contentEnabled = true;

  /** Loaded content per track key (COMPLETE / ERROR / UNSUPPORTED); same instance returned. */
  private final Map<String, SubtitleContent> cache = new HashMap<>();
  /** Keys with a background load in flight. */
  private final Set<String> loading = new HashSet<>();
  /** Media id the cache belongs to; the cache is dropped when the item changes. */
  private @Nullable String cachedMediaId;
  /** Subtitle delay (ms) the cached content for a key was shifted by (round 121). */
  private final Map<String, Long> offsetByKey = new HashMap<>();

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

  /**
   * Returns the subtitle content for a track, loading it on a background thread on first
   * request (LOADING until then). Called from the subtitle-content dialog's poll loop.
   */
  public SubtitleContent getContent(Format format, long offsetMs) {
    if (!contentEnabled || loader == null || format == null) {
      return SubtitleContent.UNSUPPORTED;
    }
    MediaItem item = currentMediaItem();
    if (item == null) {
      return SubtitleContent.UNSUPPORTED;
    }
    Uri uri = resolveSubtitleUri(item, format);
    if (uri == null) {
      return SubtitleContent.UNSUPPORTED;
    }
    String key = keyFor(uri, format);
    synchronized (cache) {
      if (!item.mediaId.equals(cachedMediaId)) {
        cache.clear();
        loading.clear();
        offsetByKey.clear();
        cachedMediaId = item.mediaId;
      }
      SubtitleContent cached = cache.get(key);
      Long cachedOffset = offsetByKey.get(key);
      if (cached != null && cachedOffset != null && cachedOffset.longValue() == offsetMs) {
        return cached;
      }
      if (cached != null) {
        // Built with a different subtitle delay: drop and reload shifted.
        cache.remove(key);
        offsetByKey.remove(key);
      }
      if (loading.contains(key)) {
        return SubtitleContent.LOADING;
      }
      loading.add(key);
    }
    Thread thread =
        new Thread(
            () -> loadInBackground(item, uri, format, key, offsetMs),
            "subtitle-content-loader");
    thread.setDaemon(true);
    thread.start();
    return SubtitleContent.LOADING;
  }

  public void setContentEnabled(boolean enabled) {
    this.contentEnabled = enabled;
  }

  public boolean canRetryContent(Format format) {
    if (format == null) {
      return false;
    }
    String key = keyForCurrentItem(format);
    if (key == null) {
      return false;
    }
    synchronized (cache) {
      SubtitleContent cached = cache.get(key);
      return cached != null && cached.state == SubtitleContent.State.ERROR;
    }
  }

  public void retryContent(Format format) {
    if (format == null) {
      return;
    }
    String key = keyForCurrentItem(format);
    if (key == null) {
      return;
    }
    synchronized (cache) {
      // A load in flight is left alone; it will populate the cache when it finishes.
      cache.remove(key);
      offsetByKey.remove(key);
    }
  }

  public void close() {
    synchronized (cache) {
      cache.clear();
      loading.clear();
      offsetByKey.clear();
      cachedMediaId = null;
    }
  }

  private void loadInBackground(
      MediaItem item, Uri uri, Format format, String key, long offsetMs) {
    SubtitleContent result;
    try {
      result = loader.load(item, uri, format);
      if (result == null) {
        result = SubtitleContent.UNSUPPORTED;
      }
    } catch (IOException | RuntimeException e) {
      Log.w(TAG, "Failed to load subtitle content: " + uri, e);
      result = new SubtitleContent(SubtitleContent.State.ERROR, List.of());
    }
    if (offsetMs != 0 && !result.entries.isEmpty()) {
      result = shiftEntries(result, offsetMs);
    }
    synchronized (cache) {
      loading.remove(key);
      cache.put(key, result);
      offsetByKey.put(key, offsetMs);
    }
  }

  /**
   * Shifts every entry of a subtitle content by the subtitle delay (round 121), so the
   * dialog's current-line highlight matches the delayed on-screen subtitles. Times are
   * clamped at zero; the state is unchanged.
   */
  private static SubtitleContent shiftEntries(SubtitleContent content, long offsetMs) {
    long shiftUs = offsetMs * 1000;
    List<SubtitleContent.Entry> shifted = new ArrayList<>(content.entries.size());
    for (SubtitleContent.Entry entry : content.entries) {
      shifted.add(
          new SubtitleContent.Entry(
              Math.max(0, entry.startTimeUs + shiftUs),
              Math.max(0, entry.endTimeUs + shiftUs),
              entry.text,
              entry.image));
    }
    return new SubtitleContent(content.state, shifted);
  }

  private @Nullable MediaItem currentMediaItem() {
    MediaItem item = player == null ? null : player.getCurrentMediaItem();
    return item != null ? item : preloadMediaItem;
  }

  private @Nullable String keyForCurrentItem(Format format) {
    MediaItem item = currentMediaItem();
    if (item == null) {
      return null;
    }
    Uri uri = resolveSubtitleUri(item, format);
    return uri == null ? null : keyFor(uri, format);
  }

  private static String keyFor(Uri uri, Format format) {
    String track =
        format.id != null
            ? format.id
            : format.language + "|" + format.label + "|" + format.sampleMimeType;
    return uri.toString() + "\n" + track;
  }

  /**
   * Resolves the subtitle file URI for a selected track from the media item's subtitle
   * configurations: exact id match first (external subtitles carry the configuration id
   * as the track Format id), then language + label, then the single configuration when
   * there is only one. Embedded tracks have no configuration and return null.
   */
  private static @Nullable Uri resolveSubtitleUri(MediaItem item, Format format) {
    if (item.localConfiguration == null) {
      return null;
    }
    List<MediaItem.SubtitleConfiguration> configs =
        item.localConfiguration.subtitleConfigurations;
    for (MediaItem.SubtitleConfiguration config : configs) {
      if (config.id != null && config.id.equals(format.id)) {
        return config.uri;
      }
    }
    for (MediaItem.SubtitleConfiguration config : configs) {
      if (equalsNonNull(config.language, format.language)
          && equalsNonNull(config.label, format.label)) {
        return config.uri;
      }
    }
    if (configs.size() == 1) {
      return configs.get(0).uri;
    }
    return null;
  }

  private static boolean equalsNonNull(@Nullable String a, @Nullable String b) {
    return a != null && a.equals(b);
  }
}
