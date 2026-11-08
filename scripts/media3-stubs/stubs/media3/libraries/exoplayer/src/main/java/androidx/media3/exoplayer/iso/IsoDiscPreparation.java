/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Detects ISO disc images. The real implementation inspects the media source; the shim
 * checks the URI (".iso" suffix or "iso" scheme), which covers the app's ISO playback
 * paths (ISO files play as plain files, without disc navigation).
 */
package androidx.media3.exoplayer.iso;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class IsoDiscPreparation {

  private IsoDiscPreparation() {}

  public static boolean isIso(MediaItem item) {
    if (item == null || item.localConfiguration == null) return false;
    Uri uri = item.localConfiguration.uri;
    if (uri == null) return false;
    if ("iso".equalsIgnoreCase(uri.getScheme())) return true;
    String path = uri.getPath();
    return path != null && path.toLowerCase().endsWith(".iso");
  }
}
