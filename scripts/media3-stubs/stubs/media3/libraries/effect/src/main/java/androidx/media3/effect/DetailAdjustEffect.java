/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Detail video effect (sharpness, shadow lift, threshold...). The real implementation is a
 * GPU shader effect; the shim renders as a pass-through so video keeps playing with
 * default detail when the effect is enabled.
 */
package androidx.media3.effect;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class DetailAdjustEffect implements GlEffect {

  private VideoAdjustment adjustment;

  public DetailAdjustEffect() {}

  public void setAdjustment(VideoAdjustment adjustment) {
    this.adjustment = adjustment;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
    return new PassthroughShaderProgram();
  }
}
