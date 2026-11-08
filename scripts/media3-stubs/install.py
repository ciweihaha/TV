#!/usr/bin/env python3
"""Install FongMi private-API compatibility shims into Media3 checkouts.

The upstream FongMi/TV app (5.8.7) uses private Media3 extensions that are not part of
the public FongMi/media source (decoder modes, disc menus, subtitle content/transcript,
libass UI, ...). This script:

  1. Copies the shim sources from stubs/media3/ into a FongMi/media checkout and from
     stubs/media3-ass/ into a FongMi/media (libass) checkout, mirroring the module
     layout, so they are compiled into the AARs.
  2. Patches a few stock Media3 files to add the missing extension methods the app
     calls (Player / PlayerView / MpvPlayer / LibassPlaybackSession /
     DefaultExtractorsFactory).

Every patch uses an exact anchor with an assertion: if the upstream source changes,
the script fails loudly instead of silently skipping. Run from the repo root:

    python3 scripts/media3-stubs/install.py --media3 media3 --media3-ass media3-ass

See README.md for the full list of shims and the known runtime degradations.
"""

import argparse
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise AssertionError(
            f"{label}: anchor found {count} times in {path} (expected exactly 1)"
        )
    path.write_text(text.replace(old, new))
    print(f"patched {label} in {path}")


def copy_stubs(src_root: Path, dst_root: Path) -> int:
    copied = 0
    for src in sorted(src_root.rglob("*.java")):
        rel = src.relative_to(src_root)
        dst = dst_root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        copied += 1
    print(f"copied {copied} shim sources from {src_root} to {dst_root}")
    return copied


# Shims that live in stubs/media3/ but are also needed by the media3-ass
# checkout: the LibassSubtitleController shim and the patched
# LibassPlaybackSession reference them, and the libass checkout is built
# separately (its own composite build), so they must be present there too.
# Verified by auditing every media3-ass shim import against the 9dae39e9
# file tree: exactly these four are missing there (transitive closure).
MEDIA3_ASS_EXTRA_SHIMS = [
    "libraries/common/src/main/java/androidx/media3/common/text/SubtitleSelectionState.java",
    "libraries/exoplayer/src/main/java/androidx/media3/exoplayer/text/SecondaryTextOutput.java",
    "libraries/exoplayer/src/main/java/androidx/media3/exoplayer/text/SubtitleTranscript.java",
    "libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/SecondaryTextTrackSelector.java",
]


def copy_extra_shims_for_ass(media3_ass: Path) -> None:
    for rel in MEDIA3_ASS_EXTRA_SHIMS:
        src = HERE / "stubs" / "media3" / rel
        dst = media3_ass / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        print(f"copied extra shim for media3-ass: {rel}")


# ---------------------------------------------------------------- Player.java
def patch_player(media3: Path) -> None:
    path = media3 / "libraries/common/src/main/java/androidx/media3/common/Player.java"
    replace_once(
        path,
        "import androidx.media3.common.text.Cue;",
        "import androidx.media3.common.text.Cue;\nimport androidx.media3.common.text.SubtitleOffsets;",
        "Player import SubtitleOffsets",
    )
    replace_once(
        path,
        """  @UnstableApi
  default long getTextOffsetMs() {
    throw new UnsupportedOperationException();
  }
""",
        """  @UnstableApi
  default long getTextOffsetMs() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the disc-menu state of the current media item.
   *
   * <p>FongMi extension (shim): always returns {@link DiscMenuState#EMPTY}.
   */
  @UnstableApi
  default DiscMenuState getDiscMenuState() {
    return DiscMenuState.EMPTY;
  }

  /**
   * Returns the decoder mode for the given track type, or null when not applicable.
   *
   * <p>FongMi extension (shim): always returns null.
   */
  @UnstableApi
  default @Nullable DecoderMode getDecoderMode(@C.TrackType int trackType) {
    return null;
  }

  /**
   * Returns the primary/secondary subtitle offsets.
   *
   * <p>FongMi extension (shim): always returns {@link SubtitleOffsets#ZERO}.
   */
  @UnstableApi
  default SubtitleOffsets getSubtitleOffsets() {
    return SubtitleOffsets.ZERO;
  }

  /**
   * Sets the primary/secondary subtitle offsets.
   *
   * <p>FongMi extension (shim): no-op.
   */
  @UnstableApi
  default void setSubtitleOffsets(SubtitleOffsets offsets) {}
""",
        "Player disc-menu/decoder-mode/subtitle-offset methods",
    )
    replace_once(
        path,
        "    default void onIsPlayingChanged(boolean isPlaying) {}",
        """    default void onIsPlayingChanged(boolean isPlaying) {}

    /**
     * Called when the disc-menu state changes.
     *
     * <p>FongMi extension (shim): never called by the stock player.
     */
    default void onDiscMenuStateChanged(DiscMenuState state) {}""",
        "Player.Listener onDiscMenuStateChanged",
    )


