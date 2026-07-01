/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ghidra_tblstring.TestUtils;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblStringEncoderTest {
  @DisplayName("encodes simple table values")
  @Test
  void encodeReadsSimpleValues() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n42=B\n");

    List<byte[]> encodings = TblStringEncoder.encodeAll("AB", table, 10);

    assertEquals(1, encodings.size());
    assertEquals("4142", TblHex.toHex(encodings.get(0)));
  }

  @DisplayName("returns every matching encoding")
  @Test
  void encodeReturnsEveryMatchingEncoding() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n8140=A\n42=B\n8142=AB\n");

    List<byte[]> encodings = TblStringEncoder.encodeAll("AB", table, 10);

    assertEquals(3, encodings.size());
    assertEquals("4142", TblHex.toHex(encodings.get(0)));
    assertEquals("814042", TblHex.toHex(encodings.get(1)));
    assertEquals("8142", TblHex.toHex(encodings.get(2)));
  }

  @DisplayName("deduplicates identical byte encodings")
  @Test
  void encodeDeduplicatesIdenticalBytes() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n42=B\n4142=AB\n");

    List<byte[]> encodings = TblStringEncoder.encodeAll("AB", table, 10);

    assertEquals(1, encodings.size());
    assertEquals("4142", TblHex.toHex(encodings.get(0)));
  }

  @DisplayName("ignores empty table values")
  @Test
  void encodeIgnoresEmptyValues() throws IOException {
    TblTable table = TestUtils.parseTblTableString("00=\n41=A\n");

    List<byte[]> encodings = TblStringEncoder.encodeAll("A", table, 10);

    assertEquals(1, encodings.size());
    assertEquals("41", TblHex.toHex(encodings.get(0)));
  }

  @DisplayName("returns no encodings for unencodable text")
  @Test
  void encodeReturnsNoEncodingsForUnencodableText() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n");

    assertEquals(List.of(), TblStringEncoder.encodeAll("B", table, 10));
  }

  @DisplayName("rejects ambiguous results beyond the configured cap")
  @Test
  void encodeRejectsTooManyResults() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n42=A\n43=A\n");

    assertThrows(IllegalArgumentException.class, () -> TblStringEncoder.encodeAll("A", table, 2));
  }

  @DisplayName("returns immutable encoding lists")
  @Test
  void encodeReturnsImmutableLists() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n");
    List<byte[]> encodings = TblStringEncoder.encodeAll("A", table, 10);

    assertThrows(UnsupportedOperationException.class, () -> encodings.add(new byte[] {0x42}));
    assertThrows(UnsupportedOperationException.class, () -> encodings.remove(0));
    assertEquals("41", TblHex.toHex(encodings.get(0)));
  }

  @DisplayName("rejects invalid arguments")
  @Test
  void encodeRejectsInvalidArguments() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n");

    assertThrows(NullPointerException.class, () -> TblStringEncoder.encodeAll(null, table, 10));
    assertThrows(NullPointerException.class, () -> TblStringEncoder.encodeAll("A", null, 10));
    assertThrows(IllegalArgumentException.class, () -> TblStringEncoder.encodeAll("A", table, 0));
  }
}
