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
    replace_once(
        path,
        "  /** {@link #getCurrentMediaEditions()} changed. */\n  @UnstableApi int EVENT_MEDIA_EDITIONS_CHANGED = 32;",
        """  /** {@link #getCurrentMediaEditions()} changed. */
  @UnstableApi int EVENT_MEDIA_EDITIONS_CHANGED = 32;

  /**
   * The disc-menu state changed.
   *
   * <p>FongMi extension (shim): never fired by the stock player; value 33 is the next
   * free event index after {@link #EVENT_MEDIA_EDITIONS_CHANGED}.
   */
  @UnstableApi int EVENT_DISC_MENU_STATE_CHANGED = 33;""",
        "Player.EVENT_DISC_MENU_STATE_CHANGED",
    )
    replace_once(
        path,
        """    EVENT_MEDIA_CHAPTERS_CHANGED,
    EVENT_MEDIA_EDITIONS_CHANGED
  })""",
        """    EVENT_MEDIA_CHAPTERS_CHANGED,
    EVENT_MEDIA_EDITIONS_CHANGED,
    EVENT_DISC_MENU_STATE_CHANGED
  })""",
        "Player @Event IntDef entry",
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
        "import android.graphics.Rect;",
        "import android.graphics.Rect;\nimport android.graphics.RectF;",
        "PlayerView import RectF",
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

  /**
   * Animates the video transform to the target over the given duration (ms).
   *
   * <p>FongMi extension (round 103): animates the video surface view with a
   * ViewPropertyAnimator; the stored transform is updated to the target
   * immediately.
   */
  @UnstableApi
  public void animateVideoTransform(VideoTransform target, int durationMs) {
    if (target == null) {
      target = VideoTransform.DEFAULT;
    }
    this.videoTransform = target;
    View surfaceView = getVideoSurfaceView();
    if (surfaceView != null) {
      surfaceView
          .animate()
          .scaleX(target.scaleX)
          .scaleY(target.scaleY)
          .translationX(target.panX)
          .translationY(target.panY)
          .setDuration(durationMs)
          .start();
    }
  }

  /** Cancels a running video-transform animation (FongMi extension, round 103). */
  @UnstableApi
  public void finishVideoTransformAnimation() {
    View surfaceView = getVideoSurfaceView();
    if (surfaceView != null) {
      surfaceView.animate().cancel();
    }
  }

  /**
   * Returns the bounds of the video content within this view.
   *
   * <p>FongMi extension (round 103): the surface view's bounds relative to this
   * view; empty when there is no surface view (callers fall back to the full
   * view size).
   */
  @UnstableApi
  public RectF getVideoBounds() {
    View surfaceView = getVideoSurfaceView();
    if (surfaceView == null) {
      return new RectF();
    }
    return new RectF(
        surfaceView.getLeft(),
        surfaceView.getTop(),
        surfaceView.getRight(),
        surfaceView.getBottom());
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
    # Round 101: DataSource (disc data source factory) and File (startup scripts).
    replace_once(
        path,
        "import androidx.media3.common.TrackSelectionParameters;",
        "import androidx.media3.common.TrackSelectionParameters;\nimport androidx.media3.datasource.DataSource;",
        "MpvPlayer import DataSource",
    )
    replace_once(
        path,
        "import java.util.List;",
        "import java.io.File;\nimport java.util.List;",
        "MpvPlayer import File",
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
    # Round 101: script/log/audio/motion-interpolation APIs the TV app calls.
    # Anchored on the round-96 extension block (supportsPointer) so this runs
    # after the first patch in the same install.
    replace_once(
        path,
        """  @Override
  public boolean supportsPointer() {
    return false;
  }""",
        """  @Override
  public boolean supportsPointer() {
    return false;
  }

  // ---- FongMi extensions, part 2: script/log/audio/motion APIs (round 101) ----

  /** Listener for mpv log messages (FongMi extension shim). */
  public interface LogListener {
    void onLogMessage(String prefix, int level, String text);
  }

  /** Callback invoked when an asynchronously loaded script finishes (FongMi extension shim). */
  public interface ScriptLoadCallback {
    void onLoaded(boolean success, long clientId);
  }

  /** Listener notified when an audio filter is applied (FongMi extension shim). */
  public interface AudioFilterListener {
    void onAudioFilterApplied(boolean success);
  }

  private boolean verboseLoggingEnabled;
  @Nullable private DataSource.Factory discDataSourceFactory;
  private int motionInterpolationFps;

  /** Enables verbose mpv logging (shim: stored only). */
  public void setVerboseLoggingEnabled(boolean enabled) {
    this.verboseLoggingEnabled = enabled;
  }

  /** Sets the data source factory used for disc playback (shim: stored only). */
  public void setDiscDataSourceFactory(@Nullable DataSource.Factory factory) {
    this.discDataSourceFactory = factory;
  }

  /** Resets the secondary text track selection override (shim: no-op). */
  public void resetSecondaryTextTrackSelection() {}

  /**
   * Sets the scripts loaded at startup.
   *
   * <p>FongMi extension (shim): always returns false (scripts are not applied); the
   * TV app marks every startup script with an error status.
   */
  public boolean setStartupScripts(List<File> scripts) {
    return false;
  }

  /**
   * Loads a script asynchronously.
   *
   * <p>FongMi extension (shim): always returns false (scripts are not loaded).
   */
  public boolean loadScript(String path, @Nullable ScriptLoadCallback callback) {
    return false;
  }

  /**
   * Returns the JSON status data of a startup script.
   *
   * <p>FongMi extension (shim): always returns null.
   */
  public @Nullable String getScriptData(String statusKey) {
    return null;
  }

  /**
   * Returns the input bindings as JSON.
   *
   * <p>FongMi extension (shim): always returns null.
   */
  public @Nullable String getInputBindings() {
    return null;
  }

  /** Unloads a previously loaded script (shim: no-op). */
  public void unloadScript(long clientId) {}

  /**
   * Runs a script binding.
   *
   * <p>FongMi extension (shim): always returns false (bindings are not run).
   */
  public boolean runScriptBinding(String binding) {
    return false;
  }

  /** Sets the log listener (shim: never called). */
  public void setLogListener(int level, @Nullable LogListener listener) {}

  /** Sets the audio-filter listener (shim: never called). */
  public void setAudioFilterListener(@Nullable AudioFilterListener listener) {}

  /**
   * Sets the motion-interpolation frame rate.
   *
   * <p>FongMi extension (shim): the value is stored and reported back, but no
   * interpolation is performed.
   */
  public ListenableFuture<Void> setMotionInterpolationFps(int fps) {
    this.motionInterpolationFps = fps;
    return Futures.immediateVoidFuture();
  }

  /** Returns the motion-interpolation frame rate set by {@link #setMotionInterpolationFps}. */
  public int getMotionInterpolationFps() {
    return motionInterpolationFps;
  }

  /** Returns whether motion interpolation is supported (shim: always false). */
  public boolean isMotionInterpolationSupported() {
    return false;
  }

  /** Returns whether a motion-interpolation update is pending (shim: always false). */
  public boolean isMotionInterpolationUpdatePending() {
    return false;
  }

  /** Returns the effective motion-interpolation target frame rate (shim: the set value). */
  public float getMotionInterpolationEffectiveTargetFps() {
    return (float) motionInterpolationFps;
  }

  /** Adds a motion-interpolation listener (shim: never called). */
  public void addMotionInterpolationListener(Runnable listener) {}

  /** Removes a motion-interpolation listener (shim: no-op). */
  public void removeMotionInterpolationListener(Runnable listener) {}""",
        "MpvPlayer round-101 extension APIs",
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


# ------------------------------------------------- MpvPlayerConfig.java (round 101)
def patch_mpv_player_config(media3: Path) -> None:
    path = (
        media3
        / "libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/MpvPlayerConfig.java"
    )
    replace_once(
        path,
        "import androidx.media3.common.DolbyVisionOutputPolicy;",
        "import androidx.media3.common.DolbyVisionOutputPolicy;\nimport androidx.media3.datasource.DataSource;",
        "MpvPlayerConfig import DataSource",
    )
    replace_once(
        path,
        "  private final boolean videoSharpnessSupported;\n",
        "  private final boolean videoSharpnessSupported;\n"
        "  @Nullable private final DataSource.Factory dataSourceFactory;\n"
        "  private final int motionInterpolationFps;\n",
        "MpvPlayerConfig fields",
    )
    replace_once(
        path,
        "    this.videoSharpnessSupported = builder.videoSharpnessSupported;\n",
        "    this.videoSharpnessSupported = builder.videoSharpnessSupported;\n"
        "    this.dataSourceFactory = builder.dataSourceFactory;\n"
        "    this.motionInterpolationFps = builder.motionInterpolationFps;\n",
        "MpvPlayerConfig constructor",
    )
    replace_once(
        path,
        """  @RestrictTo(LIBRARY_GROUP)
  public boolean isVideoSharpnessSupported() {
    return videoSharpnessSupported;
  }
""",
        """  @RestrictTo(LIBRARY_GROUP)
  public boolean isVideoSharpnessSupported() {
    return videoSharpnessSupported;
  }

  @Nullable
  @RestrictTo(LIBRARY_GROUP)
  public DataSource.Factory getDataSourceFactory() {
    return dataSourceFactory;
  }

  @RestrictTo(LIBRARY_GROUP)
  public int getMotionInterpolationFps() {
    return motionInterpolationFps;
  }
""",
        "MpvPlayerConfig getters",
    )
    replace_once(
        path,
        "    private boolean videoSharpnessSupported = true;\n",
        "    private boolean videoSharpnessSupported = true;\n"
        "    @Nullable private DataSource.Factory dataSourceFactory;\n"
        "    private int motionInterpolationFps;\n",
        "MpvPlayerConfig.Builder fields",
    )
    replace_once(
        path,
        "    public MpvPlayerConfig build() {",
        """    @CanIgnoreReturnValue
    public Builder setDataSourceFactory(@Nullable DataSource.Factory dataSourceFactory) {
      checkState(!buildCalled);
      this.dataSourceFactory = dataSourceFactory;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setMotionInterpolationFps(int motionInterpolationFps) {
      checkState(!buildCalled);
      this.motionInterpolationFps = motionInterpolationFps;
      return this;
    }

    public MpvPlayerConfig build() {""",
        "MpvPlayerConfig.Builder setters",
    )
    # Round 102: setDefaultFontDirectories(File, File) was hidden behind javac's
    # 100-error cap in run 42534171249 and only surfaced in run 42536151812
    # (MpvUtil.java:142). The fork-only API stores the two font directories;
    # the stock player does not consume them (same pattern as the round-101
    # setDataSourceFactory/setMotionInterpolationFps setters).
    replace_once(
        path,
        "  @Nullable private final DataSource.Factory dataSourceFactory;\n"
        "  private final int motionInterpolationFps;\n",
        "  @Nullable private final DataSource.Factory dataSourceFactory;\n"
        "  private final int motionInterpolationFps;\n"
        "  @Nullable private final File defaultFontsDirectory;\n"
        "  @Nullable private final File defaultSystemFontsDirectory;\n",
        "MpvPlayerConfig default font directory fields",
    )
    replace_once(
        path,
        "    this.dataSourceFactory = builder.dataSourceFactory;\n"
        "    this.motionInterpolationFps = builder.motionInterpolationFps;\n",
        "    this.dataSourceFactory = builder.dataSourceFactory;\n"
        "    this.motionInterpolationFps = builder.motionInterpolationFps;\n"
        "    this.defaultFontsDirectory = builder.defaultFontsDirectory;\n"
        "    this.defaultSystemFontsDirectory = builder.defaultSystemFontsDirectory;\n",
        "MpvPlayerConfig default font directory constructor",
    )
    replace_once(
        path,
        """  @RestrictTo(LIBRARY_GROUP)
  public int getMotionInterpolationFps() {
    return motionInterpolationFps;
  }
""",
        """  @RestrictTo(LIBRARY_GROUP)
  public int getMotionInterpolationFps() {
    return motionInterpolationFps;
  }

  @Nullable
  @RestrictTo(LIBRARY_GROUP)
  public File getDefaultFontsDirectory() {
    return defaultFontsDirectory;
  }

  @Nullable
  @RestrictTo(LIBRARY_GROUP)
  public File getDefaultSystemFontsDirectory() {
    return defaultSystemFontsDirectory;
  }
""",
        "MpvPlayerConfig default font directory getters",
    )
    replace_once(
        path,
        "    @Nullable private DataSource.Factory dataSourceFactory;\n"
        "    private int motionInterpolationFps;\n",
        "    @Nullable private DataSource.Factory dataSourceFactory;\n"
        "    private int motionInterpolationFps;\n"
        "    @Nullable private File defaultFontsDirectory;\n"
        "    @Nullable private File defaultSystemFontsDirectory;\n",
        "MpvPlayerConfig.Builder default font directory fields",
    )
    replace_once(
        path,
        """    @CanIgnoreReturnValue
    public Builder setMotionInterpolationFps(int motionInterpolationFps) {
      checkState(!buildCalled);
      this.motionInterpolationFps = motionInterpolationFps;
      return this;
    }

    public MpvPlayerConfig build() {""",
        """    @CanIgnoreReturnValue
    public Builder setMotionInterpolationFps(int motionInterpolationFps) {
      checkState(!buildCalled);
      this.motionInterpolationFps = motionInterpolationFps;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDefaultFontDirectories(
        @Nullable File defaultFontsDirectory, @Nullable File defaultSystemFontsDirectory) {
      checkState(!buildCalled);
      this.defaultFontsDirectory = defaultFontsDirectory;
      this.defaultSystemFontsDirectory = defaultSystemFontsDirectory;
      return this;
    }

    public MpvPlayerConfig build() {""",
        "MpvPlayerConfig.Builder setDefaultFontDirectories",
    )


# ------------------------------------------------- MpvSubtitleOptions.java (round 101)
def patch_mpv_subtitle_options(media3: Path) -> None:
    path = (
        media3
        / "libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/MpvSubtitleOptions.java"
    )
    replace_once(
        path,
        "import androidx.annotation.RestrictTo;",
        "import androidx.annotation.Nullable;\nimport androidx.annotation.RestrictTo;",
        "MpvSubtitleOptions import Nullable",
    )
    replace_once(
        path,
        "  private final boolean secondaryOverrideAssStyles;\n",
        "  private final boolean secondaryOverrideAssStyles;\n"
        "  @Nullable private final String fontsDirectory;\n"
        "  private final float verticalOffset;\n"
        "  private final float bitmapSubtitleBrightness;\n"
        "  private final double secondaryVerticalOffset;\n"
        "  @Nullable private final String fontFamily;\n",
        "MpvSubtitleOptions fields",
    )
    replace_once(
        path,
        "    secondaryOverrideAssStyles = builder.secondaryOverrideAssStyles;\n",
        "    secondaryOverrideAssStyles = builder.secondaryOverrideAssStyles;\n"
        "    fontsDirectory = builder.fontsDirectory;\n"
        "    verticalOffset = builder.verticalOffset;\n"
        "    bitmapSubtitleBrightness = builder.bitmapSubtitleBrightness;\n"
        "    secondaryVerticalOffset = builder.secondaryVerticalOffset;\n"
        "    fontFamily = builder.fontFamily;\n",
        "MpvSubtitleOptions constructor",
    )
    replace_once(
        path,
        """  public boolean shouldOverrideSecondaryAssStyles() {
    return secondaryOverrideAssStyles;
  }
""",
        """  public boolean shouldOverrideSecondaryAssStyles() {
    return secondaryOverrideAssStyles;
  }

  @Nullable
  public String getFontsDirectory() {
    return fontsDirectory;
  }

  public float getVerticalOffset() {
    return verticalOffset;
  }

  public float getBitmapSubtitleBrightness() {
    return bitmapSubtitleBrightness;
  }

  public double getSecondaryVerticalOffset() {
    return secondaryVerticalOffset;
  }

  @Nullable
  public String getFontFamily() {
    return fontFamily;
  }
""",
        "MpvSubtitleOptions getters",
    )
    replace_once(
        path,
        "    private boolean secondaryOverrideAssStyles;\n",
        "    private boolean secondaryOverrideAssStyles;\n"
        "    @Nullable private String fontsDirectory;\n"
        "    private float verticalOffset;\n"
        "    private float bitmapSubtitleBrightness;\n"
        "    private double secondaryVerticalOffset;\n"
        "    @Nullable private String fontFamily;\n",
        "MpvSubtitleOptions.Builder fields",
    )
    replace_once(
        path,
        "    public MpvSubtitleOptions build() {",
        """    @CanIgnoreReturnValue
    public Builder setFontsDirectory(@Nullable String fontsDirectory) {
      checkState(!buildCalled);
      this.fontsDirectory = fontsDirectory;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setVerticalOffset(float verticalOffset) {
      checkState(!buildCalled);
      this.verticalOffset = verticalOffset;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setBitmapSubtitleBrightness(float bitmapSubtitleBrightness) {
      checkState(!buildCalled);
      this.bitmapSubtitleBrightness = bitmapSubtitleBrightness;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSecondarySubtitlePosition(double secondaryPosition) {
      checkState(!buildCalled);
      this.secondaryPosition = secondaryPosition;
      secondarySubtitleConfigured = true;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSecondaryVerticalOffset(double secondaryVerticalOffset) {
      checkState(!buildCalled);
      this.secondaryVerticalOffset = secondaryVerticalOffset;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSecondaryAssStyleOverride(boolean secondaryOverrideAssStyles) {
      checkState(!buildCalled);
      this.secondaryOverrideAssStyles = secondaryOverrideAssStyles;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setFontFamily(@Nullable String fontFamily) {
      checkState(!buildCalled);
      this.fontFamily = fontFamily;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDefaultStyle(
        int foregroundColor,
        int backgroundColor,
        int edgeType,
        int edgeColor,
        float edgeSize,
        float shadowOffset) {
      checkState(!buildCalled);
      style = STYLE_CUSTOM;
      this.foregroundColor = foregroundColor;
      this.backgroundColor = backgroundColor;
      this.edgeType = edgeType;
      this.edgeColor = edgeColor;
      this.edgeSize = edgeSize;
      this.shadowOffset = shadowOffset;
      return this;
    }

    public MpvSubtitleOptions build() {""",
        "MpvSubtitleOptions.Builder setters",
    )


# ------------------------------------------------- MpvLibrary.java (round 101)
def patch_mpv_library(media3: Path) -> None:
    path = (
        media3
        / "libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/MpvLibrary.java"
    )
    replace_once(
        path,
        "import androidx.media3.common.MediaLibraryInfo;",
        "import androidx.annotation.Nullable;\n"
        "import androidx.media3.common.MediaLibraryInfo;\n"
        "import androidx.media3.common.util.LibraryLoader;\n"
        "import java.util.function.BooleanSupplier;",
        "MpvLibrary imports",
    )
    replace_once(
        path,
        """  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }""",
        """  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  @Nullable private static BooleanSupplier libraryLoader;

  /**
   * Overrides how the mpv native libraries are loaded.
   *
   * <p>FongMi extension (shim): the loader is stored but the stock {@link LibraryLoader}
   * is still used to load the libraries. The parameter is a {@link BooleanSupplier}
   * because the TV app passes {@code MpvUtil::loadRuntime} (a {@code () -> boolean}
   * method reference), which cannot be a {@link LibraryLoader} (a class, not an
   * interface).
   */
  public static void setLibraryLoader(@Nullable BooleanSupplier loader) {
    libraryLoader = loader;
  }""",
        "MpvLibrary setLibraryLoader",
    )


# ------------------------------------------------- MpvAudioFilter.java (round 101)
def patch_mpv_audio_filter(media3: Path) -> None:
    path = (
        media3
        / "libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/audio/MpvAudioFilter.java"
    )
    replace_once(
        path,
        """    public MpvAudioFilter build() {""",
        """    /**
     * Adds dynamic normalization (ffmpeg {@code dynaudnorm} filter).
     *
     * <p>FongMi extension (shim approximation): frame length in seconds is converted
     * to milliseconds for the filter's {@code f} option; {@code maxGain} maps to the
     * filter's peak limit {@code p}.
     */
    @CanIgnoreReturnValue
    public Builder addDynamicNormalization(String id, double frameLengthSeconds, double maxGain) {
      checkState(!buildCalled);
      String target = target("dynaudnorm", id);
      String filter =
          Util.formatInvariant(
              "%s=f=%s:p=%s",
              target,
              decimal(frameLengthSeconds * 1000.0, 0),
              decimal(maxGain, 1));
      return addFilter(filter, filter);
    }

    public MpvAudioFilter build() {""",
        "MpvAudioFilter.Builder addDynamicNormalization",
    )


# ------------------------------------------------- MatroskaExtractor.java (round 101)
def patch_matroska_extractor(media3: Path) -> None:
    path = (
        media3
        / "libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java"
    )
    replace_once(
        path,
        "  public static final int FLAG_EMIT_RAW_SUBTITLE_DATA = 1 << 1; // 2",
        """  public static final int FLAG_EMIT_RAW_SUBTITLE_DATA = 1 << 1; // 2

  /**
   * Flag to defer seeking for cues until the cues element is actually needed.
   *
   * <p>FongMi extension (shim): defined for API compatibility; the stock extractor
   * ignores this flag.
   */
  public static final int FLAG_DEFER_SEEK_FOR_CUES = 1 << 2; // 4""",
        "MatroskaExtractor FLAG_DEFER_SEEK_FOR_CUES",
    )
    replace_once(
        path,
        "      value = {FLAG_DISABLE_SEEK_FOR_CUES, FLAG_EMIT_RAW_SUBTITLE_DATA})",
        "      value = {\n"
        "        FLAG_DISABLE_SEEK_FOR_CUES,\n"
        "        FLAG_EMIT_RAW_SUBTITLE_DATA,\n"
        "        FLAG_DEFER_SEEK_FOR_CUES\n"
        "      })",
        "MatroskaExtractor @IntDef value list",
    )


# ------------------------------------------------- PlayerSeekView.java (round 101)
def patch_player_seek_view(media3: Path) -> None:
    path = (
        media3 / "libraries/ui/src/main/java/androidx/media3/ui/PlayerSeekView.java"
    )
    replace_once(
        path,
        "import java.util.Locale;",
        "import java.util.Locale;\nimport java.util.function.LongSupplier;",
        "PlayerSeekView import LongSupplier",
    )
    replace_once(
        path,
        """  private long currentDuration;
  private long currentWindowOffset;""",
        """  private long currentDuration;
  private long currentWindowOffset;
  @Nullable private LongSupplier durationSupplier;""",
        "PlayerSeekView durationSupplier field",
    )
    replace_once(
        path,
        """  /** Returns the bound {@link TimeBar}. */""",
        """  /**
   * Sets a supplier for the duration shown in the seek view, overriding the
   * duration from the player timeline.
   *
   * <p>FongMi extension: the TV app passes its active playback duration
   * ({@code PlaybackActivity::getActivePlaybackDuration}).
   */
  public void setDurationSupplier(@Nullable LongSupplier durationSupplier) {
    this.durationSupplier = durationSupplier;
    if (attached) {
      updateTimeline();
    }
  }

  /** Returns the bound {@link TimeBar}. */""",
        "PlayerSeekView setDurationSupplier",
    )
    replace_once(
        path,
        """    } else if (player.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      long durationMs = player.getContentDuration();
      if (durationMs != C.TIME_UNSET) {
        durationUs = msToUs(durationMs);
      }
    }
    updateDuration(Util.usToMs(durationUs), adGroupCount);""",
        """    } else if (player.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      long durationMs = player.getContentDuration();
      if (durationMs != C.TIME_UNSET) {
        durationUs = msToUs(durationMs);
      }
    }
    if (durationSupplier != null) {
      long suppliedDurationMs = durationSupplier.getAsLong();
      if (suppliedDurationMs != C.TIME_UNSET && suppliedDurationMs >= 0) {
        durationUs = msToUs(suppliedDurationMs);
      }
    }
    updateDuration(Util.usToMs(durationUs), adGroupCount);""",
        "PlayerSeekView updateTimeline uses durationSupplier",
    )


# ------------------------------------------------- SubtitleView.java (round 101)
def patch_subtitle_view(media3: Path) -> None:
    path = media3 / "libraries/ui/src/main/java/androidx/media3/ui/SubtitleView.java"
    replace_once(
        path,
        """  public void setBottomPosition(float bottomPosition) {
    this.bottomPosition = bottomPosition;
    updateOutput();
  }""",
        """  public void setBottomPosition(float bottomPosition) {
    this.bottomPosition = bottomPosition;
    updateOutput();
  }

  /**
   * Sets the brightness multiplier applied to bitmap subtitles.
   *
   * <p>FongMi extension (shim): accepted but not applied.
   */
  public void setBitmapSubtitleBrightness(float bitmapSubtitleBrightness) {}""",
        "SubtitleView setBitmapSubtitleBrightness",
    )


# ------------------------------------------------- PreloadManagerListener.java (round 101)
def patch_preload_manager_listener(media3: Path) -> None:
    path = (
        media3
        / "libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/preload/PreloadManagerListener.java"
    )
    replace_once(
        path,
        """  /** Called when an {@linkplain PreloadException error} occurs. */
  default void onError(PreloadException exception) {}""",
        """  /** Called when an {@linkplain PreloadException error} occurs. */
  default void onError(PreloadException exception) {}

  /**
   * Called when preloading of the given {@link MediaItem} is paused.
   *
   * <p>FongMi extension (shim): never called by the stock preload manager.
   */
  default void onPreloadPaused(MediaItem mediaItem) {}""",
        "PreloadManagerListener onPreloadPaused",
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
    patch_mpv_player_config(media3)
    patch_mpv_subtitle_options(media3)
    patch_mpv_library(media3)
    patch_mpv_audio_filter(media3)
    patch_matroska_extractor(media3)
    patch_player_seek_view(media3)
    patch_subtitle_view(media3)
    patch_preload_manager_listener(media3)
    patch_libass_session(media3_ass)
    patch_extractors_factory(media3)
    print("OK: all shims installed and patches applied")
    return 0


if __name__ == "__main__":
    sys.exit(main())
