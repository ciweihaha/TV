/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Value object describing the audio effects the user configured (equalizer, center gain,
 * balance, loudness, stability...). The real implementation applies these through the
 * platform equalizer / FFmpeg; this shim only carries the values.
 */
package androidx.media3.common.audio;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.util.Arrays;

@UnstableApi
public final class AudioEffectParameters {

  public static final AudioEffectParameters DEFAULT = new Builder().build();

  private final int[] equalizerFrequencies;
  private final short[] equalizerLevels;
  private final float stability;
  private final int boostMillibels;
  private final int preampMillibels;
  private final int centerGainMillibels;
  private final float balance;
  private final int channelMode;
  private final boolean loudnessEnabled;

  private AudioEffectParameters(Builder builder) {
    this.equalizerFrequencies = builder.equalizerFrequencies;
    this.equalizerLevels = builder.equalizerLevels;
    this.stability = builder.stability;
    this.boostMillibels = builder.boostMillibels;
    this.preampMillibels = builder.preampMillibels;
    this.centerGainMillibels = builder.centerGainMillibels;
    this.balance = builder.balance;
    this.channelMode = builder.channelMode;
    this.loudnessEnabled = builder.loudnessEnabled;
  }

  public boolean hasEffect() {
    return hasBands()
        || centerGainMillibels != 0
        || balance != 0
        || stability > 0
        || boostMillibels != 0
        || preampMillibels != 0
        || loudnessEnabled
        || channelMode != AudioChannelMode.AUTO;
  }

  public boolean hasBands() {
    return equalizerLevels.length > 0;
  }

  public boolean hasCenterGain(int channelCount) {
    return isCenterGainAvailable(channelCount) && centerGainMillibels != 0;
  }

  /** Linear gain factor for the front-center channel. */
  public float getCenterGainFactor() {
    return (float) Math.pow(10.0, centerGainMillibels / 2000.0);
  }

  public int getChannelMode() {
    return channelMode;
  }

  public float getBalance() {
    return balance;
  }

  public boolean isLoudnessEnabled() {
    return loudnessEnabled;
  }

  public float getStability() {
    return stability;
  }

  public int getBoostMillibels() {
    return boostMillibels;
  }

  public int getPreampMillibels() {
    return preampMillibels;
  }

  public short[] getEqualizerLevelsMillibels() {
    return equalizerLevels.clone();
  }

  public int[] getEqualizerFrequencies() {
    return equalizerFrequencies.clone();
  }

  /** Whether a limiter is needed to avoid clipping with the current gains. */
  public boolean shouldLimitOutput(int channelCount) {
    return hasBands() || boostMillibels != 0 || preampMillibels != 0;
  }

  public static boolean isCenterGainAvailable(int channelCount) {
    return channelCount >= 3;
  }

  public static final class Builder {
    private int[] equalizerFrequencies = new int[0];
    private short[] equalizerLevels = new short[0];
    private float stability;
    private int boostMillibels;
    private int preampMillibels;
    private int centerGainMillibels;
    private float balance;
    private int channelMode = AudioChannelMode.AUTO;
    private boolean loudnessEnabled;

    public Builder setEqualizerBands(int[] frequencies, short[] levels) {
      this.equalizerFrequencies = frequencies == null ? new int[0] : frequencies.clone();
      this.equalizerLevels = levels == null ? new short[0] : levels.clone();
      return this;
    }

    public Builder setStability(float stability) {
      this.stability = stability;
      return this;
    }

    public Builder setBoostMillibels(int boostMillibels) {
      this.boostMillibels = boostMillibels;
      return this;
    }

    public Builder setPreampMillibels(int preampMillibels) {
      this.preampMillibels = preampMillibels;
      return this;
    }

    public Builder setCenterGainMillibels(int centerGainMillibels) {
      this.centerGainMillibels = centerGainMillibels;
      return this;
    }

    public Builder setBalance(float balance) {
      this.balance = balance;
      return this;
    }

    public Builder setChannelMode(int channelMode) {
      this.channelMode = channelMode;
      return this;
    }

    public Builder setLoudnessEnabled(boolean loudnessEnabled) {
      this.loudnessEnabled = loudnessEnabled;
      return this;
    }

    public AudioEffectParameters build() {
      return new AudioEffectParameters(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AudioEffectParameters)) return false;
    AudioEffectParameters other = (AudioEffectParameters) o;
    return Arrays.equals(equalizerFrequencies, other.equalizerFrequencies)
        && Arrays.equals(equalizerLevels, other.equalizerLevels)
        && Float.compare(stability, other.stability) == 0
        && boostMillibels == other.boostMillibels
        && preampMillibels == other.preampMillibels
        && centerGainMillibels == other.centerGainMillibels
        && Float.compare(balance, other.balance) == 0
        && channelMode == other.channelMode
        && loudnessEnabled == other.loudnessEnabled;
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(equalizerFrequencies);
    result = 31 * result + Arrays.hashCode(equalizerLevels);
    result = 31 * result + Float.hashCode(stability);
    result = 31 * result + boostMillibels;
    result = 31 * result + preampMillibels;
    result = 31 * result + centerGainMillibels;
    result = 31 * result + Float.hashCode(balance);
    result = 31 * result + channelMode;
    result = 31 * result + Boolean.hashCode(loudnessEnabled);
    return result;
  }
}
