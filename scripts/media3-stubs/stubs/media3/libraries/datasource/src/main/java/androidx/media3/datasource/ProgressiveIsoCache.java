/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Wraps a data-source factory so ISO images can be served (progressively) from the disk
 * cache. The real implementation serves ISO sectors through the cache; the shim returns
 * the factory unchanged, so ISO files play as plain files (no disc navigation).
 */
package androidx.media3.datasource;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.cache.Cache;

@UnstableApi
public final class ProgressiveIsoCache {

  private ProgressiveIsoCache() {}

  public static DataSource.Factory withDiskCache(DataSource.Factory factory, Cache cache) {
    return factory;
  }
}
