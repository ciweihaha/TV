/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Encodes the selected ISO edition index into a media URI. The index travels as a URI
 * fragment so it never reaches the server and never changes the resolved path.
 */
package androidx.media3.datasource;

import android.net.Uri;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class IsoUri {

  private IsoUri() {}

  public static Uri withEditionIndex(Uri uri, int editionIndex) {
    if (uri == null) return null;
    return uri.buildUpon().fragment("iso-edition=" + editionIndex).build();
  }
}
