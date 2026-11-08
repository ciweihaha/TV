/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Loads subtitle content (parsed lines for the subtitle-content dialog) for the MPV
 * engine. Implemented by the app (SubtitleFileContent); see
 * {@link androidx.media3.exoplayer.text.DefaultSubtitleContentLoader} for the default
 * implementation used by the Exo engine.
 */
package androidx.media3.mpvplayer;

import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.text.SubtitleContent;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

@UnstableApi
public interface MpvSubtitleContentLoader {

  SubtitleContent load(MediaItem item, Uri uri, Format format) throws IOException;
}
