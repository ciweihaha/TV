/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * MediaSource factory that resolves the content type of a URI before delegating to the
 * inner factory (e.g. ISO images, concatenated URLs). The shim delegates directly; the
 * inner DefaultMediaSourceFactory already sniffs content types through its extractors.
 */
package androidx.media3.exoplayer.source;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

@UnstableApi
public final class ContentTypeResolvingMediaSource {

  private ContentTypeResolvingMediaSource() {}

  public static final class Factory implements MediaSource.Factory {

    private final MediaSource.Factory factory;
    private final DataSource.Factory dataSourceFactory;

    public Factory(MediaSource.Factory factory, DataSource.Factory dataSourceFactory) {
      this.factory = factory;
      this.dataSourceFactory = dataSourceFactory;
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(
        @NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
      factory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(
        @NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      factory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return factory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
      return factory.createMediaSource(mediaItem);
    }
  }
}
