/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Reports whether the MPV engine can play a DRM-protected media item. The shim always
 * says no, so DRM items fall back to the Exo engine (which handles DRM properly).
 */
package androidx.media3.mpvplayer.media;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class MpvDrmSupport {

  private MpvDrmSupport() {}

  public static boolean supports(MediaItem item) {
    return false;
  }
}
