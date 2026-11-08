/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 */
package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;
import java.util.Objects;

/** Scale/pan transform applied to the video surface (mpv-style video-scale commands). */
@UnstableApi
public final class VideoTransform {

  public static final float MAX_SCALE = 8.0f;

  public final float scaleX;
  public final float scaleY;
  public final float panX;
  public final float panY;

  public VideoTransform(float scaleX, float scaleY, float panX, float panY) {
    this.scaleX = scaleX;
    this.scaleY = scaleY;
    this.panX = panX;
    this.panY = panY;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VideoTransform)) return false;
    VideoTransform other = (VideoTransform) o;
    return Float.compare(scaleX, other.scaleX) == 0
        && Float.compare(scaleY, other.scaleY) == 0
        && Float.compare(panX, other.panX) == 0
        && Float.compare(panY, other.panY) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(scaleX, scaleY, panX, panY);
  }
}
