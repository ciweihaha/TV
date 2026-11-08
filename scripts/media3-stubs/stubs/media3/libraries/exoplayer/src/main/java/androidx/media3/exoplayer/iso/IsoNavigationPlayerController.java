/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Wraps an ExoPlayer to add ISO/BD disc navigation (menus, BD-J). The real
 * implementation intercepts playback for ISO images and exposes the disc menu through
 * {@link androidx.media3.common.DiscMenuController}; the shim passes the underlying
 * player through unchanged, so ISO files play as plain files and disc menus report
 * "unavailable".
 */
package androidx.media3.exoplayer.iso;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.DiscMenuController;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import java.util.function.Consumer;

@UnstableApi
public final class IsoNavigationPlayerController implements DiscMenuController {

  private final ExoPlayer player;

  public IsoNavigationPlayerController(ExoPlayer player) {
    this.player = player;
  }

  /** The player to use for playback (the wrapped ExoPlayer in the shim). */
  public Player getPlayer() {
    return player;
  }

  /**
   * Prepares a media source for playback. Returns the source unchanged (ISO preparation
   * is inactive in the shim).
   */
  public @Nullable MediaSource prepareMediaSource(
      Context context, MediaSource mediaSource, DataSource.Factory dataSourceFactory) {
    return mediaSource;
  }

  /** Whether disc navigation playback has started (always false in the shim). */
  public boolean isStarted() {
    return false;
  }

  public void close() {}

  @Override
  public boolean isNavigationPlayback() {
    return false;
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public boolean sendAction(String action) {
    return false;
  }

  @Override
  public boolean open(String action, Consumer<OpenResult> callback) {
    return false;
  }

  @Override
  public boolean supportsPointer() {
    return false;
  }
}
