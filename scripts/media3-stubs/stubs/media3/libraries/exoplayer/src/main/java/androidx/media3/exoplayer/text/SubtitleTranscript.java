/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * The live subtitle transcript of a media item (transcribed / fetched subtitle text with
 * timings), used by the subtitle-content dialog and the transcript subtitle parser. The
 * shim carries no transcript; sessions report "no transcript".
 */
package androidx.media3.exoplayer.text;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class SubtitleTranscript {

  public SubtitleTranscript() {}
}
