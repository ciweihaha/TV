/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Renderers factory used by the Exo engine. The real implementation wires the FFmpeg
 * motion (frame interpolation) renderers and the FongMi decoder manager into the stock
 * DefaultRenderersFactory; this shim keeps stock renderer behaviour (no motion
 * interpolation, no decoder-mode switching) while preserving the API the app uses.
 */
package androidx.media3.decoder.ffmpeg;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderManager;
import androidx.media3.exoplayer.DefaultRenderersFactory;

@UnstableApi
public class FfmpegMotionRenderersFactory extends DefaultRenderersFactory {

  private final int targetFps;
  private final int motionVectorCount;
  private final boolean motionEnabled;
  private @Nullable DecoderManager decoderManager;
  private boolean surfaceControlFrameRetentionEnabled;

  public FfmpegMotionRenderersFactory(
      Context context, int targetFps, int motionVectorCount, boolean motionEnabled) {
    super(context);
    this.targetFps = targetFps;
    this.motionVectorCount = motionVectorCount;
    this.motionEnabled = motionEnabled;
  }

  /** Stores the decoder manager (decoder-mode switching is not applied by the shim). */
  public FfmpegMotionRenderersFactory setDecoderManager(DecoderManager decoderManager) {
    this.decoderManager = decoderManager;
    return this;
  }

  /** No-op in the shim (stock renderers have no such option in this Media3 version). */
  public FfmpegMotionRenderersFactory setEnableSurfaceControlFrameRetention(boolean enabled) {
    this.surfaceControlFrameRetentionEnabled = enabled;
    return this;
  }
}
