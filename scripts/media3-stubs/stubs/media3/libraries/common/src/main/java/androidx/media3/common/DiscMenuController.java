/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 */
package androidx.media3.common;

import androidx.media3.common.util.UnstableApi;
import java.util.function.Consumer;

/** Controls ISO/BD disc-menu navigation (opening menus, sending keys). */
@UnstableApi
public interface DiscMenuController {

  enum OpenResult {
    SUCCESS,
    UNAVAILABLE,
    TIMED_OUT,
    RUNTIME_MISSING
  }

  /** Whether the player is currently playing back disc-menu navigation content. */
  boolean isNavigationPlayback();

  /** Whether a disc menu is currently active (open on screen). */
  boolean isActive();

  /** Sends a navigation action ("up", "down", "left", "right", "select", "prev", ...). */
  boolean sendAction(String action);

  /**
   * Opens a disc menu. The result is delivered asynchronously through {@code callback}.
   *
   * @return false if the menu cannot be opened at all.
   */
  boolean open(String action, Consumer<OpenResult> callback);

  /** Whether pointer (touch/mouse) input is supported while a menu is active. */
  boolean supportsPointer();
}
