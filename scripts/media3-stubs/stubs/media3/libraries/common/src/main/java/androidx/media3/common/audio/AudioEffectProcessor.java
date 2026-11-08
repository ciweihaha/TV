/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * AudioProcessor hook for the Exo audio effect chain. The real implementation applies the
 * configured equalizer / channel mix in software; this shim stays inactive so audio passes
 * through unchanged.
 *
 * Interface shape verified against FongMi/media @ 3c2cbe8a (Media3 1.11.0-based):
 * configure() returns AudioFormat (NOT_SET when inactive); the old
 * getOutputChannelCount()/getOutputSampleRateHz()/getOutputEncoding() methods no longer exist
 * upstream, and flush() defaults to throwing unless overridden.
 */
package androidx.media3.common.audio;

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
  public AudioFormat configure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    // Inactive: audio passes through unchanged; NOT_SET is the documented inactive output.
    return AudioFormat.NOT_SET;
  }

  @Override
  public boolean isActive() {
    return false;
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
    // No-op: the default implementation throws, so this must be overridden.
  }

  @Override
  public void reset() {
    parameters = AudioEffectParameters.DEFAULT;
  }
}
