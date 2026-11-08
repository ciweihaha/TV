/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Builds channel-mix matrices (channelCount x channelCount, row = output channel, column =
 * input channel) for the mpv pan filter. Channel order follows the standard layout:
 * 0=FL, 1=FR, 2=FC, 3=LFE, 4=BL, 5=BR, 6=SL, 7=SR.
 */
package androidx.media3.common.audio;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class AudioChannelMix {

  private AudioChannelMix() {}

  /** Boosts (or cuts) the front-center channel by a linear {@code factor}. */
  public static float[][] createFrontCenterGainMix(int channelCount, float factor) {
    float[][] mix = identity(channelCount);
    if (channelCount > 2) {
      mix[2][2] = 1.0f + factor;
    }
    return mix;
  }

  /**
   * Stereo downmix: FL/FR keep their own channel and fold in center (at -3 dB) and the
   * surround channels (at -6 dB). With {@code reverse} the left/right rows are swapped.
   */
  public static float[][] createStereoMix(int channelCount, boolean reverse) {
    float[][] mix = new float[channelCount][channelCount];
    int left = reverse ? 1 : 0;
    int right = reverse ? 0 : 1;
    mix[left][0] = 1.0f;
    mix[right][1] = 1.0f;
    if (channelCount > 2) {
      mix[left][2] = 0.7071f;
      mix[right][2] = 0.7071f;
    }
    if (channelCount > 4) {
      mix[left][4] = 0.5f;
      mix[right][5] = 0.5f;
    }
    if (channelCount > 6) {
      mix[left][6] = 0.5f;
      mix[right][7] = 0.5f;
    }
    return mix;
  }

  /** Mono downmix: every output channel is the average of all input channels. */
  public static float[][] createMonoMix(int channelCount) {
    float[][] mix = new float[channelCount][channelCount];
    float weight = 1.0f / Math.max(1, channelCount);
    for (int row = 0; row < channelCount; row++) {
      for (int col = 0; col < channelCount; col++) {
        mix[row][col] = weight;
      }
    }
    return mix;
  }

  /**
   * Front balance: {@code balance} in [-1, 1]; negative shifts left, positive shifts right.
   */
  public static float[][] createFrontBalanceMix(int channelCount, float balance) {
    float[][] mix = identity(channelCount);
    if (channelCount >= 2) {
      float leftGain = balance < 0 ? 1.0f : 1.0f - balance;
      float rightGain = balance > 0 ? 1.0f : 1.0f + balance;
      mix[0][0] = leftGain;
      mix[1][1] = rightGain;
    }
    return mix;
  }

  /** Matrix product: applies {@code next} first, then {@code mix}. */
  public static float[][] compose(float[][] mix, float[][] next) {
    int n = mix.length;
    float[][] out = new float[n][n];
    for (int row = 0; row < n; row++) {
      for (int col = 0; col < n; col++) {
        float sum = 0;
        for (int k = 0; k < n; k++) {
          sum += mix[row][k] * next[k][col];
        }
        out[row][col] = sum;
      }
    }
    return out;
  }

  private static float[][] identity(int channelCount) {
    float[][] mix = new float[channelCount][channelCount];
    for (int i = 0; i < channelCount; i++) {
      mix[i][i] = 1.0f;
    }
    return mix;
  }
}
