/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * TextOutput for the secondary (dual) subtitle track. The real implementation forwards
 * cues to the libass secondary subtitle view; the shim drops them (secondary subtitles
 * are inactive).
 */
package androidx.media3.exoplayer.text;

import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class SecondaryTextOutput implements TextOutput {

  public SecondaryTextOutput() {}

  @Override
  public void onCues(CueGroup cueGroup) {
    // No-op: secondary subtitles are inactive in the shim.
  }
}
