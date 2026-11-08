/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Parses ClearKey license material out of a DRM configuration / license string. The result
 * is the key map the extractor factory uses to decrypt ClearKey MP4 content
 * (kid UUID -> raw key bytes), or an empty map when the material is not ClearKey.
 *
 * Supported license forms:
 * <ul>
 *   <li>ClearKey JSON: {"keys":[{"kty":"oct","kid":"base64url","k":"base64url"}]}</li>
 * </ul>
 */
package androidx.media3.common.util;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import android.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

@UnstableApi
public final class LocalClearKeyLicense {

  private LocalClearKeyLicense() {}

  /** Parses ClearKey JSON (or returns an empty map when {@code key} is not ClearKey JSON). */
  public static Map<UUID, byte[]> parse(@Nullable String key) {
    if (key == null) return Collections.emptyMap();
    String trimmed = key.trim();
    if (!trimmed.startsWith("{")) return Collections.emptyMap();
    try {
      JSONObject json = new JSONObject(trimmed);
      JSONArray keys = json.optJSONArray("keys");
      if (keys == null) return Collections.emptyMap();
      Map<UUID, byte[]> result = new LinkedHashMap<>();
      for (int i = 0; i < keys.length(); i++) {
        JSONObject entry = keys.optJSONObject(i);
        if (entry == null) continue;
        String kid = entry.optString("kid", null);
        String k = entry.optString("k", null);
        if (kid == null || k == null) continue;
        byte[] kidBytes = Base64.decode(kid, Base64.URL_SAFE);
        byte[] keyBytes = Base64.decode(k, Base64.URL_SAFE);
        if (kidBytes.length != 16 || keyBytes.length == 0) continue;
        result.put(toUuid(kidBytes), keyBytes);
      }
      return result;
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  /** Parses the ClearKey license URI of a DRM configuration, if any. */
  public static Map<UUID, byte[]> parse(@Nullable MediaItem.DrmConfiguration drmConfiguration) {
    if (drmConfiguration == null || drmConfiguration.licenseUri == null) {
      return Collections.emptyMap();
    }
    return parse(drmConfiguration.licenseUri.toString());
  }

  private static UUID toUuid(byte[] bytes) {
    long most = 0;
    long least = 0;
    for (int i = 0; i < 8; i++) most = (most << 8) | (bytes[i] & 0xff);
    for (int i = 8; i < 16; i++) least = (least << 8) | (bytes[i] & 0xff);
    return new UUID(most, least);
  }
}
