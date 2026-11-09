/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Colour/tone video effect (saturation, contrast, brightness, gamma, hue rotation, colour
 * temperature...). Real GPU implementation since round 119: ToneAdjustShaderProgram applies the
 * VideoAdjustment in a single fragment shader. HDR input falls back to passthrough (the shader
 * math is SDR-only, same limitation as stock HslShaderProgram), and a shader program creation
 * failure is logged and falls back to passthrough, so video keeps playing.
 */
package androidx.media3.effect;

import android.content.Context;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class ColorToneAdjustEffect implements GlEffect {

  private static final String TAG = "ColorToneAdjustEffect";

  private VideoAdjustment adjustment;

  public ColorToneAdjustEffect() {}

  public void setAdjustment(VideoAdjustment adjustment) {
    this.adjustment = adjustment;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
    if (useHdr || adjustment == null) {
      return new PassthroughShaderProgram();
    }
    try {
      return new ToneAdjustShaderProgram(context, adjustment);
    } catch (VideoFrameProcessingException e) {
      Log.e(TAG, "Failed to create tone adjust shader program, falling back to passthrough", e);
      return new PassthroughShaderProgram();
    }
  }
}
