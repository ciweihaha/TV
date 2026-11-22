/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Reads the family name out of a font file (TTF/OTF 'name' table, nameID 1). The real
 * implementation uses FreeType through JNI; this pure-Java implementation covers the
 * TrueType/OpenType files users install, and falls back to the file name otherwise.
 *
 * listFaces enumerates the faces in a file: a single entry for TTF/OTF, one per font
 * in a TTC/OTC collection ('ttcf'), reading nameID 1 (family) and nameID 2 (style)
 * out of each face's own 'name' table.
 */
package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class LibassFontFile {

  private LibassFontFile() {}

  /** One face inside a font file: a single font, or one entry of a TTC/OTC collection. */
  public static final class Face {

    public final int index;
    public final boolean inCollection;
    public final String familyName;
    @Nullable public final String styleName;

    public Face(int index, boolean inCollection, String familyName, @Nullable String styleName) {
      this.index = index;
      this.inCollection = inCollection;
      this.familyName = familyName;
      this.styleName = styleName;
    }
  }

  /** Returns the family name of a font file (nameID 1), or the file name stem. */
  public static String getFamilyName(File file) throws IOException {
    List<Face> faces = listFaces(file);
    if (!faces.isEmpty()) return faces.get(0).familyName;
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  /**
   * Returns one {@link Face} per font in the file, or an empty list when the file is not a
   * readable TrueType/OpenType font or collection.
   */
  public static List<Face> listFaces(File file) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      if (raf.length() < 12) return List.of();
      int tag = raf.readInt();
      if (tag == 0x74746366 /* ttcf */) {
        raf.readInt(); // version
        int numFonts = raf.readInt();
        List<Face> faces = new ArrayList<>();
        for (int i = 0; i < numFonts; i++) {
          raf.seek(12L + i * 4L);
          long offset = raf.readInt() & 0xffffffffL;
          String[] names = readNames(raf, offset, file.getName());
          if (names == null) continue;
          faces.add(new Face(i, true, names[0], names[1]));
        }
        return List.copyOf(faces);
      }
      if (tag != 0x00010000 && tag != 0x4F54544F /* OTTO */
          && tag != 0x74727565 /* true */) {
        return List.of();
      }
      String[] names = readNames(raf, 0, file.getName());
      if (names == null) return List.of();
      return List.of(new Face(0, false, names[0], names[1]));
    }
  }

  /**
   * Reads nameID 1 (family) and nameID 2 (style) out of the 'name' table of the font whose
   * offset table starts at {@code baseOffset}. Returns {@code null} when there is no usable
   * 'name' table; the family falls back to the file name stem, the style may be {@code null}.
   */
  @Nullable
  private static String[] readNames(RandomAccessFile raf, long baseOffset, String fileName)
      throws IOException {
    raf.seek(baseOffset);
    int sfntVersion = raf.readInt();
    if (sfntVersion != 0x00010000 && sfntVersion != 0x4F54544F /* OTTO */
        && sfntVersion != 0x74727565 /* true */) {
      return null;
    }
    int numTables = raf.readUnsignedShort();
    long nameOffset = -1;
    long nameLength = -1;
    for (int i = 0; i < numTables; i++) {
      raf.seek(baseOffset + 12L + i * 16L);
      int tag = raf.readInt();
      raf.skipBytes(4);
      long offset = raf.readInt() & 0xffffffffL;
      long length = raf.readInt() & 0xffffffffL;
      if (tag == 0x6E616D65 /* name */) {
        // Table offsets inside a collection face are relative to that face's offset table.
        nameOffset = baseOffset + offset;
        nameLength = length;
        break;
      }
    }
    if (nameOffset < 0) return null;
    raf.seek(nameOffset);
    int format = raf.readUnsignedShort();
    int count = raf.readUnsignedShort();
    long stringOffset = raf.readUnsignedShort();
    String family = null;
    String familyFallback = null;
    String style = null;
    String styleFallback = null;
    for (int i = 0; i < count; i++) {
      raf.seek(nameOffset + 6L + i * 12L);
      int platformId = raf.readUnsignedShort();
      int encodingId = raf.readUnsignedShort();
      int languageId = raf.readUnsignedShort();
      int nameId = raf.readUnsignedShort();
      int length = raf.readUnsignedShort();
      int offset = raf.readUnsignedShort();
      if ((nameId != 1 && nameId != 2) || length <= 0) continue;
      long pos = nameOffset + stringOffset + offset;
      if (pos + length > nameOffset + nameLength) continue;
      byte[] bytes = new byte[length];
      raf.seek(pos);
      raf.readFully(bytes);
      String value = decode(platformId, encodingId, bytes);
      if (value == null || value.isEmpty()) continue;
      // Prefer US English; keep the first usable name as fallback.
      if (nameId == 1) {
        if (languageId == 0x0409) family = value;
        else if (familyFallback == null) familyFallback = value;
      } else {
        if (languageId == 0x0409) style = value;
        else if (styleFallback == null) styleFallback = value;
      }
    }
    if (family == null) family = familyFallback;
    if (family == null || family.isEmpty()) {
      int dot = fileName.lastIndexOf('.');
      family = dot > 0 ? fileName.substring(0, dot) : fileName;
    }
    if (style == null) style = styleFallback;
    return new String[] {family, style};
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
