/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Writes a fontconfig configuration file (fonts.conf) pointing at the app's subtitle
 * font directories, so libass can find user-installed fonts.
 */
package androidx.media3.exoplayer.libass;

import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@UnstableApi
public final class LibassFontConfig {

  private LibassFontConfig() {}

  /** Writes a fonts.conf listing {@code fontDirectories} (plus the cache dir). */
  public static void write(File output, File cacheDirectory, List<File> fontDirectories)
      throws IOException {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\"?>\n");
    xml.append("<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">\n");
    xml.append("<fontconfig>\n");
    if (cacheDirectory != null) {
      xml.append("  <cachedir>").append(escape(cacheDirectory.getAbsolutePath()))
          .append("</cachedir>\n");
    }
    if (fontDirectories != null) {
      for (File directory : fontDirectories) {
        if (directory != null) {
          xml.append("  <dir>").append(escape(directory.getAbsolutePath()))
              .append("</dir>\n");
        }
      }
    }
    xml.append("</fontconfig>\n");
    try (FileOutputStream out = new FileOutputStream(output)) {
      out.write(xml.toString().getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
