/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

/** Backslash escaping helpers used by .tbl parsing, serialization, and registry editing. */
public final class TblTextEscapes {
  private TblTextEscapes() {}

  /**
   * Decodes {@code \n}, {@code \r}, {@code \t}, and {@code \\} sequences.
   *
   * <p>Unknown escape sequences keep the escaped character, matching historical romhacking table
   * behavior used by this extension.
   *
   * @param value escaped text
   * @return unescaped text
   */
  public static String unescape(String value) {
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c != '\\' || i + 1 >= value.length()) {
        result.append(c);
        continue;
      }

      char next = value.charAt(++i);
      switch (next) {
        case 'n':
          result.append('\n');
          break;
        case 'r':
          result.append('\r');
          break;
        case 't':
          result.append('\t');
          break;
        case '\\':
          result.append('\\');
          break;
        default:
          result.append(next);
          break;
      }
    }

    return result.toString();
  }

  /**
   * Encodes newlines, carriage returns, tabs, and backslashes for {@code .tbl} output.
   *
   * @param value raw decoded text
   * @return escaped text safe for {@code TblTable.parse(...)}
   */
  public static String escape(String value) {
    StringBuilder result = new StringBuilder(value.length());

    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\n':
          result.append("\\n");
          break;
        case '\r':
          result.append("\\r");
          break;
        case '\t':
          result.append("\\t");
          break;
        case '\\':
          result.append("\\\\");
          break;
        default:
          result.append(c);
          break;
      }
    }

    return result.toString();
  }
}