# ------------------------------------------------------------- PlayerView.java
def patch_player_view(media3: Path) -> None:
    path = media3 / "libraries/ui/src/main/java/androidx/media3/ui/PlayerView.java"
    replace_once(
        path,
        "import androidx.media3.common.Player;",
        "import androidx.media3.common.DiscMenuController;\n"
        "import androidx.media3.common.Player;\n"
        "import androidx.media3.common.VideoTransform;",
        "PlayerView imports",
    )
    replace_once(
        path,
        """  @UnstableApi
  @Nullable
  public View getVideoSurfaceView() {
    return surfaceView;
  }
""",
        """  @UnstableApi
  @Nullable
  public View getVideoSurfaceView() {
    return surfaceView;
  }

  // ---- FongMi extensions (shims, see scripts/media3-stubs/README.md) ----

  private @Nullable DiscMenuController discMenuController;
  private VideoTransform videoTransform = new VideoTransform(1.0f, 1.0f, 0.0f, 0.0f);
  private float videoAspectRatioOverride;

  /** Sets the disc-menu controller (stored only; no disc-menu UI in the shim). */
  @UnstableApi
  public void setDiscMenuController(@Nullable DiscMenuController discMenuController) {
    this.discMenuController = discMenuController;
  }

  /** Returns the disc-menu controller set by {@link #setDiscMenuController}. */
  @UnstableApi
  public @Nullable DiscMenuController getDiscMenuController() {
    return discMenuController;
  }

  /** Returns the current video scale/pan transform. */
  @UnstableApi
  public VideoTransform getVideoTransform() {
    return videoTransform;
  }

  /** Applies a video scale/pan transform to the video surface view. */
  @UnstableApi
  public void setVideoTransform(VideoTransform videoTransform) {
    this.videoTransform =
        videoTransform == null
            ? new VideoTransform(1.0f, 1.0f, 0.0f, 0.0f)
            : videoTransform;
    View surfaceView = getVideoSurfaceView();
    if (surfaceView != null) {
      surfaceView.setScaleX(this.videoTransform.scaleX);
      surfaceView.setScaleY(this.videoTransform.scaleY);
      surfaceView.setTranslationX(this.videoTransform.panX);
      surfaceView.setTranslationY(this.videoTransform.panY);
    }
  }

  /** Returns the video aspect-ratio override (0 = none). */
  @UnstableApi
  public float getVideoAspectRatioOverride() {
    return videoAspectRatioOverride;
  }

  /** Stores a video aspect-ratio override (not applied by the shim). */
  @UnstableApi
  public void setVideoAspectRatioOverride(float videoAspectRatioOverride) {
    this.videoAspectRatioOverride = videoAspectRatioOverride;
  }

  /** Sets the backend player used for rendering (delegates to {@link #setPlayer}). */
  @UnstableApi
  public void setBackendPlayer(@Nullable Player player) {
    setPlayer(player);
  }

  /** Forwards a touch event to the active disc menu (no-op in the shim). */
  @UnstableApi
  public boolean dispatchDiscMenuTouchEvent(MotionEvent event) {
    return false;
  }
""",
        "PlayerView FongMi extensions",
    )


