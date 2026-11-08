/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Loads subtitle content (the parsed lines shown by the subtitle-content dialog) from a
 * URI. The real implementation downloads the file and parses it; the shim reports the
 * track as unsupported, so the dialog shows "unsupported" while normal subtitle
 * rendering (through the player's own subtitle track) keeps working.
 */
package androidx.media3.exoplayer.text;

import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.text.SubtitleContent;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.mpvplayer.MpvSubtitleContentLoader;
import java.io.IOException;

@UnstableApi
public final class DefaultSubtitleContentLoader implements MpvSubtitleContentLoader {

  private final DataSource.Factory dataSourceFactory;

  public DefaultSubtitleContentLoader(DataSource.Factory dataSourceFactory) {
    this.dataSourceFactory = dataSourceFactory;
  }

  @Override
  public SubtitleContent load(MediaItem item, Uri uri, Format format) throws IOException {
    return SubtitleContent.UNSUPPORTED;
  }
}
