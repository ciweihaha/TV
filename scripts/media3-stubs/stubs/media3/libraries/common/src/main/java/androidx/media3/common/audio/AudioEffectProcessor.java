/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * AudioProcessor hook for the Exo audio effect chain. The real implementation applies the
 * configured equalizer / channel mix in software; this shim stays inactive so audio passes
 * through unchanged.
 */
package androidx.media3.common.audio;

import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

@UnstableApi
public final class AudioEffectProcessor implements AudioProcessor {

  private AudioEffectParameters parameters = AudioEffectParameters.DEFAULT;

  public AudioEffectProcessor() {}

  /** Applies new effect parameters (no-op in the shim: the processor stays inactive). */
  public void setParameters(AudioEffectParameters parameters, boolean softwareEqualizer) {
    this.parameters = parameters == null ? AudioEffectParameters.DEFAULT : parameters;
  }

  /** Clears the effect parameters. */
  public void resetParameters() {
    this.parameters = AudioEffectParameters.DEFAULT;
  }

  @Override
  public boolean configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
    // Inactive: audio passes through unchanged.
    return false;
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public int getOutputChannelCount() {
    return Format.NO_VALUE;
  }

  @Override
  public int getOutputSampleRateHz() {
    return Format.NO_VALUE;
  }

  @Override
  public int getOutputEncoding() {
    return Format.NO_VALUE;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    // No-op: inactive.
  }

  @Override
  public void queueEndOfStream() {
    // No-op.
  }

  @Override
  public ByteBuffer getOutput() {
    return EMPTY_BUFFER;
  }

  @Override
  public boolean isEnded() {
    return true;
  }

  @Override
  public void flush() {
    // No-op.
  }

  @Override
  public void reset() {
    parameters = AudioEffectParameters.DEFAULT;
  }
}