# -------------------------------------------------------------- MpvPlayer.java
def patch_mpv_player(media3: Path) -> None:
    path = (
        media3
        / "libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java"
    )
    replace_once(
        path,
        "import androidx.media3.common.TrackSelectionParameters;",
        "import androidx.media3.common.DiscMenuController;\n"
        "import androidx.media3.common.TrackSelectionOverride;\n"
        "import androidx.media3.common.TrackSelectionParameters;\n"
        "import androidx.media3.common.text.SubtitleContent;\n"
        "import androidx.media3.common.text.SubtitleOffsets;\n"
        "import androidx.media3.common.text.SubtitleSelectionState;",
        "MpvPlayer imports",
    )
    replace_once(
        path,
        "import java.util.List;",
        "import java.util.List;\nimport java.util.function.Consumer;",
        "MpvPlayer import Consumer",
    )
    replace_once(
        path,
        "public final class MpvPlayer extends SimpleBasePlayer {",
        "public final class MpvPlayer extends SimpleBasePlayer implements DiscMenuController {",
        "MpvPlayer implements DiscMenuController",
    )
    replace_once(
        path,
        """  public void setSubtitleOptions(MpvSubtitleOptions subtitleOptions) {
    MpvPlayerConfig config =
        new MpvPlayerConfig.Builder()
            .addAndroidSubtitleOptions(applicationContext, checkNotNull(subtitleOptions))
            .build();
    runOnPlayerLooper(() -> subtitleController.setOptions(config));
  }
""",
        """  public void setSubtitleOptions(MpvSubtitleOptions subtitleOptions) {
    MpvPlayerConfig config =
        new MpvPlayerConfig.Builder()
            .addAndroidSubtitleOptions(applicationContext, checkNotNull(subtitleOptions))
            .build();
    runOnPlayerLooper(() -> subtitleController.setOptions(config));
  }

  // ---- FongMi extensions (shims, see scripts/media3-stubs/README.md) ----

  private MpvDecoderMode videoDecoderMode = MpvDecoderMode.AUTO;
  private @Nullable MpvSubtitleContentLoader subtitleContentLoader;
  private SubtitleOffsets subtitleOffsets = SubtitleOffsets.ZERO;
  private boolean subtitleContentEnabled = true;
  private @Nullable TrackSelectionOverride secondaryTextTrackSelectionOverride;
  private boolean secondaryTextTrackAutoSelectionEnabled = true;

  public MpvDecoderMode getVideoDecoderMode() {
    return videoDecoderMode;
  }

  public void setVideoDecoderMode(MpvDecoderMode videoDecoderMode) {
    if (videoDecoderMode != null) this.videoDecoderMode = videoDecoderMode;
  }

  public SubtitleContent getSubtitleContentForSelection(TrackSelectionOverride selection) {
    return SubtitleContent.UNSUPPORTED;
  }

  public void setSubtitleContentEnabled(boolean enabled) {
    this.subtitleContentEnabled = enabled;
  }

  public boolean canRetrySubtitleContent(Format format) {
    return false;
  }

  public void retrySubtitleContent(Format format) {}

  public SubtitleOffsets getSubtitleOffsets() {
    return subtitleOffsets;
  }

  public void setSubtitleOffsets(SubtitleOffsets offsets) {
    if (offsets != null) subtitleOffsets = offsets;
  }

  public SubtitleSelectionState getSubtitleSelectionState() {
    return SubtitleSelectionState.EMPTY;
  }

  public void setSubtitleContentLoader(MpvSubtitleContentLoader loader) {
    this.subtitleContentLoader = loader;
  }

  public void setSecondaryTextTrackSelectionOverride(
      @Nullable TrackSelectionOverride selection) {
    this.secondaryTextTrackSelectionOverride = selection;
  }

  public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {
    this.secondaryTextTrackAutoSelectionEnabled = enabled;
  }

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
  public boolean open(String action, Consumer<DiscMenuController.OpenResult> callback) {
    return false;
  }

  @Override
  public boolean supportsPointer() {
    return false;
  }
""",
        "MpvPlayer FongMi extensions",
    )
    replace_once(
        path,
        """    @CanIgnoreReturnValue
    public Builder setConfig(MpvPlayerConfig config) {""",
        """    private @Nullable MpvDecoderMode videoDecoderMode;

    @CanIgnoreReturnValue
    public Builder setVideoDecoderMode(MpvDecoderMode videoDecoderMode) {
      checkState(!buildCalled);
      this.videoDecoderMode = videoDecoderMode;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setConfig(MpvPlayerConfig config) {""",
        "MpvPlayer.Builder setVideoDecoderMode",
    )
    replace_once(
        path,
        """    public MpvPlayer build() {
      checkState(!buildCalled);
      buildCalled = true;
      return new MpvPlayer(context, config, decode, looper);
    }""",
        """    public MpvPlayer build() {
      checkState(!buildCalled);
      buildCalled = true;
      MpvPlayer player = new MpvPlayer(context, config, decode, looper);
      if (videoDecoderMode != null) player.setVideoDecoderMode(videoDecoderMode);
      return player;
    }""",
        "MpvPlayer.Builder build applies decoder mode",
    )


