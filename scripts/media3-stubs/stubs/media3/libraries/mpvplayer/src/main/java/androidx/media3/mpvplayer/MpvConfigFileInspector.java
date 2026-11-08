/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Inspects the mpv configuration directory (mpv.conf) for options the app's UI manages
 * (e.g. the subtitle fonts directory). Pure file parsing, no native dependency.
 */
package androidx.media3.mpvplayer;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

@UnstableApi
public final class MpvConfigFileInspector {

  private final File mpvDirectory;

  public MpvConfigFileInspector(File mpvDirectory) {
    this.mpvDirectory = mpvDirectory;
  }

  /** Returns the subtitle fonts directory from mpv.conf ("sub-fonts-dir"), if set. */
  public @Nullable File readSubtitleFontsDirectory() {
    String content = readConfig();
    if (content == null) return null;
    for (String line : content.split("\n")) {
      String value = optionValue(line, "sub-fonts-dir");
      if (value != null && !value.isEmpty()) {
        File dir = new File(value);
        return dir.isAbsolute() ? dir : new File(mpvDirectory, value);
      }
    }
    return null;
  }

  /** Returns the option names present in the given mpv.conf content. */
  public Set<String> getEffectiveOptionNames(CharSequence content) {
    Set<String> names = new LinkedHashSet<>();
    if (content == null) return names;
    for (String line : content.toString().split("\n")) {
      String name = optionName(line);
      if (name != null) names.add(name);
    }
    return names;
  }

  private @Nullable String readConfig() {
    try {
      File config = new File(mpvDirectory, "mpv.conf");
      if (!config.isFile()) return null;
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (FileInputStream in = new FileInputStream(config)) {
        byte[] chunk = new byte[8192];
        int count;
        while ((count = in.read(chunk)) != -1) buffer.write(chunk, 0, count);
      }
      return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException | SecurityException e) {
      return null;
    }
  }

  private static @Nullable String optionValue(String line, String option) {
    String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;
    if (!trimmed.startsWith(option)) return null;
    String rest = trimmed.substring(option.length()).trim();
    if (!rest.startsWith("=")) return null;
    String value = rest.substring(1).trim();
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static @Nullable String optionName(String line) {
    String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;
    int end = trimmed.length();
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c == '=' || Character.isWhitespace(c)) {
        end = i;
        break;
      }
    }
    String name = trimmed.substring(0, end);
    return name.isEmpty() ? null : name;
  }
}
