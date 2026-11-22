/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Identifies one font face for subtitle rendering: a font file plus the index of the
 * face inside it (0 for a single TTF/OTF, the face index for a TTC/OTC collection).
 * Mirrors the Media3 API used by upstream FongMi/TV (ExternalFont, ExoSubtitleController,
 * MpvSubtitleOptions); the fork's public Media3 source does not contain this class.
 */
package androidx.media3.common.text;

import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.util.Objects;

/** A font file plus the index of the face to use from it. */
@UnstableApi
public final class SubtitleFontFile {

  public final File file;
  public final int ttcIndex;

  public SubtitleFontFile(File file, int ttcIndex) {
    this.file = file;
    this.ttcIndex = ttcIndex;
  }

  public File getFile() {
    return file;
  }

  public int getTtcIndex() {
    return ttcIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SubtitleFontFile)) return false;
    SubtitleFontFile other = (SubtitleFontFile) o;
    return ttcIndex == other.ttcIndex && Objects.equals(file, other.file);
  }

  @Override
  public int hashCode() {
    return Objects.hash(file, ttcIndex);
  }

  @Override
  public String toString() {
    return "SubtitleFontFile{file=" + file + ", ttcIndex=" + ttcIndex + "}";
  }
}