# ------------------------------------------------- LibassPlaybackSession.java
def patch_libass_session(media3_ass: Path) -> None:
    path = (
        media3_ass
        / "libraries/exoplayer_libass/src/main/java/androidx/media3/exoplayer/libass/LibassPlaybackSession.java"
    )
    replace_once(
        path,
        "import androidx.media3.exoplayer.Renderer;",
        "import androidx.media3.exoplayer.Renderer;\n"
        "import androidx.media3.exoplayer.text.SubtitleTranscript;",
        "LibassPlaybackSession import SubtitleTranscript",
    )
    replace_once(
        path,
        """  /** Creates a session with an explicit libass enablement snapshot. */
  public LibassPlaybackSession(""",
        """  /** Creates a session from a {@link LibassConfiguration} (FongMi extension shim). */
  public LibassPlaybackSession(LibassConfiguration configuration, boolean enabled) {
    this(
        configuration == null ? null : configuration.getFontConfig(),
        configuration == null ? null : configuration.getFontsDirectory(),
        configuration == null ? null : configuration.getDefaultFontFamily(),
        enabled);
  }

  /** Creates a session with an explicit libass enablement snapshot. */
  public LibassPlaybackSession(""",
        "LibassPlaybackSession LibassConfiguration constructor",
    )
    replace_once(
        path,
        """  /** Creates matching subtitle-parser and extractor factories for {@code mediaItem}. */
  public synchronized MediaComponents createMediaComponents(""",
        """  /**
   * Creates matching subtitle-parser and extractor factories for {@code mediaItem}, with
   * an optional live subtitle transcript (FongMi extension shim: the transcript is
   * ignored).
   */
  public synchronized MediaComponents createMediaComponents(
      MediaItem mediaItem,
      ExtractorsFactory extractorsFactory,
      @Nullable SubtitleTranscript transcript) {
    return createMediaComponents(mediaItem, extractorsFactory);
  }

  /** Creates matching subtitle-parser and extractor factories for {@code mediaItem}. */
  public synchronized MediaComponents createMediaComponents(""",
        "LibassPlaybackSession createMediaComponents with transcript",
    )
    replace_once(
        path,
        """  @Override
  public synchronized void close() {""",
        """  /** Sets the bottom position of the subtitle view as a fraction (shim: no-op). */
  public synchronized void setBottomPositionFraction(float fraction) {}

  /** Sets the bottom position of the secondary subtitle view (shim: no-op). */
  public synchronized void setSecondaryBottomPositionFraction(float fraction) {}

  /** Sets the subtitle font scale (shim: no-op). */
  public synchronized void setFontScale(float scale, boolean applyToSecondary) {}

  @Override
  public synchronized void close() {""",
        "LibassPlaybackSession subtitle style methods",
    )


# ------------------------------------------------- DefaultExtractorsFactory.java
def patch_extractors_factory(media3: Path) -> None:
    path = (
        media3
        / "libraries/extractor/src/main/java/androidx/media3/extractor/DefaultExtractorsFactory.java"
    )
    replace_once(
        path,
        "import java.util.Map;",
        "import java.util.Map;\nimport java.util.UUID;",
        "DefaultExtractorsFactory import UUID",
    )
    replace_once(
        path,
        """  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setMatroskaExtractorFlags(
      @MatroskaExtractor.Flags int flags) {
    this.matroskaFlags = flags;
    return this;
  }
""",
        """  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setMatroskaExtractorFlags(
      @MatroskaExtractor.Flags int flags) {
    this.matroskaFlags = flags;
    return this;
  }

  /**
   * Sets Matroska extractor flags for HTTP sources only (FongMi extension shim: applies
   * the flags to all sources).
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setMatroskaExtractorFlagsForHttpSources(
      @MatroskaExtractor.Flags int flags) {
    return setMatroskaExtractorFlags(flags);
  }

  /**
   * Sets ClearKey key material (kid -> key) used to decrypt MP4 content without a DRM
   * session (FongMi extension shim: accepted but not applied).
   */
  @CanIgnoreReturnValue
  public synchronized DefaultExtractorsFactory setMp4ClearKeys(Map<UUID, byte[]> clearKeys) {
    return this;
  }
""",
        "DefaultExtractorsFactory FongMi extensions",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--media3", required=True, help="FongMi/media checkout dir")
    parser.add_argument(
        "--media3-ass", required=True, help="FongMi/media (libass) checkout dir"
    )
    args = parser.parse_args()
    media3 = Path(args.media3)
    media3_ass = Path(args.media3_ass)
    for d in (media3, media3_ass):
        if not (d / "libraries").is_dir():
            raise SystemExit(f"not a media checkout (no libraries/): {d}")
    copy_stubs(HERE / "stubs" / "media3", media3)
    copy_stubs(HERE / "stubs" / "media3-ass", media3_ass)
    copy_extra_shims_for_ass(media3_ass)
    patch_player(media3)
    patch_player_view(media3)
    patch_mpv_player(media3)
    patch_libass_session(media3_ass)
    patch_extractors_factory(media3)
    print("OK: all shims installed and patches applied")
    return 0


if __name__ == "__main__":
    sys.exit(main())
