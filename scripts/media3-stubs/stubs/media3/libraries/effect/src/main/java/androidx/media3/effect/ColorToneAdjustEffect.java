/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Colour/tone video effect (saturation, contrast, brightness, colour temperature...). The
 * real implementation is a GPU shader effect; the shim renders as a pass-through so video
 * keeps playing with default colours when the effect is enabled.
 */
package androidx.media3.effect;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class ColorToneAdjustEffect implements GlEffect {

  private VideoAdjustment adjustment;

  public ColorToneAdjustEffect() {}

  public void setAdjustment(VideoAdjustment adjustment) {
    this.adjustment = adjustment;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
    return new PassthroughShaderProgram();
  }
}
