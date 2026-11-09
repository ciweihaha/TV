/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Detail video effect (sharpness, shadow lift, threshold...). Real GPU implementation since
 * round 119: DetailAdjustShaderProgram applies a 3x3 unsharp mask with a local-contrast
 * threshold gate plus shadow lift. HDR input falls back to passthrough (the shader math is
 * SDR-only), and a shader program creation failure is logged and falls back to passthrough,
 * so video keeps playing.
 */
package androidx.media3.effect;

import android.content.Context;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class DetailAdjustEffect implements GlEffect {

  private static final String TAG = "DetailAdjustEffect";

  private VideoAdjustment adjustment;

  public DetailAdjustEffect() {}

  public void setAdjustment(VideoAdjustment adjustment) {
    this.adjustment = adjustment;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
    if (useHdr || adjustment == null) {
      return new PassthroughShaderProgram();
    }
    try {
      return new DetailAdjustShaderProgram(context, adjustment);
    } catch (VideoFrameProcessingException e) {
      Log.e(TAG, "Failed to create detail adjust shader program, falling back to passthrough", e);
      return new PassthroughShaderProgram();
    }
  }
}
