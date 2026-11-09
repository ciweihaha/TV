/*
 * Compatibility shim for the FongMi/TV fork (see DecoderMode.java).
 *
 * Loads subtitle content (the parsed lines shown by the subtitle-content dialog) from a
 * URI. Round 114: real implementation — downloads the file through the given data source
 * factory and parses it with the stock {@link DefaultSubtitleParserFactory} (SSA/ASS,
 * WebVTT, SubRip, TX3G, TTML, and image subtitles). Formats the factory does not support
 * still report {@link SubtitleContent#UNSUPPORTED}; normal subtitle rendering (through
 * the player's own subtitle track) is unaffected.
 */
package androidx.media3.exoplayer.text;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.SubtitleContent;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.mpvplayer.MpvSubtitleContentLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class DefaultSubtitleContentLoader implements MpvSubtitleContentLoader {

  private static final int BUFFER_SIZE = 8 * 1024;

  private final DataSource.Factory dataSourceFactory;
  private final SubtitleParser.Factory parserFactory;

  public DefaultSubtitleContentLoader(DataSource.Factory dataSourceFactory) {
    this(dataSourceFactory, new DefaultSubtitleParserFactory());
  }

  public DefaultSubtitleContentLoader(
      DataSource.Factory dataSourceFactory, SubtitleParser.Factory parserFactory) {
    this.dataSourceFactory = dataSourceFactory;
    this.parserFactory = parserFactory;
  }

  @Override
  public SubtitleContent load(MediaItem item, Uri uri, Format format) throws IOException {
    if (format == null || !parserFactory.supportsFormat(format)) {
      return SubtitleContent.UNSUPPORTED;
    }
    byte[] data = readFully(uri);
    SubtitleParser parser = parserFactory.create(format);
    List<SubtitleContent.Entry> entries = new ArrayList<>();
    parser.parse(
        data,
        SubtitleParser.OutputOptions.allCues(),
        cues -> {
          for (Cue cue : cues.cues) {
            boolean image = cue.bitmap != null;
            CharSequence text = cue.text;
            if (text == null && !image) {
              continue;
            }
            entries.add(
                new SubtitleContent.Entry(
                    cues.startTimeUs,
                    cues.endTimeUs,
                    text == null ? "" : text.toString(),
                    image));
          }
        });
    return new SubtitleContent(SubtitleContent.State.COMPLETE, entries);
  }

  private byte[] readFully(Uri uri) throws IOException {
    DataSource dataSource = dataSourceFactory.createDataSource();
    try {
      dataSource.open(new DataSpec(uri));
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = dataSource.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    } finally {
      dataSource.close();
    }
  }
}
