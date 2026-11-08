/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Detects CMG (China Media Group / CCTV) streams, which must play through the Exo engine.
 * The shim never detects CMG content, so engine selection falls back to the user setting.
 */
package androidx.media3.exoplayer.hls;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class CmgConfiguration {

  private CmgConfiguration() {}

  /** Returns the CMG configuration for a CMG media URI, or null when it is not one. */
  public static @Nullable CmgConfiguration fromMediaUri(Uri uri) {
    return null;
  }
}
