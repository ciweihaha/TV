/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Configuration for the libass subtitle renderer (fontconfig path, fonts directory,
 * default font family, render limits). The real LibassPlaybackSession reads these
 * values; the shim carries them so the app can construct a session.
 */
package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class LibassConfiguration {

  private final @Nullable String fontConfig;
  private final @Nullable String fontsDirectory;
  private final @Nullable String defaultFontFamily;
  private final int maximumRenderPixels;
  private final int maximumGlyphCount;
  private final int maximumBitmapCacheSizeMb;

  private LibassConfiguration(Builder builder) {
    this.fontConfig = builder.fontConfig;
    this.fontsDirectory = builder.fontsDirectory;
    this.defaultFontFamily = builder.defaultFontFamily;
    this.maximumRenderPixels = builder.maximumRenderPixels;
    this.maximumGlyphCount = builder.maximumGlyphCount;
    this.maximumBitmapCacheSizeMb = builder.maximumBitmapCacheSizeMb;
  }

  public @Nullable String getFontConfig() {
    return fontConfig;
  }

  public @Nullable String getFontsDirectory() {
    return fontsDirectory;
  }

  public @Nullable String getDefaultFontFamily() {
    return defaultFontFamily;
  }

  public int getMaximumRenderPixels() {
    return maximumRenderPixels;
  }

  public int getMaximumGlyphCount() {
    return maximumGlyphCount;
  }

  public int getMaximumBitmapCacheSizeMb() {
    return maximumBitmapCacheSizeMb;
  }

  public static final class Builder {
    private @Nullable String fontConfig;
    private @Nullable String fontsDirectory;
    private @Nullable String defaultFontFamily;
    private int maximumRenderPixels;
    private int maximumGlyphCount;
    private int maximumBitmapCacheSizeMb;

    public Builder setFontConfig(@Nullable String fontConfig) {
      this.fontConfig = fontConfig;
      return this;
    }

    public Builder setFontsDirectory(@Nullable String fontsDirectory) {
      this.fontsDirectory = fontsDirectory;
      return this;
    }

    public Builder setDefaultFontFamily(@Nullable String defaultFontFamily) {
      this.defaultFontFamily = defaultFontFamily;
      return this;
    }

    public Builder setMaximumRenderPixels(int maximumRenderPixels) {
      this.maximumRenderPixels = maximumRenderPixels;
      return this;
    }

    public Builder setMaximumGlyphCount(int maximumGlyphCount) {
      this.maximumGlyphCount = maximumGlyphCount;
      return this;
    }

    public Builder setMaximumBitmapCacheSizeMb(int maximumBitmapCacheSizeMb) {
      this.maximumBitmapCacheSizeMb = maximumBitmapCacheSizeMb;
      return this;
    }

    public LibassConfiguration build() {
      return new LibassConfiguration(this);
    }
  }
}
