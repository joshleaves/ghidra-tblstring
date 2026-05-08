/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import java.util.Objects;

/**
 * Stateless decoder for byte arrays using a parsed {@link TblTable}.
 *
 * <p>Decoding is greedy: at each byte offset, entries are checked in longest-key-first order, so
 * multi-byte keys take precedence over shorter overlapping keys. Bytes that do not match any table
 * entry are rendered according to {@link DecodeOptions}.
 */
public final class TblStringDecoder {
  private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

  /**
   * Immutable options controlling decoder fallback behavior.
   *
   * <p>The table itself defines successful matches. Options only affect unmatched bytes.
   */
  public static final class DecodeOptions {
    private final UnknownMode unknownMode;

    private DecodeOptions(UnknownMode unknownMode) {
      this.unknownMode = Objects.requireNonNull(unknownMode, "unknownMode");
    }

    /**
     * Returns default decoder options.
     *
     * <p>The default renders each unknown byte as uppercase hexadecimal wrapped in angle brackets,
     * for example {@code <FF>}.
     *
     * @return default decoder options
     */
    public static DecodeOptions defaults() {
      return new DecodeOptions(UnknownMode.HEX_ANGLE);
    }

    /**
     * Creates options using the supplied unknown-byte rendering mode.
     *
     * @param unknownMode rendering mode for unmatched bytes
     * @return decoder options
     * @throws NullPointerException if {@code unknownMode} is {@code null}
     */
    public static DecodeOptions unknownMode(UnknownMode unknownMode) {
      return new DecodeOptions(unknownMode);
    }
  }

  /** Rendering modes for bytes that do not match any table entry. */
  public enum UnknownMode {
    /** Render unknown bytes as uppercase hexadecimal in angle brackets, e.g. {@code <FF>}. */
    HEX_ANGLE,

    /** Render each unknown byte as {@code .}. */
    DOT,

    /** Render each unknown byte as {@code ?}. */
    QUESTION_MARK
  }

  private TblStringDecoder() {}

  /**
   * Decodes bytes with default options.
   *
   * @param bytes encoded bytes
   * @param table table used for decoding
   * @return decoded string
   * @throws NullPointerException if {@code bytes} or {@code table} is {@code null}
   */
  public static String decode(byte[] bytes, TblTable table) {
    return decode(bytes, table, DecodeOptions.defaults());
  }

  /**
   * Decodes bytes with explicit options.
   *
   * @param bytes encoded bytes
   * @param table table used for decoding
   * @param options unknown-byte fallback options
   * @return decoded string
   * @throws NullPointerException if any argument is {@code null}
   */
  public static String decode(byte[] bytes, TblTable table, DecodeOptions options) {
    Objects.requireNonNull(bytes, "bytes");
    Objects.requireNonNull(table, "table");
    Objects.requireNonNull(options, "options");

    StringBuilder result = new StringBuilder();
    int offset = 0;

    while (offset < bytes.length) {
      TblTableEntry match = findMatch(bytes, offset, table);

      if (match != null) {
        result.append(match.getValue());
        offset += match.getKeyLength();
        continue;
      }

      appendUnknown(result, bytes[offset], options);
      offset++;
    }

    return result.toString();
  }

  private static TblTableEntry findMatch(byte[] bytes, int offset, TblTable table) {
    for (TblTableEntry entry : table.getLongestFirstEntries()) {
      if (entry.matchesAt(bytes, offset)) {
        return entry;
      }
    }

    return null;
  }

  private static void appendUnknown(StringBuilder result, byte value, DecodeOptions options) {
    int unsigned = value & 0xff;

    switch (options.unknownMode) {
      case HEX_ANGLE:
        appendHexUnknown(result, unsigned);
        break;
      case DOT:
        result.append('.');
        break;
      case QUESTION_MARK:
        result.append('?');
        break;
      default:
        appendHexUnknown(result, unsigned);
        break;
    }
  }

  private static void appendHexUnknown(StringBuilder result, int unsigned) {
    result
        .append('<')
        .append(HEX_DIGITS[(unsigned >>> 4) & 0xf])
        .append(HEX_DIGITS[unsigned & 0xf])
        .append('>');
  }
}
