/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ghidra_tblstring.TestUtils;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblStringDecoderTest {
  private static String decode(String table, int... bytes) throws IOException {
    return TblStringDecoder.decode(TestUtils.toBytesArray(bytes), TestUtils.parseTblTableString(table));
  }

  @DisplayName("decodes simple byte matches")
  @Test
  void decodeReadsSimpleMatches() throws IOException {
    String decoded = decode("20= \n41=A\n42=B\n", 0x41, 0x20, 0x42);

    assertEquals("A B", decoded);
  }

  @DisplayName("uses longest matching key first")
  @Test
  void decodeUsesLongestMatchingKeyFirst() throws IOException {
    String decoded = decode("1F=<short>\n1F00=<long>\n00=<zero>\n", 0x1f, 0x00);

    assertEquals("<long>", decoded);
  }

  @DisplayName("continues decoding after multi-byte matches")
  @Test
  void decodeContinuesAfterMultiByteMatches() throws IOException {
    String decoded = decode("1F00=X\n41=A\n", 0x1f, 0x00, 0x41);

    assertEquals("XA", decoded);
  }

  @DisplayName("renders unknown bytes as hex angle brackets by default")
  @Test
  void decodeRendersUnknownBytesAsHexAngleByDefault() throws IOException {
    String decoded = decode("41=A\n", 0x41, 0x00, 0xff);

    assertEquals("A<00><FF>", decoded);
  }

  @DisplayName("renders unknown bytes as dots")
  @Test
  void decodeRendersUnknownBytesAsDots() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n");

    String decoded =
        TblStringDecoder.decode(
            TestUtils.toBytesArray(0x41, 0x00, 0xff),
            table,
            TblStringDecoder.DecodeOptions.unknownMode(TblStringDecoder.UnknownMode.DOT));

    assertEquals("A..", decoded);
  }

  @DisplayName("renders unknown bytes as question marks")
  @Test
  void decodeRendersUnknownBytesAsQuestionMarks() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n");

    String decoded =
        TblStringDecoder.decode(
            TestUtils.toBytesArray(0x41, 0x00, 0xff),
            table,
            TblStringDecoder.DecodeOptions.unknownMode(
                TblStringDecoder.UnknownMode.QUESTION_MARK));

    assertEquals("A??", decoded);
  }

  @DisplayName("renders unmatched partial multi-byte keys byte by byte")
  @Test
  void decodeRendersUnmatchedPartialMultiByteKeysByteByByte() throws IOException {
    String decoded = decode("1F00=X\n", 0x1f, 0x01);

    assertEquals("<1F><01>", decoded);
  }

  @DisplayName("decodes empty input as empty output")
  @Test
  void decodeEmptyInputAsEmptyOutput() throws IOException {
    String decoded = decode("41=A\n");

    assertEquals("", decoded);
  }

  @DisplayName("rejects null decode arguments")
  @Test
  void decodeRejectsNullArguments() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n");
    byte[] bytes = TestUtils.toBytesArray(0x41);

    assertThrows(NullPointerException.class, () -> TblStringDecoder.decode(null, table));
    assertThrows(NullPointerException.class, () -> TblStringDecoder.decode(bytes, null));
    assertThrows(NullPointerException.class, () -> TblStringDecoder.decode(bytes, table, null));
  }

  @DisplayName("rejects null unknown modes")
  @Test
  void decodeOptionsRejectsNullUnknownMode() {
    assertThrows(
        NullPointerException.class, () -> TblStringDecoder.DecodeOptions.unknownMode(null));
  }
}
