/*
 * Compatibility shim for the FongMi/TV fork.
 *
 * The upstream FongMi/TV app (5.8.7) uses private Media3 extensions that are not part of the
 * public FongMi/media source (decoder modes, disc menus, subtitle content/transcript, libass
 * UI, ...). This file re-creates the minimal public API surface the app code uses, so the app
 * can be built from the public Media3 source. Features backed by these shims degrade
 * gracefully (see scripts/media3-stubs/README.md).
 */
package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;

/** Decoder selection mode (audio/video). */
@UnstableApi
public enum DecoderMode {
  AUTO,
  HARDWARE,
  SOFTWARE,
  FFMPEG
}
