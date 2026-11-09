/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Real GPU implementation of the colour/tone video effect (saturation, contrast, brightness,
 * gamma, hue rotation, colour-temperature gains). Follows the stock HslShaderProgram pattern:
 * a BaseGlShaderProgram driving a GlProgram built from inline GLSL strings, so the stubs tree
 * stays .java-only for install.py (no res/raw files to copy).
 *
 * Parameters come from VideoAdjustment, i.e. from the TV app's VideoEffectProfile presets.
 * Identity values (saturation 1, contrast 1, brightness 0, gamma 1, hue 0, gains 1) leave the
 * frame unchanged. The vertex shader is the stock vertex_shader_transformation_es2 shader.
 * HDR input is not supported by the shader math (same limitation as stock HslShaderProgram);
 * the effect returns a passthrough program for HDR instead of throwing, so playback keeps
 * working.
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
/* package */ final class ToneAdjustShaderProgram extends BaseGlShaderProgram {

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
          + "uniform float uSaturation;\n"
          + "uniform float uContrast;\n"
          + "uniform float uBrightness;\n"
          + "uniform float uGamma;\n"
          + "uniform float uHueDegrees;\n"
          + "uniform float uRedGain;\n"
          + "uniform float uBlueGain;\n"
          + "varying vec2 vTexSamplingCoord;\n"
          + "const vec3 LUMA_WEIGHTS = vec3(0.2126, 0.7152, 0.0722);\n"
          + "void main() {\n"
          + "  vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);\n"
          + "  vec3 rgb = inputColor.rgb;\n"
          + "  rgb += uBrightness;\n"
          + "  rgb = (rgb - 0.5) * uContrast + 0.5;\n"
          + "  float luma = dot(rgb, LUMA_WEIGHTS);\n"
          + "  rgb = mix(vec3(luma), rgb, uSaturation);\n"
          + "  float angle = radians(uHueDegrees);\n"
          + "  float cosA = cos(angle);\n"
          + "  float sinA = sin(angle);\n"
          + "  vec3 yiq = vec3(\n"
          + "    0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b,\n"
          + "    0.596 * rgb.r - 0.274 * rgb.g - 0.322 * rgb.b,\n"
          + "    0.211 * rgb.r - 0.523 * rgb.g + 0.312 * rgb.b);\n"
          + "  float iPrime = yiq.y * cosA - yiq.z * sinA;\n"
          + "  float qPrime = yiq.y * sinA + yiq.z * cosA;\n"
          + "  rgb = vec3(\n"
          + "    yiq.x + 0.956 * iPrime + 0.619 * qPrime,\n"
          + "    yiq.x - 0.272 * iPrime - 0.647 * qPrime,\n"
          + "    yiq.x - 1.106 * iPrime + 1.703 * qPrime);\n"
          + "  rgb = pow(max(rgb, vec3(0.0)), vec3(1.0 / max(uGamma, 0.01)));\n"
          + "  rgb.r *= uRedGain;\n"
          + "  rgb.b *= uBlueGain;\n"
          + "  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), inputColor.a);\n"
          + "}\n";

  private final GlProgram glProgram;

  public ToneAdjustShaderProgram(Context context, VideoAdjustment adjustment)
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

    glProgram.setFloatUniform("uSaturation", adjustment.getSaturation());
    glProgram.setFloatUniform("uContrast", adjustment.getContrast());
    glProgram.setFloatUniform("uBrightness", adjustment.getBrightness());
    glProgram.setFloatUniform("uGamma", adjustment.getGamma());
    glProgram.setFloatUniform("uHueDegrees", adjustment.getHue());
    glProgram.setFloatUniform("uRedGain", adjustment.getRedGain());
    glProgram.setFloatUniform("uBlueGain", adjustment.getBlueGain());
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
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
