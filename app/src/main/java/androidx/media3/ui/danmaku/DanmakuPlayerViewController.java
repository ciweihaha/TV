/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Drives the danmaku (bullet comment) overlay on a PlayerView. The real implementation
 * fetches live danmaku, renders scrolling comments and exposes them to the content
 * dialog; the shim accepts the wiring and reports no comments, so danmaku stays
 * inactive while playback is unaffected.
 */
package androidx.media3.ui.danmaku;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import java.util.List;
import okhttp3.OkHttpClient;

@UnstableApi
public final class DanmakuPlayerViewController {

  public DanmakuPlayerViewController() {}

  /** The danmaku items currently known (empty in the shim). */
  public List<Danmaku> getItems() {
    return List.of();
  }

  /** Posts one danmaku item (no-op in the shim). */
  public void post(Danmaku item) {}

  public void bind(PlayerView playerView) {}

  public void setConfig(DanmakuConfig config) {}

  public void setOkHttpClient(OkHttpClient client) {}

  public void setEnabled(boolean enabled) {}

  public void setDataSource(@Nullable Uri uri) {}

  public void sendNow(String text) {}

  public void close() {}
}
