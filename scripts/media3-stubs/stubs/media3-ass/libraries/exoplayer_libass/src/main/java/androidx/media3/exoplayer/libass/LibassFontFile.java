/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Reads the family name out of a font file (TTF/OTF 'name' table, nameID 1). The real
 * implementation uses FreeType through JNI; this pure-Java implementation covers the
 * TrueType/OpenType files users install, and falls back to the file name otherwise.
 */
package androidx.media3.exoplayer.libass;

import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

@UnstableApi
public final class LibassFontFile {

  private LibassFontFile() {}

  /** Returns the family name of a font file (nameID 1), or the file name stem. */
  public static String getFamilyName(File file) throws IOException {
    String parsed = parseFamilyName(file);
    if (parsed != null && !parsed.isEmpty()) return parsed;
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static String parseFamilyName(File file) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      if (raf.length() < 12) return null;
      int sfntVersion = raf.readInt();
      if (sfntVersion != 0x00010000 && sfntVersion != 0x4F54544F /* OTTO */
          && sfntVersion != 0x74727565 /* true */) {
        return null;
      }
      int numTables = raf.readUnsignedShort();
      long nameOffset = -1;
      long nameLength = -1;
      for (int i = 0; i < numTables; i++) {
        raf.seek(12L + i * 16L);
        int tag = raf.readInt();
        raf.skipBytes(4);
        long offset = raf.readInt() & 0xffffffffL;
        long length = raf.readInt() & 0xffffffffL;
        if (tag == 0x6E616D65 /* name */) {
          nameOffset = offset;
          nameLength = length;
          break;
        }
      }
      if (nameOffset < 0) return null;
      raf.seek(nameOffset);
      int format = raf.readUnsignedShort();
      int count = raf.readUnsignedShort();
      long stringOffset = raf.readUnsignedShort();
      String fallback = null;
      for (int i = 0; i < count; i++) {
        raf.seek(nameOffset + 6L + i * 12L);
        int platformId = raf.readUnsignedShort();
        int encodingId = raf.readUnsignedShort();
        int languageId = raf.readUnsignedShort();
        int nameId = raf.readUnsignedShort();
        int length = raf.readUnsignedShort();
        int offset = raf.readUnsignedShort();
        if (nameId != 1 || length <= 0) continue;
        long pos = nameOffset + stringOffset + offset;
        if (pos + length > nameOffset + nameLength) continue;
        byte[] bytes = new byte[length];
        raf.seek(pos);
        raf.readFully(bytes);
        String value = decode(platformId, encodingId, bytes);
        if (value == null || value.isEmpty()) continue;
        // Prefer US English; keep the first usable name as fallback.
        if (languageId == 0x0409) return value;
        if (fallback == null) fallback = value;
      }
      return fallback;
    }
  }

  private static String decode(int platformId, int encodingId, byte[] bytes) {
    try {
      if (platformId == 3 || platformId == 0) {
        // Windows / Unicode: UTF-16BE.
        return new String(bytes, "UTF-16BE").trim();
      }
      if (platformId == 1 && encodingId == 0) {
        // Mac Roman: approximate as ISO-8859-1 for the ASCII range used by family names.
        return new String(bytes, "ISO-8859-1").trim();
      }
      return new String(bytes, "UTF-16BE").trim();
    } catch (Exception e) {
      return null;
    }
  }
}
