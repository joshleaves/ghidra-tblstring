/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblTextEscapesTest {
  @DisplayName("unescapes common backslash sequences")
  @Test
  void unescapeDecodesCommonBackslashSequences() {
    assertEquals("line\ncarriage\rtab\tbackslash\\", TblTextEscapes.unescape("line\\ncarriage\\rtab\\tbackslash\\\\"));
  }

  @DisplayName("keeps unknown escapes as literal characters")
  @Test
  void unescapeKeepsUnknownEscapesAsLiteralCharacters() {
    assertEquals("fooxbar", TblTextEscapes.unescape("foo\\xbar"));
  }

  @DisplayName("escapes common control characters")
  @Test
  void escapeWritesCommonBackslashSequences() {
    assertEquals("line\\ncarriage\\rtab\\tbackslash\\\\", TblTextEscapes.escape("line\ncarriage\rtab\tbackslash\\"));
  }
}
