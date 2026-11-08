/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Connects the libass subtitle renderer to a PlayerView (subtitle view, style overrides,
 * secondary subtitle view). The real implementation drives the libass subtitle view; the
 * shim accepts the wiring and does nothing, so libass subtitles stay inactive while the
 * standard ExoPlayer subtitle view keeps working.
 */
package androidx.media3.ui.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.libass.LibassPlaybackSession;
import androidx.media3.exoplayer.libass.LibassSubtitleController;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import java.util.function.Consumer;

@UnstableApi
public final class LibassPlayerViewController {

  private final Player player;
  private final LibassPlaybackSession session;
  private final LibassSubtitleController controller;

  public LibassPlayerViewController(
      Player player,
      LibassPlaybackSession session,
      LibassSubtitleController controller) {
    this.player = player;
    this.session = session;
    this.controller = controller;
  }

  public void bind(PlayerView playerView) {}

  public void setStyleOverride(
      @Nullable CaptionStyleCompat style, @Nullable String fontFamily) {}

  public void setSecondarySubtitleViewConfigurator(
      Consumer<SubtitleView> configurator) {}

  public void close() {}
}
