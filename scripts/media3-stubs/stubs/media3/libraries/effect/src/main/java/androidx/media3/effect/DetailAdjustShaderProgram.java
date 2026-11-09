/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Real GPU implementation of the detail video effect: sharpness via a 3x3 unsharp mask with a
 * local-contrast threshold gate (only sharpens where neighbouring pixels differ enough, so flat
 * areas and noise are not amplified), plus a shadow lift that raises dark areas. Same
 * BaseGlShaderProgram pattern as ToneAdjustShaderProgram. The texel size depends on the input
 * frame dimensions, so it is set in configure() rather than in the constructor.
 *
 * Identity values (sharpness 0, shadowLift 0) leave the frame unchanged. HDR input is not
 * supported by the shader math; the effect returns a passthrough program for HDR instead of
 * throwing, so playback keeps working.
 */
package androidx.media3.effect;

import android.content.Context;
import android.opengl.GLES20;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
/* package */ final class DetailAdjustShaderProgram extends BaseGlShaderProgram {

  private static final String VERTEX_SHADER =
      "#version 100\n"
          + "attribute vec4 aFramePosition;\n"
          + "uniform mat4 uTransformationMatrix;\n"
          + "uniform mat4 uTexTransformationMatrix;\n"
          + "varying vec2 vTexSamplingCoord;\n"
          + "void main() {\n"
          + "  gl_Position = uTransformationMatrix * aFramePosition;\n"
          + "  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,\n"
          + "                              aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);\n"
          + "  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;\n"
          + "}\n";

  private static final String FRAGMENT_SHADER =
      "#version 100\n"
          + "precision highp float;\n"
          + "uniform sampler2D uTexSampler;\n"
          + "uniform vec2 uTexelSize;\n"
          + "uniform float uSharpness;\n"
          + "uniform float uThreshold;\n"
          + "uniform float uShadowLift;\n"
          + "varying vec2 vTexSamplingCoord;\n"
          + "void main() {\n"
          + "  vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);\n"
          + "  vec3 blur = vec3(0.0);\n"
          + "  for (int x = -1; x <= 1; x++) {\n"
          + "    for (int y = -1; y <= 1; y++) {\n"
          + "      blur += texture2D(uTexSampler,\n"
          + "          vTexSamplingCoord + vec2(float(x), float(y)) * uTexelSize).rgb;\n"
          + "    }\n"
          + "  }\n"
          + "  blur /= 9.0;\n"
          + "  vec3 detail = inputColor.rgb - blur;\n"
          + "  float mask = smoothstep(uThreshold, uThreshold * 2.0 + 0.0001, length(detail));\n"
          + "  vec3 sharp = inputColor.rgb + detail * uSharpness * mask;\n"
          + "  float luma = dot(sharp, vec3(0.2126, 0.7152, 0.0722));\n"
          + "  sharp += uShadowLift * (1.0 - luma) * (1.0 - sharp);\n"
          + "  gl_FragColor = vec4(clamp(sharp, 0.0, 1.0), inputColor.a);\n"
          + "}\n";

  private final GlProgram glProgram;

  public DetailAdjustShaderProgram(Context context, VideoAdjustment adjustment)
      throws VideoFrameProcessingException {
    super(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1);
    try {
      glProgram = new GlProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);

    glProgram.setFloatUniform("uSharpness", adjustment.getSharpness());
    glProgram.setFloatUniform("uThreshold", adjustment.getThreshold());
    glProgram.setFloatUniform("uShadowLift", adjustment.getShadowLift());
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    glProgram.setFloatsUniform(
        "uTexelSize", new float[] {1.0f / inputWidth, 1.0f / inputHeight});
    return new Size(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException {
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.bindAttributesAndUniforms();

      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e, presentationTimeUs);
    }
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    try {
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }
}
