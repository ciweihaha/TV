/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Blu-ray Disc Java runtime (BDJ module). The real implementation prepares the BDJ
 * runtime from the downloaded module APK; the shim reports "not installed", so the
 * module UI offers installation instead of failing.
 */
package androidx.media3.exoplayer.iso;

import android.app.Application;
import android.os.CancellationSignal;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class BdjRuntime {

  /** Status of the installed BDJ runtime. */
  public static final class Status {
    public final boolean restartRequired;

    public Status(boolean restartRequired) {
      this.restartRequired = restartRequired;
    }
  }

  private BdjRuntime() {}

  /** Prepares the BDJ runtime (no-op in the shim: the runtime stays unavailable). */
  public static void prepare(Application application, CancellationSignal signal) {}

  /** Returns the status of the installed BDJ runtime (always "not installed"). */
  public static Status getStatus(Application application) {
    return new Status(false);
  }
}
